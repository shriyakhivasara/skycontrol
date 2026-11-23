package exceptions;

/**
 * Thrown when an aircraft cannot be parked at a gate: either no suitable gate is free when it
 * lands (it then goes to a remote stand), or the controller picked a gate that is taken or too small.
 */
public class GateUnavailableException extends SkyControlException {
    private GateUnavailableException(String message) {
        super(message);
    }

    /** No free gate of the needed size when the aircraft lands. */
    public static GateUnavailableException noFreeGate(String flightNumber, String gateSize) {
        return new GateUnavailableException("No free " + gateSize + " gate for " + flightNumber + ", sent to remote stand");
    }

    /** The controller tried to move an aircraft to a gate it cannot use. */
    public static GateUnavailableException cannotMove(String flightNumber, String gateId, String reason) {
        return new GateUnavailableException("Cannot move " + flightNumber + " to " + gateId + ": " + reason);
    }
}
