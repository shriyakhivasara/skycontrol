package engine;

import engine.RunwayScheduler.Clearance;
import events.Event;
import exceptions.GateUnavailableException;
import exceptions.InsufficientFuelException;
import exceptions.InvalidEventException;
import exceptions.InvalidFlightPlanException;
import exceptions.RunwayClosedException;
import exceptions.SeparationViolationException;
import exceptions.SkyControlException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import model.Airport;
import model.Flight;
import model.FlightSnapshot;
import model.FlightStatus;
import model.Gate;
import model.Priority;
import model.Runway;
import model.SimTime;
import policy.SchedulingPolicy;
import report.Statistics;

/**
 * The control tower runs the whole simulation. Each call to tick() moves time on by one
 * simulated minute and:
 * <ol>
 *   <li>re-opens runways whose closure has ended,</li>
 *   <li>adds any extra traffic from the TrafficGenerator,</li>
 *   <li>moves flights along: onto radar, into the holding queue, burning fuel,
 *       and out of the gate when their turnaround is done,</li>
 *   <li>asks the RunwayScheduler who may land or take off now (in automatic mode; in
 *       manual mode the user clears each flight with clearFlight()),</li>
 *   <li>parks landed aircraft at a gate (or a remote stand).</li>
 * </ol>
 * Every public method is synchronized: the clock thread, the screens and event buttons
 * all use the tower at the same time.
 */
public class ControlTower {
    private static final int LOG_SIZE = 300;
    /** A departure may push back this many minutes before its scheduled time (0 = not early). */
    public static final int EARLY_PUSHBACK_MINUTES = 0;

    private final Airport airport;
    private final RunwayScheduler scheduler;
    private final GateAllocator gates;
    private final TrafficGenerator generator;      // may be null: no extra traffic
    private final Map<String, Flight> flights = new LinkedHashMap<>();
    private final Map<String, Integer> reopenAt = new HashMap<>();   // runway name -> minute
    private final Deque<LogEntry> log = new ArrayDeque<>();
    private final List<FlightListener> listeners = new CopyOnWriteArrayList<>();
    private final ControllerScore score = new ControllerScore();
    private boolean manualMode = false;
    private int minute = -1;

    public ControlTower(Airport airport, SchedulingPolicy policy, TrafficGenerator generator) {
        this.airport = airport;
        this.scheduler = new RunwayScheduler(airport.getRunways(), policy);
        this.gates = new GateAllocator(airport.getGates());
        this.generator = generator;
    }

    // =====================================================================
    // Setting up
    // =====================================================================

    /**
     * Adds an arrival and the departure its aircraft will become after turnaround.
     * @param departure may be null if the aircraft stays here
     */
    public synchronized void addFlight(Flight arrival, Flight departure) throws InvalidFlightPlanException {
        if (!arrival.isArrival()) {
            throw new InvalidFlightPlanException(arrival.getFlightNumber() + " is not an arrival");
        }
        checkUnique(arrival.getFlightNumber());
        if (departure != null) {
            checkUnique(departure.getFlightNumber());
            if (departure.isArrival()) {
                throw new InvalidFlightPlanException(departure.getFlightNumber() + " is not a departure");
            }
            if (departure.getScheduledTime() <= arrival.getScheduledTime()) {
                throw new InvalidFlightPlanException(departure.getFlightNumber() + " leaves before its aircraft arrives");
            }
            arrival.setLinkedDeparture(departure);
            flights.put(departure.getFlightNumber(), departure);
        }
        flights.put(arrival.getFlightNumber(), arrival);
    }

    private void checkUnique(String flightNumber) throws InvalidFlightPlanException {
        if (flights.containsKey(flightNumber)) {
            throw new InvalidFlightPlanException("duplicate flight number " + flightNumber);
        }
    }

    public void addListener(FlightListener listener) {
        listeners.add(listener);
    }

    // =====================================================================
    // The simulation step
    // =====================================================================

    /** Advances the simulation by one minute. */
    public synchronized void tick() {
        minute++;
        int now = minute;

        reopenRunways(now);
        addGeneratedTraffic(now);

        for (Flight f : new ArrayList<>(flights.values())) {
            if (f.isArrival()) {
                moveArrival(f, now);
            }
        }

        if (!manualMode) {
            for (Clearance c : scheduler.assignSlots(now)) {
                complete(c.flight, c.runway, now, false);
            }
        }
    }

    /** Finishes a landing or take-off that has just been cleared. */
    private void complete(Flight f, Runway runway, int now, boolean byController) {
        if (f.isArrival()) {
            land(f, runway, now);
        } else {
            f.setStatus(FlightStatus.DEPARTED);
            record(f, "took off from " + runway.getName() + delayText(f));
        }
        if (byController) {
            score.movement(f.delayMinutes(), f.getPriority() == Priority.EMERGENCY);
        }
    }

    private void reopenRunways(int now) {
        for (Runway r : airport.getRunways()) {
            Integer when = reopenAt.get(r.getName());
            if (when != null && now >= when) {
                r.setOpen(true);
                reopenAt.remove(r.getName());
                record(null, "Runway " + r.getName() + " reopened");
            }
        }
    }

    private void addGeneratedTraffic(int now) {
        if (generator == null) {
            return;
        }
        List<Flight> extra = generator.generate(now);
        if (extra.isEmpty()) {
            return;
        }
        try {
            addFlight(extra.get(0), extra.size() > 1 ? extra.get(1) : null);
            record(extra.get(0), "filed an unscheduled flight plan from " + extra.get(0).getOtherAirport());
        } catch (InvalidFlightPlanException e) {
            record(null, e.getMessage());
        }
    }

    /** Moves one arrival along its life cycle for this minute. */
    private void moveArrival(Flight f, int now) {
        switch (f.getStatus()) {
            case SCHEDULED:
                if (now >= f.getScheduledTime() - Flight.RADAR_RANGE_MINUTES) {
                    f.setStatus(FlightStatus.APPROACHING);
                    record(f, "on radar, " + Math.round(f.getFuelMinutes()) + " min of fuel");
                }
                break;
            case APPROACHING:
                burn(f);
                if (f.getStatus() == FlightStatus.APPROACHING && now >= f.getScheduledTime()) {
                    scheduler.requestLanding(f, now);
                    record(f, "reached the airport, waiting to land");
                }
                break;
            case HOLDING:
                burn(f);
                break;
            case AT_GATE:
            case REMOTE_STAND:
                tryPushBack(f, now);
                break;
            default:
                break;
        }
    }

    /** One minute of fuel burn. Raises priority when low; diverts when fuel runs out. */
    private void burn(Flight f) {
        try {
            if (f.burnFuel(1)) {
                scheduler.reprioritise(f);
                record(f, "LOW FUEL, " + Math.round(f.getFuelMinutes()) + " min left");
            }
        } catch (InsufficientFuelException e) {
            divert(f, e.getMessage());
        }
    }

    private void divert(Flight f, String reason) {
        if (manualMode) {
            score.diversion();
        }
        scheduler.remove(f);
        f.setStatus(FlightStatus.DIVERTED);
        record(f, reason + " to AUH");
        Flight dep = f.getLinkedDeparture();
        if (dep != null && !dep.getStatus().isFinished()) {
            dep.setStatus(FlightStatus.CANCELLED);
            record(dep, "cancelled, inbound aircraft diverted");
        }
    }

    private void land(Flight f, Runway runway, int now) {
        String where;
        try {
            Gate g = gates.allocate(f);
            f.setStatus(FlightStatus.AT_GATE);
            where = "gate " + g.getId();
        } catch (GateUnavailableException e) {
            f.setStatus(FlightStatus.REMOTE_STAND);
            f.setGateId(GateAllocator.REMOTE_STAND);
            where = "remote stand (no free gate)";
        }
        f.setGateArrivalTime(now);
        Flight dep = f.getLinkedDeparture();
        if (dep != null) {
            dep.setStatus(FlightStatus.BOARDING);
            dep.setGateId(f.getGateId());
        }
        record(f, "landed on " + runway.getName() + ", " + where + delayText(f));
    }

    /** When turnaround is finished, the aircraft leaves the gate and its departure asks to take off. */
    private void tryPushBack(Flight arrival, int now) {
        int turnaround = arrival.getAircraft().turnaroundMinutes();
        if (arrival.getStatus() == FlightStatus.REMOTE_STAND) {
            turnaround += GateAllocator.REMOTE_STAND_PENALTY;
        }
        if (now < arrival.getGateArrivalTime() + turnaround) {
            return;
        }
        Flight dep = arrival.getLinkedDeparture();
        if (dep != null && now < dep.getScheduledTime() - EARLY_PUSHBACK_MINUTES) {
            return;  // ready early: wait at the gate for the scheduled time
        }
        gates.release(arrival);
        arrival.setStatus(FlightStatus.LANDED);   // this arrival is now complete
        record(arrival, "turnaround done, left " + arrival.getGateId());
        if (dep != null) {
            scheduler.requestTakeoff(dep, now);
            record(dep, "pushed back from " + arrival.getGateId() + ", waiting to take off");
        }
    }

    private static String delayText(Flight f) {
        int d = f.delayMinutes();
        return d > 0 ? " (" + d + " min late)" : " (on time)";
    }

    // =====================================================================
    // Operations used by events and the control panel
    // =====================================================================

    /** Applies an event and logs it. */
    public synchronized void fireEvent(Event event) throws SkyControlException {
        event.apply(this, Math.max(0, minute));
        record(null, event.describe());
    }

    /** MAYDAY: the flight goes to the front of the queue (under the priority policy). */
    public synchronized void declareEmergency(String flightNumber) throws InvalidEventException {
        Flight f = requireFlight(flightNumber);
        if (!f.getStatus().isAirborne()) {
            throw new InvalidEventException(flightNumber + " is not in the air near the airport (" + f.getStatus() + ")");
        }
        if (f.getPriority() == Priority.EMERGENCY) {
            throw new InvalidEventException(flightNumber + " has already declared an emergency");
        }
        f.declareEmergency();
        if (f.getStatus() == FlightStatus.APPROACHING) {
            scheduler.requestLanding(f, Math.max(0, minute));   // comes straight in
        } else {
            scheduler.reprioritise(f);
        }
    }

    /**
     * Closes a runway.
     * @param untilMinute minute it reopens by itself, or -1 to stay closed until reopened
     */
    public synchronized void closeRunway(String name, int untilMinute) throws SkyControlException {
        Runway r = requireRunway(name);
        if (!r.isOpen()) {
            throw new RunwayClosedException(r.getName() + " (already)");
        }
        r.setOpen(false);
        if (untilMinute >= 0) {
            reopenAt.put(r.getName(), untilMinute);
        }
    }

    public synchronized void openRunway(String name) throws InvalidEventException {
        Runway r = requireRunway(name);
        if (r.isOpen()) {
            throw new InvalidEventException("Runway " + r.getName() + " is already open");
        }
        r.setOpen(true);
        reopenAt.remove(r.getName());
    }

    // =====================================================================
    // Manual control: the user is the controller
    // =====================================================================

    /** In manual mode nothing lands or takes off unless the user clears it. */
    public synchronized void setManualMode(boolean manual) {
        if (manual == manualMode) {
            return;
        }
        manualMode = manual;
        record(null, manual ? "MANUAL control: you now clear every landing and take-off"
                : "AUTOMATIC control: the scheduler clears flights again");
    }

    /**
     * The controller clears a waiting flight to land or take off on a runway, right now.
     * @throws InvalidEventException       if the flight is not waiting for a runway, or names are unknown
     * @throws RunwayClosedException       if the runway is closed
     * @throws SeparationViolationException if it is too soon after the previous aircraft (costs points)
     */
    public synchronized void clearFlight(String flightNumber, String runwayName) throws SkyControlException {
        Flight f = requireFlight(flightNumber);
        if (!scheduler.contains(f)) {
            throw new InvalidEventException(f.getFlightNumber() + " is not waiting for a runway ("
                    + f.getStatus().label() + ")");
        }
        Runway r = requireRunway(runwayName);
        if (!r.isOpen()) {
            throw new RunwayClosedException(r.getName());
        }
        int now = Math.max(0, minute);
        int safeFrom = r.earliestTimeFor(f.getAircraft());
        if (safeFrom > now) {
            score.violation();
            record(f, "UNSAFE clearance on " + r.getName() + " refused, safe from " + SimTime.format(safeFrom));
            throw new SeparationViolationException(f.getFlightNumber(), f.getAircraft().getModel(), r.getName(),
                    r.getLastFlight(), safeFrom);
        }
        r.clear(f, now);
        scheduler.remove(f);
        f.setRunwayName(r.getName());
        f.setActualTime(now);
        complete(f, r, now, true);
    }

    /** Puts a waiting flight at the back of the queue (it asks again from now). */
    public synchronized void sendToBackOfQueue(String flightNumber) throws InvalidEventException {
        Flight f = requireFlight(flightNumber);
        if (!scheduler.contains(f)) {
            throw new InvalidEventException(f.getFlightNumber() + " is not in the runway queue");
        }
        scheduler.remove(f);
        int now = Math.max(0, minute);
        if (f.isArrival()) {
            scheduler.requestLanding(f, now);
        } else {
            scheduler.requestTakeoff(f, now);
        }
        record(f, "sent to the back of the queue by the controller");
    }

    /** The controller sends an airborne arrival to another airport. */
    public synchronized void divertByController(String flightNumber) throws InvalidEventException {
        Flight f = requireFlight(flightNumber);
        if (!f.isArrival() || !f.getStatus().isAirborne()) {
            throw new InvalidEventException("Only a flight in the air can divert (" + f.getFlightNumber()
                    + " is " + f.getStatus().label() + ")");
        }
        divert(f, "diverted by the controller");
    }

    /**
     * Moves a parked aircraft to a gate the controller picks, e.g. from a remote stand to a gate
     * that has just become free (passengers no longer need a bus).
     */
    public synchronized void moveToGate(String flightNumber, String gateId) throws SkyControlException {
        Flight f = requireFlight(flightNumber);
        if (!f.isArrival() || (f.getStatus() != FlightStatus.AT_GATE && f.getStatus() != FlightStatus.REMOTE_STAND)) {
            throw new InvalidEventException(f.getFlightNumber() + " is not parked (" + f.getStatus().label() + ")");
        }
        String from = f.getGateId();
        Gate g = gates.assignTo(f, gateId);
        f.setStatus(FlightStatus.AT_GATE);
        Flight dep = f.getLinkedDeparture();
        if (dep != null) {
            dep.setGateId(g.getId());
        }
        record(f, "moved from " + from + " to gate " + g.getId());
    }

    /**
     * Adds a flight typed in by the user. Same checks as the schedule file, plus the arrival
     * must still be in the future.
     */
    public synchronized void fileFlightPlan(Flight arrival, Flight departure) throws InvalidFlightPlanException {
        int now = Math.max(0, minute);
        if (arrival.getScheduledTime() <= now) {
            throw new InvalidFlightPlanException("arrival time " + SimTime.format(arrival.getScheduledTime())
                    + " has already passed (it is " + SimTime.format(now) + ")");
        }
        addFlight(arrival, departure);
        record(arrival, "flight plan filed by the controller, due " + SimTime.format(arrival.getScheduledTime())
                + (departure == null ? "" : ", returns as " + departure.getFlightNumber()));
    }

    public synchronized boolean isManualMode() { return manualMode; }
    public synchronized ControllerScore getScore() { return score; }

    public synchronized void setPolicy(SchedulingPolicy policy) {
        scheduler.setPolicy(policy);
        record(null, "Scheduling policy changed to " + policy.displayName());
    }

    private Flight requireFlight(String flightNumber) throws InvalidEventException {
        Flight f = flightNumber == null ? null : flights.get(flightNumber.trim().toUpperCase());
        if (f == null) {
            throw new InvalidEventException("No flight called " + flightNumber);
        }
        return f;
    }

    private Runway requireRunway(String name) throws InvalidEventException {
        Runway r = name == null ? null : airport.getRunway(name.trim());
        if (r == null) {
            throw new InvalidEventException("No runway called " + name);
        }
        return r;
    }

    // =====================================================================
    // Logging and reading state
    // =====================================================================

    private void record(Flight f, String message) {
        int now = Math.max(0, minute);
        LogEntry entry = new LogEntry(now, f == null ? null : f.getFlightNumber(), message);
        log.addLast(entry);
        if (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
        for (FlightListener l : listeners) {
            l.onFlightEvent(f, message, now);
        }
    }

    /** A consistent copy of the whole airport for the screens. */
    public synchronized TowerSnapshot snapshot() {
        List<FlightSnapshot> fs = new ArrayList<>(flights.size());
        for (Flight f : flights.values()) {
            fs.add(FlightSnapshot.of(f));
        }
        List<TowerSnapshot.RunwayStatus> rs = new ArrayList<>();
        for (Runway r : airport.getRunways()) {
            rs.add(new TowerSnapshot.RunwayStatus(r.getName(), r.isOpen(),
                    reopenAt.getOrDefault(r.getName(), -1), r.getLastFlight(), r.getOperations()));
        }
        List<TowerSnapshot.GateStatus> gs = new ArrayList<>();
        for (Gate g : airport.getGates()) {
            gs.add(new TowerSnapshot.GateStatus(g.getId(), g.getSize(),
                    g.getOccupant() == null ? null : g.getOccupant().getFlightNumber()));
        }
        List<FlightSnapshot> q = new ArrayList<>();
        for (Flight f : scheduler.queueSnapshot()) {
            q.add(FlightSnapshot.of(f));
        }
        SchedulingPolicy p = scheduler.getPolicy();
        return new TowerSnapshot(minute, p.code(), p.displayName(), fs, rs, gs, q,
                new ArrayList<>(log), statistics(), manualMode, score.getPoints(), score.toString());
    }

    public synchronized Statistics statistics() {
        return Statistics.compute(flights.values(), airport.getRunways(), Math.max(0, minute));
    }

    /** Snapshots of every flight (for reports and the database). */
    public synchronized List<FlightSnapshot> flightSnapshots() {
        List<FlightSnapshot> fs = new ArrayList<>();
        for (Flight f : flights.values()) {
            fs.add(FlightSnapshot.of(f));
        }
        return fs;
    }

    public synchronized int getMinute() { return minute; }
    public synchronized Flight getFlight(String number) { return flights.get(number); }
    public synchronized int flightCount() { return flights.size(); }
    public synchronized SchedulingPolicy getPolicy() { return scheduler.getPolicy(); }
    public Airport getAirport() { return airport; }

    /** True when every flight has finished (useful for running the simulation to the end). */
    public synchronized boolean allFinished() {
        for (Flight f : flights.values()) {
            FlightStatus s = f.getStatus();
            boolean done = s.isFinished() || (f.isArrival() && s == FlightStatus.LANDED);
            if (!done) {
                return false;
            }
        }
        return true;
    }
}
