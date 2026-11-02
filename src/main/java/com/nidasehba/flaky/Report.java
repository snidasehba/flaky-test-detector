package com.nidasehba.flaky;

import com.nidasehba.flaky.TestVerdict.Status;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Markdown and JSON output, plus the quality-gate decision used by CI. */
public final class Report {

    private Report() {
    }

    public static Map<Status, Long> counts(List<TestVerdict> verdicts) {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            counts.put(s, verdicts.stream().filter(v -> v.status() == s).count());
        }
        return counts;
    }

    public static String markdown(List<TestRun> runs, List<TestVerdict> verdicts) {
        StringBuilder md = new StringBuilder("# Flaky test report\n\n");
        md.append(runs.size()).append(" runs, ").append(verdicts.size()).append(" tests.\n\n");
        md.append("| Status | Tests |\n|---|---|\n");
        counts(verdicts).forEach((s, n) -> md.append("| ").append(s).append(" | ").append(n).append(" |\n"));

        List<TestVerdict> flaky = verdicts.stream().filter(v -> v.status() == Status.FLAKY).toList();
        md.append("\n## Flaky tests (quarantine candidates)\n\n");
        if (flaky.isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| Test | Score | Failed | Flips | Same-commit pass+fail |\n|---|---|---|---|---|\n");
            for (TestVerdict v : flaky) {
                md.append("| `").append(v.test()).append("` | ").append(v.score()).append(" | ")
                        .append(v.failures()).append("/").append(v.runs()).append(" | ").append(v.flips())
                        .append(" | ").append(v.sameCommit() ? "yes" : "no").append(" |\n");
            }
        }

        List<TestVerdict> regressions = verdicts.stream()
                .filter(v -> v.status() == Status.NEWLY_FAILING || v.status() == Status.BROKEN).toList();
        md.append("\n## Real failures (do not quarantine; raise a bug)\n\n");
        if (regressions.isEmpty()) {
            md.append("None.\n");
        } else {
            for (TestVerdict v : regressions) {
                md.append("- `").append(v.test()).append("`: ").append(v.status())
                        .append(" (failed ").append(v.failures()).append(" of ").append(v.runs()).append(" runs)\n");
            }
        }
        return md.toString();
    }

    public static String json(List<TestVerdict> verdicts) {
        return verdicts.stream().map(v -> String.format(java.util.Locale.ROOT,
                        "  {\"test\": \"%s\", \"status\": \"%s\", \"runs\": %d, \"failures\": %d, \"flips\": %d, "
                                + "\"sameCommit\": %b, \"score\": %.2f, \"last\": \"%s\"}",
                        escape(v.test()), v.status(), v.runs(), v.failures(), v.flips(), v.sameCommit(), v.score(),
                        v.lastOutcome()))
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    /** True when the build should fail: more flaky tests than allowed, or any test above the score limit. */
    public static boolean gateFails(List<TestVerdict> verdicts, int maxFlaky, double maxScore) {
        List<TestVerdict> flaky = verdicts.stream().filter(v -> v.status() == Status.FLAKY).toList();
        return flaky.size() > maxFlaky || flaky.stream().anyMatch(v -> v.score() > maxScore);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
