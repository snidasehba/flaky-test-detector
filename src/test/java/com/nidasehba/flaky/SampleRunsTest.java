package com.nidasehba.flaky;

import com.nidasehba.flaky.TestVerdict.Status;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End to end over the six sample CI runs checked into sample-runs/. */
class SampleRunsTest {

    private static List<TestRun> runs;
    private static Map<String, TestVerdict> byTest;

    @BeforeAll
    static void read() throws Exception {
        runs = ReportReader.readRuns(Path.of("sample-runs"));
        byTest = new FlakinessAnalyzer().analyze(runs).stream()
                .collect(Collectors.toMap(v -> v.test().substring(v.test().lastIndexOf('.') + 1), v -> v));
    }

    @Test
    void readsAllRunsInOrderWithCommits() {
        assertEquals(6, runs.size());
        assertEquals("run-01", runs.get(0).id());
        assertEquals("a1f3c9e", runs.get(0).commit());
    }

    @Test
    void classifiesEverySampleTestCorrectly() {
        assertEquals(Status.STABLE, byTest.get("createPayment").status());
        assertEquals(Status.FLAKY, byTest.get("applyCouponUpdatesTotal").status());
        assertEquals(Status.NEWLY_FAILING, byTest.get("partialRefundReducesBalance").status());
        assertEquals(Status.BROKEN, byTest.get("lockedOutUserSeesError").status());
        assertEquals(Status.FLAKY, byTest.get("topUpCreditsWallet").status());
        assertEquals(Status.STABLE, byTest.get("uploadDocument").status());
    }

    @Test
    void couponTestFailedAndPassedOnTheSameCommit() {
        assertTrue(byTest.get("applyCouponUpdatesTotal").sameCommit());
    }

    @Test
    void gateFailsWhenNoFlakyTestsAreAllowed() {
        List<TestVerdict> verdicts = new FlakinessAnalyzer().analyze(runs);
        assertTrue(Report.gateFails(verdicts, 0, 1.0));
        assertTrue(Report.gateFails(verdicts, 5, 0.5));
        assertTrue(!Report.gateFails(verdicts, 2, 1.0));
    }

    @Test
    void reportSeparatesFlakyTestsFromRealFailures(@TempDir Path out) throws Exception {
        String md = Report.markdown(runs, new FlakinessAnalyzer().analyze(runs));
        String flakySection = md.substring(md.indexOf("## Flaky"), md.indexOf("## Real failures"));
        String realSection = md.substring(md.indexOf("## Real failures"));
        assertTrue(flakySection.contains("applyCouponUpdatesTotal"));
        assertTrue(!flakySection.contains("partialRefundReducesBalance"));
        assertTrue(realSection.contains("partialRefundReducesBalance"));
        assertTrue(realSection.contains("lockedOutUserSeesError"));
        Files.writeString(out.resolve("r.md"), md);
    }

    @Test
    void refusesXmlWithExternalEntities(@TempDir Path dir) throws Exception {
        Path run = Files.createDirectory(dir.resolve("run-x"));
        Files.writeString(run.resolve("TEST-evil.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <testsuite><testcase classname="A" name="&xxe;"/></testsuite>
                """);
        assertThrows(Exception.class, () -> ReportReader.readRun(run));
    }
}
