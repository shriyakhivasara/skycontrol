package model;

import exceptions.RunwayClosedException;

/**
 * A runway. Remembers the last aircraft that used it, because the gap needed before the
 * next one depends on the size of the one in front.
 */
public class Runway {
    private final String name;
    private boolean open = true;
    private int lastStart = -1000;               // long ago, so the runway starts free
    private int lastOccupancy = 0;
    private WakeCategory lastWake = WakeCategory.LIGHT;
    private String lastFlight;
    private int operations = 0;
    private int busyMinutes = 0;

    public Runway(String name) {
        this.name = name;
    }

    /** The earliest minute the given aircraft may start using this runway. */
    public int earliestTimeFor(Aircraft next) {
        int gap = SeparationRules.gapMinutes(lastWake, next.wakeCategory());
        return lastStart + Math.max(lastOccupancy, gap);
    }

    /** True if the aircraft could start using the runway at the given minute. */
    public boolean isAvailableFor(Aircraft next, int now) {
        return open && earliestTimeFor(next) <= now;
    }

    /** Clears a flight to land or take off now. */
    public void clear(Flight flight, int now) throws RunwayClosedException {
        if (!open) {
            throw new RunwayClosedException(name);
        }
        Aircraft a = flight.getAircraft();
        lastStart = now;
        lastOccupancy = a.runwayOccupancyMinutes();
        lastWake = a.wakeCategory();
        lastFlight = flight.getFlightNumber();
        operations++;
        busyMinutes += lastOccupancy;
    }

    public String getName() { return name; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public String getLastFlight() { return lastFlight; }
    public int getOperations() { return operations; }
    public int getBusyMinutes() { return busyMinutes; }

    @Override
    public String toString() {
        return "Runway " + name + (open ? "" : " (CLOSED)");
    }
}
