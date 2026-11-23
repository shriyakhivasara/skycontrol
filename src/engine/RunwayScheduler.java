package engine;

import exceptions.RunwayClosedException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import model.Flight;
import model.FlightStatus;
import model.Runway;
import policy.SchedulingPolicy;

/**
 * The heart of SkyControl: a queue of flights waiting for a runway.
 *
 * The queue is a PriorityQueue ordered by the current SchedulingPolicy. Each minute,
 * assignSlots() clears the flight at the front onto the runway that can take it soonest,
 * respecting the wake-turbulence gap behind the previous aircraft.
 *
 * All public methods are synchronized because the clock thread, the web server and
 * event buttons can all touch the queue at the same time.
 */
public class RunwayScheduler {
    private final List<Runway> runways;
    private SchedulingPolicy policy;
    private PriorityQueue<Flight> queue;

    public RunwayScheduler(List<Runway> runways, SchedulingPolicy policy) {
        this.runways = runways;
        this.policy = policy;
        this.queue = new PriorityQueue<>(policy.order());
    }

    /** An arriving flight reaches the airport and asks to land. */
    public synchronized void requestLanding(Flight flight, int now) {
        flight.setRequestTime(now);
        flight.setStatus(FlightStatus.HOLDING);
        queue.add(flight);
    }

    /** A departing flight has pushed back and asks to take off. */
    public synchronized void requestTakeoff(Flight flight, int now) {
        flight.setRequestTime(now);
        flight.setStatus(FlightStatus.READY_FOR_DEPARTURE);
        queue.add(flight);
    }

    /**
     * Call after a flight's priority changes. A PriorityQueue does not re-sort an element
     * that changed, so we take it out and put it back in.
     */
    public synchronized void reprioritise(Flight flight) {
        if (queue.remove(flight)) {
            queue.add(flight);
        }
    }

    /** True if the flight is waiting in the queue. */
    public synchronized boolean contains(Flight flight) {
        return queue.contains(flight);
    }

    /** Removes a flight from the queue (for example when it diverts). */
    public synchronized boolean remove(Flight flight) {
        return queue.remove(flight);
    }

    /**
     * Clears as many flights as possible at this minute.
     * @return the flights that were given a runway, in order
     */
    public synchronized List<Clearance> assignSlots(int now) {
        List<Clearance> cleared = new ArrayList<>();
        while (!queue.isEmpty()) {
            Flight next = queue.peek();
            Runway best = null;
            for (Runway r : runways) {
                if (r.isAvailableFor(next.getAircraft(), now)
                        && (best == null || r.earliestTimeFor(next.getAircraft())
                                            < best.earliestTimeFor(next.getAircraft()))) {
                    best = r;
                }
            }
            if (best == null) {
                break; // the front flight must wait, so everyone behind it waits too
            }
            try {
                best.clear(next, now);
            } catch (RunwayClosedException e) {
                break; // cannot happen: isAvailableFor already checked the runway is open
            }
            queue.poll();
            next.setRunwayName(best.getName());
            next.setActualTime(now);
            cleared.add(new Clearance(next, best, now));
        }
        return cleared;
    }

    /** Switches policy live. The queue is rebuilt in the new order. */
    public synchronized void setPolicy(SchedulingPolicy newPolicy) {
        PriorityQueue<Flight> rebuilt = new PriorityQueue<>(newPolicy.order());
        rebuilt.addAll(queue);
        this.policy = newPolicy;
        this.queue = rebuilt;
    }

    /** The waiting flights in the order they will be cleared (a copy, safe to read). */
    public synchronized List<Flight> queueSnapshot() {
        List<Flight> copy = new ArrayList<>(queue);
        copy.sort(policy.order());
        return copy;
    }

    public synchronized int queueSize() {
        return queue.size();
    }

    public synchronized SchedulingPolicy getPolicy() {
        return policy;
    }

    /** One flight being given one runway at one minute. */
    public static final class Clearance {
        public final Flight flight;
        public final Runway runway;
        public final int minute;

        public Clearance(Flight flight, Runway runway, int minute) {
            this.flight = flight;
            this.runway = runway;
            this.minute = minute;
        }
    }
}
