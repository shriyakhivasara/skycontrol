package policy;

/** Looks up a policy by its code. */
public final class Policies {
    private Policies() {
    }

    public static SchedulingPolicy byCode(String code) {
        if (code == null) {
            return null;
        }
        switch (code.trim().toUpperCase()) {
            case "FCFS": return new FcfsPolicy();
            case "PRIORITY_FUEL": return new PriorityWithFuelPolicy();
            default: return null;
        }
    }
}
