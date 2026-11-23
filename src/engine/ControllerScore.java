package engine;

/**
 * Scores the human controller in manual mode, so the user can see how well they are doing.
 * Safe, on-time movements earn points; unsafe clearances and diversions lose them.
 */
public class ControllerScore {
    public static final int ON_TIME = 10;
    public static final int LATE = 4;
    public static final int EMERGENCY_BONUS = 15;
    public static final int VIOLATION = -25;
    public static final int DIVERSION = -50;

    private int points;
    private int movements;
    private int violations;
    private int diversions;

    /** A landing or take-off the controller cleared. */
    public void movement(int delayMinutes, boolean emergency) {
        movements++;
        points += delayMinutes <= report.Statistics.ON_TIME_LIMIT ? ON_TIME : LATE;
        if (emergency) {
            points += EMERGENCY_BONUS;
        }
    }

    /** The controller tried an unsafe clearance (it was refused). */
    public void violation() {
        violations++;
        points += VIOLATION;
    }

    /** A flight diverted while the controller was in charge. */
    public void diversion() {
        diversions++;
        points += DIVERSION;
    }

    public int getPoints() { return points; }
    public int getMovements() { return movements; }
    public int getViolations() { return violations; }
    public int getDiversions() { return diversions; }

    @Override
    public String toString() {
        return points + " pts (" + movements + " cleared, " + violations + " unsafe, " + diversions + " diverted)";
    }
}
