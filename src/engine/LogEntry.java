package engine;

import model.SimTime;

/** One line of the control tower log. flight is null for airport-wide messages. */
public record LogEntry(int minute, String flight, String message) {
    public String clock() {
        return SimTime.format(minute);
    }

    @Override
    public String toString() {
        return clock() + "  " + (flight == null ? "" : flight + "  ") + message;
    }
}
