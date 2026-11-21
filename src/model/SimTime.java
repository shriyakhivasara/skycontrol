package model;

/**
 * Simulation time is a whole number of minutes since the simulation started.
 * Minute 0 is 06:00.
 */
public final class SimTime {
    public static final int START_HOUR = 6;

    private SimTime() {
    }

    /** Formats a simulation minute as a clock time, e.g. 75 becomes "07:15". */
    public static String format(int minute) {
        int total = START_HOUR * 60 + minute;
        int h = (total / 60) % 24;
        int m = total % 60;
        return String.format("%02d:%02d", h, m);
    }

    /** Parses "07:15" back to simulation minutes (75). */
    public static int parse(String clock) {
        String[] parts = clock.trim().split(":");
        int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        return total - START_HOUR * 60;
    }
}
