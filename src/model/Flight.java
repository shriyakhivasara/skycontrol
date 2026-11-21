package model;

import exceptions.InsufficientFuelException;
import exceptions.InvalidFlightPlanException;

/**
 * One flight: an arrival landing here or a departure leaving from here.
 * Times are simulation minutes (see SimTime).
 */
public class Flight {
    /** Below this many minutes of fuel a holding flight is raised to LOW_FUEL priority. */
    public static final double LOW_FUEL_THRESHOLD = 20;
    /** At this many minutes of fuel a holding flight must divert to another airport. */
    public static final double DIVERSION_RESERVE = 8;
    /** Arrivals appear on radar this many minutes before their scheduled landing. */
    public static final int RADAR_RANGE_MINUTES = 30;

    private static long nextSequence = 0;

    private final String flightNumber;
    private final String airline;
    private final String otherAirport;     // origin for arrivals, destination for departures
    private final FlightType type;
    private final Aircraft aircraft;
    private final int scheduledTime;
    private final long sequence;           // creation order, used to break ties fairly

    private FlightStatus status = FlightStatus.SCHEDULED;
    private Priority priority = Priority.NORMAL;
    private double fuelMinutes;            // only meaningful for arrivals
    private int requestTime = -1;          // when it asked for a runway
    private int actualTime = -1;           // when it actually landed or took off
    private String runwayName;
    private String gateId;
    private int gateArrivalTime = -1;
    private Flight linkedDeparture;        // the departure this arrival turns into

    private Flight(String flightNumber, String airline, String otherAirport, FlightType type,
                   Aircraft aircraft, int scheduledTime, double fuelMinutes)
            throws InvalidFlightPlanException {
        if (flightNumber == null || !flightNumber.matches("[A-Z0-9]{2}\\s?\\d{1,4}[A-Z]?")) {
            throw new InvalidFlightPlanException("bad flight number '" + flightNumber + "'");
        }
        if (aircraft == null) {
            throw new InvalidFlightPlanException(flightNumber + " has no aircraft");
        }
        if (scheduledTime < 0) {
            throw new InvalidFlightPlanException(flightNumber + " is scheduled before the simulation starts");
        }
        if (type == FlightType.ARRIVAL && fuelMinutes <= DIVERSION_RESERVE) {
            throw new InvalidFlightPlanException(flightNumber + " would arrive without enough fuel");
        }
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.otherAirport = otherAirport;
        this.type = type;
        this.aircraft = aircraft;
        this.scheduledTime = scheduledTime;
        this.fuelMinutes = fuelMinutes;
        this.sequence = nextSequence();
    }

    private static synchronized long nextSequence() {
        return nextSequence++;
    }

    /** Creates an arriving flight. fuelMinutes is the fuel it will have when it reaches the airport. */
    public static Flight arrival(String flightNumber, String airline, String origin, Aircraft aircraft,
                                 int scheduledTime, double fuelMinutes) throws InvalidFlightPlanException {
        return new Flight(flightNumber, airline, origin, FlightType.ARRIVAL, aircraft, scheduledTime, fuelMinutes);
    }

    /** Creates a departing flight. */
    public static Flight departure(String flightNumber, String airline, String destination, Aircraft aircraft,
                                   int scheduledTime) throws InvalidFlightPlanException {
        return new Flight(flightNumber, airline, destination, FlightType.DEPARTURE, aircraft, scheduledTime, 0);
    }

    // ---------- behaviour ----------

    /**
     * Burns one minute of fuel while airborne. Raises priority to LOW_FUEL when fuel is low.
     * @return true if the priority changed (so the runway queue must re-order)
     * @throws InsufficientFuelException if fuel has reached the diversion reserve
     */
    public boolean burnFuel(double minutes) throws InsufficientFuelException {
        if (type != FlightType.ARRIVAL || !status.isAirborne()) {
            return false;
        }
        fuelMinutes = Math.max(0, fuelMinutes - minutes);
        if (fuelMinutes <= DIVERSION_RESERVE) {
            throw new InsufficientFuelException(flightNumber, fuelMinutes);
        }
        if (fuelMinutes < LOW_FUEL_THRESHOLD && priority == Priority.NORMAL) {
            priority = Priority.LOW_FUEL;
            return true;
        }
        return false;
    }

    /** Declares an emergency (MAYDAY). Emergencies always go first under the priority policy. */
    public void declareEmergency() {
        priority = Priority.EMERGENCY;
    }

    /** Minutes late compared with the schedule (0 if on time or early, -1 if not finished). */
    public int delayMinutes() {
        if (actualTime < 0) {
            return -1;
        }
        return Math.max(0, actualTime - scheduledTime);
    }

    public boolean isArrival() {
        return type == FlightType.ARRIVAL;
    }

    // ---------- getters and setters ----------

    public String getFlightNumber() { return flightNumber; }
    public String getAirline() { return airline; }
    public String getOtherAirport() { return otherAirport; }
    public FlightType getType() { return type; }
    public Aircraft getAircraft() { return aircraft; }
    public int getScheduledTime() { return scheduledTime; }
    public long getSequence() { return sequence; }
    public FlightStatus getStatus() { return status; }
    public void setStatus(FlightStatus status) { this.status = status; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public double getFuelMinutes() { return fuelMinutes; }
    public int getRequestTime() { return requestTime; }
    public void setRequestTime(int requestTime) { this.requestTime = requestTime; }
    public int getActualTime() { return actualTime; }
    public void setActualTime(int actualTime) { this.actualTime = actualTime; }
    public String getRunwayName() { return runwayName; }
    public void setRunwayName(String runwayName) { this.runwayName = runwayName; }
    public String getGateId() { return gateId; }
    public void setGateId(String gateId) { this.gateId = gateId; }
    public int getGateArrivalTime() { return gateArrivalTime; }
    public void setGateArrivalTime(int t) { this.gateArrivalTime = t; }
    public Flight getLinkedDeparture() { return linkedDeparture; }
    public void setLinkedDeparture(Flight d) { this.linkedDeparture = d; }

    @Override
    public String toString() {
        return flightNumber + " " + (isArrival() ? "from " : "to ") + otherAirport
                + " [" + aircraft.getModel() + ", " + status + ", " + priority + "]";
    }
}
