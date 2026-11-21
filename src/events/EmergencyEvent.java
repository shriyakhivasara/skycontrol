package events;

import engine.ControlTower;
import exceptions.SkyControlException;

/** A flight declares MAYDAY (for example an engine failure or a medical emergency on board). */
public class EmergencyEvent implements Event {
    private final String flightNumber;
    private final String reason;

    public EmergencyEvent(String flightNumber, String reason) {
        this.flightNumber = flightNumber;
        this.reason = reason == null || reason.isBlank() ? "emergency" : reason;
    }

    @Override
    public void apply(ControlTower tower, int now) throws SkyControlException {
        tower.declareEmergency(flightNumber);
    }

    @Override
    public String describe() {
        return "MAYDAY declared by " + flightNumber + " (" + reason + "), cleared to land first";
    }
}
