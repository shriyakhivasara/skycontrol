package data;

import model.Flight;

/** One line of the schedule: an arrival and the departure its aircraft becomes. */
public record ScheduledFlight(Flight arrival, Flight departure) {
}
