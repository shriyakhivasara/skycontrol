package model;

/** A parking gate at the terminal. */
public class Gate {
    private final String id;
    private final GateSize size;
    private Flight occupant;

    public Gate(String id, GateSize size) {
        this.id = id;
        this.size = size;
    }

    public boolean isFree() {
        return occupant == null;
    }

    /** True if the gate is free and big enough for the aircraft. */
    public boolean canTake(Aircraft aircraft) {
        return isFree() && size.fits(aircraft.requiredGateSize());
    }

    public void assign(Flight flight) {
        this.occupant = flight;
    }

    public void release() {
        this.occupant = null;
    }

    public String getId() { return id; }
    public GateSize getSize() { return size; }
    public Flight getOccupant() { return occupant; }

    @Override
    public String toString() {
        return id + " (" + size + ")" + (isFree() ? " free" : " " + occupant.getFlightNumber());
    }
}
