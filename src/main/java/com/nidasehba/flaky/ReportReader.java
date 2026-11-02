package com.nidasehba.flaky;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads a folder of CI runs. Each sub-folder is one run and holds the JUnit-format XML reports
 * (TEST-*.xml) that Maven Surefire, TestNG and most CI tools write. An optional COMMIT file in the
 * run folder holds the commit SHA the run built.
 */
public final class ReportReader {

    private ReportReader() {
    }

    public static List<TestRun> readRuns(Path runsDir) throws Exception {
        List<Path> runDirs;
        try (Stream<Path> s = Files.list(runsDir)) {
            runDirs = s.filter(Files::isDirectory).sorted().toList();
        }
        List<TestRun> runs = new ArrayList<>();
        for (Path dir : runDirs) {
            runs.add(readRun(dir));
        }
        return runs;
    }

    public static TestRun readRun(Path runDir) throws Exception {
        Path commitFile = runDir.resolve("COMMIT");
        String commit = Files.exists(commitFile) ? Files.readString(commitFile).strip() : "unknown";
        List<Path> reports;
        try (Stream<Path> s = Files.list(runDir)) {
            reports = s.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
        }
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        for (Path report : reports) {
            outcomes.putAll(parseReport(report));
        }
        return new TestRun(runDir.getFileName().toString(), commit, outcomes);
    }

    static Map<String, Outcome> parseReport(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Reports can come from any pipeline, so refuse external entities (XXE).
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc;
        try {
            doc = factory.newDocumentBuilder().parse(report.toFile());
        } catch (IOException e) {
            throw new IOException("Cannot read " + report, e);
        }
        Map<String, Outcome> outcomes = new LinkedHashMap<>();
        NodeList cases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element tc = (Element) cases.item(i);
            String name = tc.getAttribute("classname") + "." + tc.getAttribute("name");
            outcomes.put(name, outcomeOf(tc));
        }
        return outcomes;
    }

    private static Outcome outcomeOf(Element testcase) {
        if (testcase.getElementsByTagName("skipped").getLength() > 0) {
            return Outcome.SKIP;
        }
        boolean failed = testcase.getElementsByTagName("failure").getLength() > 0
                || testcase.getElementsByTagName("error").getLength() > 0;
        return failed ? Outcome.FAIL : Outcome.PASS;
    }
}
