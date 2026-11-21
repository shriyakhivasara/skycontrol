package events;

import engine.ControlTower;
import exceptions.SkyControlException;
import model.SimTime;

/** Closes one runway, for example for an inspection or a disabled aircraft. */
public class RunwayClosureEvent implements Event {
    private final String runway;
    private final int durationMinutes;   // 0 or less means "until reopened by hand"
    private int reopensAt = -1;

    public RunwayClosureEvent(String runway, int durationMinutes) {
        this.runway = runway;
        this.durationMinutes = durationMinutes;
    }

    @Override
    public void apply(ControlTower tower, int now) throws SkyControlException {
        reopensAt = durationMinutes > 0 ? now + durationMinutes : -1;
        tower.closeRunway(runway, reopensAt);
    }

    @Override
    public String describe() {
        return "Runway " + runway + " CLOSED"
                + (reopensAt >= 0 ? " until " + SimTime.format(reopensAt) : " until further notice");
    }
}
