package events;

/** Builds an event from a short command, used by the control panel and the experiment. */
public final class Events {
    private Events() {
    }

    public static Event emergency(String flight) {
        return new EmergencyEvent(flight, "engine failure");
    }

    public static Event closeRunway(String runway, int minutes) {
        return new RunwayClosureEvent(runway, minutes);
    }

    public static Event reopenRunway(String runway) {
        return new RunwayReopenEvent(runway);
    }

    public static Event storm(int minutes) {
        return new StormEvent(minutes);
    }
}
