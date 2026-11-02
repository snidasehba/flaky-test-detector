package com.nidasehba.flaky;

import com.nidasehba.flaky.TestVerdict.Status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns run history into a verdict per test.
 *
 * <p>The key distinction is between flaky and newly failing. A test that passed for ten runs and then
 * failed in the last three is almost certainly a regression; quarantining it as "flaky" would hide a real
 * bug. So a test only counts as flaky when it goes back and forth, or when it both passed and failed on the
 * same commit.
 */
public class FlakinessAnalyzer {

    private final int minRuns;
    private final int regressionTail;

    /**
     * @param minRuns        fewer pass/fail results than this gives NOT_ENOUGH_DATA
     * @param regressionTail a pass streak followed by at least this many trailing failures (and no other
     *                       failures) is NEWLY_FAILING rather than FLAKY
     */
    public FlakinessAnalyzer(int minRuns, int regressionTail) {
        this.minRuns = minRuns;
        this.regressionTail = regressionTail;
    }

    public FlakinessAnalyzer() {
        this(4, 2);
    }

    public List<TestVerdict> analyze(List<TestRun> runs) {
        Set<String> tests = new LinkedHashSet<>();
        runs.forEach(r -> tests.addAll(r.outcomes().keySet()));
        List<TestVerdict> verdicts = new ArrayList<>();
        for (String test : tests) {
            verdicts.add(verdictFor(test, runs));
        }
        verdicts.sort(Comparator.comparing((TestVerdict v) -> v.status().ordinal() == Status.FLAKY.ordinal() ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(TestVerdict::score).reversed())
                .thenComparing(TestVerdict::test));
        return verdicts;
    }

    TestVerdict verdictFor(String test, List<TestRun> runs) {
        List<Outcome> history = new ArrayList<>();
        Map<String, Set<Outcome>> byCommit = new HashMap<>();
        for (TestRun run : runs) {
            Outcome o = run.outcomes().get(test);
            if (o == null || o == Outcome.SKIP) {
                continue;
            }
            history.add(o);
            byCommit.computeIfAbsent(run.commit(), c -> new HashSet<>()).add(o);
        }

        int failures = (int) history.stream().filter(o -> o == Outcome.FAIL).count();
        int flips = 0;
        for (int i = 1; i < history.size(); i++) {
            if (history.get(i) != history.get(i - 1)) {
                flips++;
            }
        }
        boolean sameCommit = byCommit.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals("unknown") && e.getValue().size() > 1);
        double score = history.size() < 2 ? 0.0 : (double) flips / (history.size() - 1);
        String last = history.isEmpty() ? "SKIP" : history.get(history.size() - 1).name();

        Status status;
        if (history.size() < minRuns) {
            status = Status.NOT_ENOUGH_DATA;
        } else if (failures == 0) {
            status = Status.STABLE;
        } else if (failures == history.size()) {
            status = Status.BROKEN;
        } else if (!sameCommit && isTrailingFailureStreak(history)) {
            status = Status.NEWLY_FAILING;
        } else {
            status = Status.FLAKY;
        }
        return new TestVerdict(test, status, history.size(), failures, flips, sameCommit, round(score), last);
    }

    /** Passes first, then only failures at the end, at least {@code regressionTail} of them. */
    private boolean isTrailingFailureStreak(List<Outcome> history) {
        int firstFail = history.indexOf(Outcome.FAIL);
        boolean allFailAfter = history.subList(firstFail, history.size()).stream().allMatch(o -> o == Outcome.FAIL);
        return allFailAfter && history.size() - firstFail >= regressionTail;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
