package events;

import engine.ControlTower;
import exceptions.InvalidEventException;
import exceptions.SkyControlException;
import model.Runway;
import model.SimTime;

/**
 * A thunderstorm over the airport: every open runway closes for a while. Aircraft keep
 * circling and burning fuel, so the ones lowest on fuel may have to divert.
 */
public class StormEvent implements Event {
    private final int durationMinutes;
    private int endsAt;

    public StormEvent(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    @Override
    public void apply(ControlTower tower, int now) throws SkyControlException {
        if (durationMinutes <= 0) {
            throw new InvalidEventException("A storm must last at least one minute");
        }
        endsAt = now + durationMinutes;
        int closed = 0;
        for (Runway r : tower.getAirport().getRunways()) {
            if (r.isOpen()) {
                tower.closeRunway(r.getName(), endsAt);
                closed++;
            }
        }
        if (closed == 0) {
            throw new InvalidEventException("Every runway is already closed");
        }
    }

    @Override
    public String describe() {
        return "THUNDERSTORM: all runways closed until " + SimTime.format(endsAt);
    }
}
