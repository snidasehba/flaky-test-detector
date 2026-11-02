package com.nidasehba.flaky;

/**
 * What the history says about one test.
 *
 * @param flips       how often the outcome changed between consecutive runs (pass to fail or back)
 * @param sameCommit  true if the test both passed and failed on the same commit, the strongest sign of flakiness
 * @param score       0.0 (stable) to 1.0 (flips every run); flips divided by possible flips
 */
public record TestVerdict(String test, Status status, int runs, int failures, int flips,
                          boolean sameCommit, double score, String lastOutcome) {

    public enum Status {
        /** Passed in every run it executed in. */
        STABLE,
        /** Failed in every run: a real, consistent break, not flakiness. */
        BROKEN,
        /** Both passed and failed. */
        FLAKY,
        /** Failed in the most recent runs after passing before: probably a new regression, not flakiness. */
        NEWLY_FAILING,
        /** Too few runs with a pass/fail result to judge. */
        NOT_ENOUGH_DATA
    }
}
