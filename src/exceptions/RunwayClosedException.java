package exceptions;

/** Thrown when a flight is cleared to use a runway that is closed. */
public class RunwayClosedException extends SkyControlException {
    public RunwayClosedException(String runwayName) {
        super("Runway " + runwayName + " is closed");
    }
}
