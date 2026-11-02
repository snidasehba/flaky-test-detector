package com.nidasehba.flaky;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Usage: java -jar flaky-test-detector.jar <runs-dir> [--max-flaky N] [--max-score X] [--out DIR]
 *
 * <p>Writes flaky-report.md and flaky-report.json, prints the Markdown, and exits with code 1 when the
 * quality gate fails so a pipeline step can block the merge.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: <runs-dir> [--max-flaky N] [--max-score X] [--out DIR]");
            System.exit(2);
        }
        Path runsDir = Path.of(args[0]);
        int maxFlaky = 0;
        double maxScore = 1.0;
        Path out = Path.of(".");
        for (int i = 1; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--max-flaky" -> maxFlaky = Integer.parseInt(args[i + 1]);
                case "--max-score" -> maxScore = Double.parseDouble(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }

        List<TestRun> runs = ReportReader.readRuns(runsDir);
        List<TestVerdict> verdicts = new FlakinessAnalyzer().analyze(runs);
        String md = Report.markdown(runs, verdicts);
        Files.createDirectories(out);
        Files.writeString(out.resolve("flaky-report.md"), md);
        Files.writeString(out.resolve("flaky-report.json"), Report.json(verdicts));
        System.out.println(md);

        if (Report.gateFails(verdicts, maxFlaky, maxScore)) {
            System.err.println("Quality gate failed: flaky tests above the allowed limit.");
            System.exit(1);
        }
    }
}
