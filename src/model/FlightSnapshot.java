package model;

/**
 * A read-only copy of a flight at one moment. Screens, the database and reports use
 * snapshots so they never touch a Flight that the clock thread is changing.
 */
public record FlightSnapshot(
        String flightNumber,
        String airline,
        FlightType type,
        String otherAirport,
        String aircraftModel,
        String aircraftCategory,
        FlightStatus status,
        Priority priority,
        double fuelMinutes,
        int scheduledTime,
        int actualTime,
        int delayMinutes,
        String runway,
        String gate) {

    public static FlightSnapshot of(Flight f) {
        return new FlightSnapshot(
                f.getFlightNumber(), f.getAirline(), f.getType(), f.getOtherAirport(),
                f.getAircraft().getModel(), f.getAircraft().category(),
                f.getStatus(), f.getPriority(), f.getFuelMinutes(),
                f.getScheduledTime(), f.getActualTime(), f.delayMinutes(),
                f.getRunwayName(), f.getGateId());
    }

    public boolean isArrival() {
        return type == FlightType.ARRIVAL;
    }

    /** Scheduled time as a clock string, e.g. "07:15". */
    public String scheduledClock() {
        return SimTime.format(scheduledTime);
    }

    /** Actual time as a clock string, or "" if it has not happened yet. */
    public String actualClock() {
        return actualTime < 0 ? "" : SimTime.format(actualTime);
    }
}
