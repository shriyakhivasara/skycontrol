package events;

import engine.ControlTower;
import exceptions.SkyControlException;

/**
 * Something that happens to the airport during a run: a MAYDAY, a runway closure, a storm.
 * Each event type knows how to apply itself to the control tower.
 */
public interface Event {
    /** Changes the airport. Throws if the event does not make sense right now. */
    void apply(ControlTower tower, int now) throws SkyControlException;

    /** One-line description for the log, e.g. "MAYDAY declared by EK512". */
    String describe();
}
