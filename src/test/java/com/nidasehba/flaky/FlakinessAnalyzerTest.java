package com.nidasehba.flaky;

import com.nidasehba.flaky.TestVerdict.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlakinessAnalyzerTest {

    private final FlakinessAnalyzer analyzer = new FlakinessAnalyzer();

    /** Builds runs for a single test from a string like "PPFP", one commit per run unless given. */
    private static List<TestRun> history(String outcomes, String... commits) {
        List<TestRun> runs = new ArrayList<>();
        for (int i = 0; i < outcomes.length(); i++) {
            Outcome o = switch (outcomes.charAt(i)) {
                case 'P' -> Outcome.PASS;
                case 'F' -> Outcome.FAIL;
                default -> Outcome.SKIP;
            };
            String commit = commits.length > i ? commits[i] : "c" + i;
            runs.add(new TestRun("run-" + i, commit, Map.of("T.test", o)));
        }
        return runs;
    }

    private TestVerdict verdict(String outcomes, String... commits) {
        return analyzer.analyze(history(outcomes, commits)).get(0);
    }

    @Test
    void alwaysPassingIsStable() {
        TestVerdict v = verdict("PPPPP");
        assertEquals(Status.STABLE, v.status());
        assertEquals(0.0, v.score());
    }

    @Test
    void alwaysFailingIsBrokenNotFlaky() {
        assertEquals(Status.BROKEN, verdict("FFFFF").status());
    }

    @Test
    void backAndForthIsFlakyWithHighScore() {
        TestVerdict v = verdict("PFPFP");
        assertEquals(Status.FLAKY, v.status());
        assertEquals(4, v.flips());
        assertEquals(1.0, v.score());
    }

    @Test
    void passStreakThenFailuresIsANewRegressionNotFlakiness() {
        // Quarantining this as flaky would hide a real bug introduced in the last two runs.
        assertEquals(Status.NEWLY_FAILING, verdict("PPPPFF").status());
    }

    @Test
    void passAndFailOnTheSameCommitIsFlakyEvenWhenItLooksLikeARegression() {
        TestVerdict v = verdict("PPPFF", "a", "b", "c", "c", "c");
        assertTrue(v.sameCommit());
        assertEquals(Status.FLAKY, v.status());
    }

    @Test
    void singleFailureInTheMiddleIsFlakyWithALowScore() {
        TestVerdict v = verdict("PPPFPPPP");
        assertEquals(Status.FLAKY, v.status());
        assertTrue(v.score() < 0.3, "score " + v.score());
    }

    @Test
    void skippedRunsAreIgnored() {
        TestVerdict v = verdict("PSPSPSP");
        assertEquals(Status.STABLE, v.status());
        assertEquals(4, v.runs());
    }

    @Test
    void tooFewRunsGiveNoVerdict() {
        assertEquals(Status.NOT_ENOUGH_DATA, verdict("PF").status());
    }

    @Test
    void unknownCommitsNeverCountAsSameCommit() {
        assertFalse(verdict("PFPF", "unknown", "unknown", "unknown", "unknown").sameCommit());
    }

    @Test
    void flakyTestsAreListedFirst() {
        List<TestRun> runs = List.of(
                new TestRun("r1", "a", Map.of("A.stable", Outcome.PASS, "B.flaky", Outcome.PASS)),
                new TestRun("r2", "b", Map.of("A.stable", Outcome.PASS, "B.flaky", Outcome.FAIL)),
                new TestRun("r3", "c", Map.of("A.stable", Outcome.PASS, "B.flaky", Outcome.PASS)),
                new TestRun("r4", "d", Map.of("A.stable", Outcome.PASS, "B.flaky", Outcome.FAIL)));
        assertEquals("B.flaky", analyzer.analyze(runs).get(0).test());
    }
}
