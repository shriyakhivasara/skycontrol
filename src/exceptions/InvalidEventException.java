package exceptions;

/** Thrown when an event cannot be applied, e.g. a MAYDAY for a flight that is already on the ground. */
public class InvalidEventException extends SkyControlException {
    public InvalidEventException(String message) {
        super(message);
    }
}
