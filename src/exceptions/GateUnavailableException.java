package exceptions;

/** Thrown when no free gate is big enough for an aircraft. The flight then waits at a remote stand. */
public class GateUnavailableException extends SkyControlException {
    public GateUnavailableException(String flightNumber, String gateSize) {
        super("No free " + gateSize + " gate for " + flightNumber + ", sent to remote stand");
    }
}
