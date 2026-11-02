package com.nidasehba.flaky;

/** The result of one test in one CI run. Skipped tests are kept but never count towards flakiness. */
public enum Outcome {
    PASS, FAIL, SKIP
}
