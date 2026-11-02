# Flaky Test Detector

Hackathon-style weekend build. Flaky tests waste hours: people re-run pipelines, stop trusting red
builds, and sometimes quarantine a test that was actually catching a real bug. This tool reads the
JUnit/TestNG XML reports from many CI runs, decides for every test whether it is **stable, flaky,
broken or newly failing**, and fails the build when flaky tests go over a limit.

Built with **Java 17** and **JUnit 5**, no other dependencies. Runs in **GitHub Actions**, where the
report is written to the job summary.

## The idea that matters: flaky is not the same as failing

| Status | History (P = pass, F = fail) | What to do |
|---|---|---|
| `STABLE` | `PPPPPP` | Nothing |
| `FLAKY` | `PFPFFP`, or a pass and a fail **on the same commit** | Quarantine, fix the wait / race / test data |
| `NEWLY_FAILING` | `PPPPFF` | **Do not quarantine**: probably a regression from a recent change. Raise a bug |
| `BROKEN` | `FFFFFF` | Fix the test or the product, it fails every time |
| `NOT_ENOUGH_DATA` | fewer than 4 results | Wait for more runs |

A pass followed by consistent failures looks "flaky" to simple tools, which then hide real bugs.
Here it only counts as flaky if the outcome goes back and forth, or if the test passed and failed
on the same commit (a re-run with no code change).

**Score** = outcome changes ÷ possible changes, from 0.0 (never changes) to 1.0 (changes every run).

## Example (the sample runs in this repo)

```
| Test                                                  | Score | Failed | Flips | Same-commit pass+fail |
| com.demo.ui.CheckoutUiTest.applyCouponUpdatesTotal    | 0.8   | 3/6    | 4     | yes                   |
| com.demo.api.WalletApiTest.topUpCreditsWallet         | 0.4   | 1/6    | 2     | yes                   |

Real failures (do not quarantine; raise a bug)
- com.demo.api.RefundApiTest.partialRefundReducesBalance: NEWLY_FAILING (failed 2 of 6 runs)
- com.demo.ui.LoginUiTest.lockedOutUserSeesError: BROKEN (failed 6 of 6 runs)
```

## Run it

```bash
mvn package
java -jar target/flaky-test-detector.jar sample-runs --max-flaky 0 --out report
```

`sample-runs/` has one folder per CI run (sorted by name = time order), each with `TEST-*.xml`
reports and an optional `COMMIT` file. Exit code 1 means the gate failed (`--max-flaky N` flaky tests
allowed, `--max-score X` highest score allowed). `report/` gets `flaky-report.md` and `flaky-report.json`.

In a real pipeline, keep the last N runs' `target/surefire-reports` as artifacts, download them into
one folder per run, and run this as a step before merge.

## Tests

16 JUnit tests: the classification rules (including the regression-vs-flaky edge cases and skipped
runs), end-to-end over the sample runs, the quality gate, and XML parsing that refuses external
entities (XXE).
