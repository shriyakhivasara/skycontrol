package exceptions;

/** Thrown when a flight is created with impossible data (bad flight number, unknown aircraft, no fuel...). */
public class InvalidFlightPlanException extends SkyControlException {
    public InvalidFlightPlanException(String message) {
        super("Invalid flight plan: " + message);
    }
}
