package engine;

import model.Flight;

/**
 * Observer: anything that wants to know when a flight changes (screens, the database,
 * the event log) implements this and registers with the engine.
 */
public interface FlightListener {
    void onFlightEvent(Flight flight, String message, int simMinute);
}
