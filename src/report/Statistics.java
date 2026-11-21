package report;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import model.Flight;
import model.FlightStatus;
import model.Runway;
import model.SimTime;

/**
 * Performance figures for one run of the simulation, worked out from the flights.
 * Immutable: a new Statistics is computed whenever the screens or reports need one.
 */
public final class Statistics {
    /** A movement counts as on time if it is at most this many minutes late (the airline industry standard). */
    public static final int ON_TIME_LIMIT = 15;

    private final int landings;
    private final int departures;
    private final int diversions;
    private final int cancellations;
    private final int holdingNow;
    private final double avgArrivalDelay;
    private final double avgDepartureDelay;
    private final double onTimePercent;
    private final double runwayUtilisation;
    private final int maxArrivalDelay;
    private final TreeMap<Integer, Integer> movementsByHour;

    private Statistics(int landings, int departures, int diversions, int cancellations, int holdingNow,
                       double avgArrivalDelay, double avgDepartureDelay, double onTimePercent,
                       double runwayUtilisation, int maxArrivalDelay, TreeMap<Integer, Integer> movementsByHour) {
        this.landings = landings;
        this.departures = departures;
        this.diversions = diversions;
        this.cancellations = cancellations;
        this.holdingNow = holdingNow;
        this.avgArrivalDelay = avgArrivalDelay;
        this.avgDepartureDelay = avgDepartureDelay;
        this.onTimePercent = onTimePercent;
        this.runwayUtilisation = runwayUtilisation;
        this.maxArrivalDelay = maxArrivalDelay;
        this.movementsByHour = movementsByHour;
    }

    /**
     * @param elapsedMinutes how long the simulation has been running
     */
    public static Statistics compute(Collection<Flight> flights, List<Runway> runways, int elapsedMinutes) {
        int landings = 0, departures = 0, diversions = 0, cancellations = 0, holding = 0;
        long arrDelay = 0, depDelay = 0;
        int onTime = 0, maxArr = 0;
        TreeMap<Integer, Integer> byHour = new TreeMap<>();

        for (Flight f : flights) {
            FlightStatus s = f.getStatus();
            if (s == FlightStatus.DIVERTED) {
                diversions++;
            } else if (s == FlightStatus.CANCELLED) {
                cancellations++;
            } else if (s == FlightStatus.HOLDING) {
                holding++;
            }
            if (f.getActualTime() < 0 || s == FlightStatus.DIVERTED) {
                continue;
            }
            int delay = f.delayMinutes();
            if (f.isArrival()) {
                landings++;
                arrDelay += delay;
                maxArr = Math.max(maxArr, delay);
            } else {
                departures++;
                depDelay += delay;
            }
            if (delay <= ON_TIME_LIMIT) {
                onTime++;
            }
            int hour = SimTime.START_HOUR + f.getActualTime() / 60;
            byHour.merge(hour, 1, Integer::sum);
        }

        int busy = 0;
        for (Runway r : runways) {
            busy += r.getBusyMinutes();
        }
        int capacity = Math.max(1, (elapsedMinutes + 1) * runways.size());
        int movements = landings + departures;

        return new Statistics(landings, departures, diversions, cancellations, holding,
                landings == 0 ? 0 : (double) arrDelay / landings,
                departures == 0 ? 0 : (double) depDelay / departures,
                movements == 0 ? 100 : 100.0 * onTime / movements,
                100.0 * busy / capacity, maxArr, byHour);
    }

    public int getLandings() { return landings; }
    public int getDepartures() { return departures; }
    public int getMovements() { return landings + departures; }
    public int getDiversions() { return diversions; }
    public int getCancellations() { return cancellations; }
    public int getHoldingNow() { return holdingNow; }
    public double getAvgArrivalDelay() { return avgArrivalDelay; }
    public double getAvgDepartureDelay() { return avgDepartureDelay; }
    public double getOnTimePercent() { return onTimePercent; }
    public double getRunwayUtilisation() { return runwayUtilisation; }
    public int getMaxArrivalDelay() { return maxArrivalDelay; }

    /** Landings plus take-offs per clock hour, in hour order. */
    public Map<Integer, Integer> getMovementsByHour() {
        return Collections.unmodifiableMap(movementsByHour);
    }

    @Override
    public String toString() {
        return String.format("landings=%d departures=%d diversions=%d avgArrDelay=%.1f avgDepDelay=%.1f onTime=%.0f%%",
                landings, departures, diversions, avgArrivalDelay, avgDepartureDelay, onTimePercent);
    }
}
