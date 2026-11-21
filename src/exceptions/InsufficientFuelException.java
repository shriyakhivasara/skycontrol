package exceptions;

/** Thrown when a holding flight's fuel drops to its diversion reserve. The flight must divert. */
public class InsufficientFuelException extends SkyControlException {
    public InsufficientFuelException(String flightNumber, double fuelLeft) {
        super(flightNumber + " has only " + Math.round(fuelLeft) + " min of fuel left, diverting");
    }
}
