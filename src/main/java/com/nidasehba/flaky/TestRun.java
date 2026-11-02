package com.nidasehba.flaky;

import java.util.Map;

/**
 * One CI run: an id (the folder name, e.g. run-0042), the commit it built, and the outcome of every test.
 * Runs are sorted by id, so name folders so that they sort in time order.
 */
public record TestRun(String id, String commit, Map<String, Outcome> outcomes) {
}
