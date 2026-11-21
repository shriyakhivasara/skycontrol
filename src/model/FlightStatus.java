package model;

/** Every state a flight can be in, from first appearing on radar to leaving. */
public enum FlightStatus {
    SCHEDULED,            // not yet on radar
    APPROACHING,          // on radar, flying towards the airport
    HOLDING,              // arrived, circling until a runway slot is free
    LANDED,               // on the ground, looking for a gate
    REMOTE_STAND,         // parked away from the terminal (no gate free); passengers go by bus
    AT_GATE,              // at the gate, being turned around
    BOARDING,             // a departure whose aircraft is being turned around
    READY_FOR_DEPARTURE,  // pushed back, waiting for a take-off slot
    DEPARTED,
    DIVERTED,             // sent to another airport
    CANCELLED;            // a departure whose inbound aircraft diverted

    /** Text shown on the flight boards. */
    public String label() {
        switch (this) {
            case SCHEDULED: return "Scheduled";
            case APPROACHING: return "On approach";
            case HOLDING: return "Holding";
            case LANDED: return "Landed";
            case REMOTE_STAND: return "Remote stand";
            case AT_GATE: return "At gate";
            case BOARDING: return "Boarding";
            case READY_FOR_DEPARTURE: return "Taxiing";
            case DEPARTED: return "Departed";
            case DIVERTED: return "Diverted";
            case CANCELLED: return "Cancelled";
            default: return name();
        }
    }

    /** True while the aircraft is in the air near the airport and burning fuel. */
    public boolean isAirborne() {
        return this == APPROACHING || this == HOLDING;
    }

    /** True once the flight is finished as far as this airport is concerned. */
    public boolean isFinished() {
        return this == DEPARTED || this == DIVERTED || this == CANCELLED;
    }
}
