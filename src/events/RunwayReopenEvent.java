package events;

import engine.ControlTower;
import exceptions.SkyControlException;

/** Reopens a closed runway. */
public class RunwayReopenEvent implements Event {
    private final String runway;

    public RunwayReopenEvent(String runway) {
        this.runway = runway;
    }

    @Override
    public void apply(ControlTower tower, int now) throws SkyControlException {
        tower.openRunway(runway);
    }

    @Override
    public String describe() {
        return "Runway " + runway + " reopened";
    }
}
