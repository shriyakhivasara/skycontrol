package engine;

import exceptions.GateUnavailableException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.Aircraft;
import model.Flight;
import model.Gate;
import model.GateSize;

/**
 * Finds a gate for each landed aircraft. Gates are grouped by size in an EnumMap so a
 * small aircraft is offered a normal gate first and only takes a large gate if no normal
 * one is free (large gates are kept for widebodies where possible).
 */
public class GateAllocator {
    /** Extra turnaround minutes at a remote stand (passengers travel by bus). */
    public static final int REMOTE_STAND_PENALTY = 15;
    public static final String REMOTE_STAND = "Remote";

    private final EnumMap<GateSize, List<Gate>> gatesBySize = new EnumMap<>(GateSize.class);
    private final Map<String, Gate> gateOfFlight = new HashMap<>();

    public GateAllocator(List<Gate> gates) {
        for (GateSize size : GateSize.values()) {
            gatesBySize.put(size, new ArrayList<>());
        }
        for (Gate g : gates) {
            gatesBySize.get(g.getSize()).add(g);
        }
    }

    /**
     * Parks the flight at the smallest free gate that fits.
     * @throws GateUnavailableException if every suitable gate is taken
     */
    public synchronized Gate allocate(Flight flight) throws GateUnavailableException {
        Aircraft aircraft = flight.getAircraft();
        for (GateSize size : GateSize.values()) {          // NORMAL first, then LARGE
            if (!size.fits(aircraft.requiredGateSize())) {
                continue;
            }
            for (Gate g : gatesBySize.get(size)) {
                if (g.canTake(aircraft)) {
                    g.assign(flight);
                    gateOfFlight.put(flight.getFlightNumber(), g);
                    flight.setGateId(g.getId());
                    return g;
                }
            }
        }
        throw GateUnavailableException.noFreeGate(flight.getFlightNumber(), aircraft.requiredGateSize().name().toLowerCase());
    }

    /**
     * Moves a parked flight to a gate chosen by the controller.
     * @throws GateUnavailableException if the gate is taken or too small
     */
    public synchronized Gate assignTo(Flight flight, String gateId) throws GateUnavailableException {
        Gate target = null;
        for (List<Gate> list : gatesBySize.values()) {
            for (Gate g : list) {
                if (g.getId().equalsIgnoreCase(gateId)) {
                    target = g;
                }
            }
        }
        if (target == null) {
            throw GateUnavailableException.cannotMove(flight.getFlightNumber(), gateId, "there is no such gate");
        }
        if (target.getOccupant() == flight) {
            return target;
        }
        if (!target.getSize().fits(flight.getAircraft().requiredGateSize())) {
            throw GateUnavailableException.cannotMove(flight.getFlightNumber(), target.getId(),
                    "too small for a " + flight.getAircraft().getModel());
        }
        if (!target.isFree()) {
            throw GateUnavailableException.cannotMove(flight.getFlightNumber(), target.getId(),
                    "taken by " + target.getOccupant().getFlightNumber());
        }
        release(flight);
        target.assign(flight);
        gateOfFlight.put(flight.getFlightNumber(), target);
        flight.setGateId(target.getId());
        return target;
    }

    /** Frees the gate used by this flight, if it has one. */
    public synchronized void release(Flight flight) {
        Gate g = gateOfFlight.remove(flight.getFlightNumber());
        if (g != null) {
            g.release();
        }
    }

    public synchronized int freeGates(GateSize size) {
        int n = 0;
        for (Gate g : gatesBySize.get(size)) {
            if (g.isFree()) {
                n++;
            }
        }
        return n;
    }
}
