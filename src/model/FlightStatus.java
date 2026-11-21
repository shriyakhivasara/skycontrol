package model;

/** Every state a flight can be in, from first appearing on radar to leaving. */
public enum FlightStatus {
    SCHEDULED,            // not yet on radar
    APPROACHING,          // on radar, flying towards the airport
    HOLDING,              // arrived, circling until a runway slot is free
    LANDED,               // on the ground, looking for a gate
    REMOTE_STAND,         // parked away from the terminal because no gate was free
    AT_GATE,              // at the gate, being turned around
    READY_FOR_DEPARTURE,  // pushed back, waiting for a take-off slot
    DEPARTED,
    DIVERTED;             // sent to another airport

    /** True while the aircraft is in the air near the airport and burning fuel. */
    public boolean isAirborne() {
        return this == APPROACHING || this == HOLDING;
    }

    /** True once the flight is finished as far as this airport is concerned. */
    public boolean isFinished() {
        return this == DEPARTED || this == DIVERTED;
    }
}
