package exceptions;

import model.SimTime;

/**
 * Thrown in manual mode when the controller clears an aircraft onto a runway too soon
 * after the previous one (the wake turbulence gap has not passed yet).
 */
public class SeparationViolationException extends SkyControlException {
    private final int safeFrom;

    public SeparationViolationException(String flight, String aircraft, String runway,
                                        String previousFlight, int safeFrom) {
        super("Unsafe: " + flight + " (" + aircraft + ") must wait for the gap behind "
                + previousFlight + " on " + runway + ". Safe from " + SimTime.format(safeFrom));
        this.safeFrom = safeFrom;
    }

    /** The first minute the clearance would be safe. */
    public int getSafeFrom() {
        return safeFrom;
    }
}
