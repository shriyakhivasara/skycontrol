package test;

/** A tiny test helper: counts passed and failed checks and prints a summary. */
public class Check {
    private final String suite;
    private int passed = 0;
    private int failed = 0;

    public Check(String suite) {
        this.suite = suite;
        System.out.println("=== " + suite + " ===");
    }

    public void that(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + description);
        } else {
            failed++;
            System.out.println("  FAIL  " + description);
        }
    }

    public void equal(Object expected, Object actual, String description) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        that(ok, description + (ok ? "" : "  (expected " + expected + ", got " + actual + ")"));
    }

    /** Prints the summary and returns the number of failures. */
    public int finish() {
        System.out.println(suite + ": " + passed + " passed, " + failed + " failed");
        System.out.println();
        return failed;
    }
}
