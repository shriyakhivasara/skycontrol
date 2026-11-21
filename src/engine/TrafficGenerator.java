package engine;

import exceptions.InvalidFlightPlanException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.Aircraft;
import model.Flight;

/**
 * Adds unscheduled traffic on top of the timetable: private jets and extra freighters
 * that file a flight plan at short notice. Uses a seeded Random so a run can be repeated
 * exactly (important for a fair policy comparison).
 */
public class TrafficGenerator {
    private static final String[][] TEMPLATES = {
            // model, airline, origin
            {"G650", "Private", "LTN"},
            {"CL350", "Private", "BAH"},
            {"E50P", "Private", "MCT"},
            {"B777F", "Emirates SkyCargo", "AMS"},
            {"B747F", "Cargolux", "LUX"},
            {"A320", "Air Arabia", "SHJ"},
    };
    private static final String[] DESTINATIONS = {"RUH", "JED", "KWI", "MCT", "BAH", "IST"};

    private final Random random;
    private final double flightsPerMinute;
    private int counter = 0;

    /**
     * @param seed             same seed gives the same extra traffic
     * @param flightsPerMinute average extra arrivals per simulated minute (0 to turn off)
     */
    public TrafficGenerator(long seed, double flightsPerMinute) {
        this.random = new Random(seed);
        this.flightsPerMinute = flightsPerMinute;
    }

    /**
     * Possibly creates an extra arrival (and its return departure) at this minute.
     * The arrival is due Flight.RADAR_RANGE_MINUTES from now, so it appears on radar at once.
     * @return either an empty list or [arrival, departure]
     */
    public List<Flight> generate(int now) {
        List<Flight> out = new ArrayList<>();
        if (random.nextDouble() >= flightsPerMinute) {
            return out;
        }
        String[] t = TEMPLATES[random.nextInt(TEMPLATES.length)];
        counter++;
        try {
            Aircraft aircraft = Aircraft.of(t[0]);
            String prefix = t[1].equals("Private") ? "PJ" : t[1].equals("Air Arabia") ? "G9" : "CG";
            int due = now + Flight.RADAR_RANGE_MINUTES;
            double fuel = 45 + random.nextInt(31);           // 45-75 minutes when first seen
            Flight arrival = Flight.arrival(prefix + (700 + counter), t[1], t[2], aircraft, due, fuel);
            int departs = due + aircraft.turnaroundMinutes() + 15;
            Flight departure = Flight.departure(prefix + (700 + counter) + "D", t[1],
                    DESTINATIONS[random.nextInt(DESTINATIONS.length)], aircraft, departs);
            out.add(arrival);
            out.add(departure);
        } catch (InvalidFlightPlanException e) {
            // templates are fixed and valid, so this cannot happen
            throw new IllegalStateException(e);
        }
        return out;
    }
}
