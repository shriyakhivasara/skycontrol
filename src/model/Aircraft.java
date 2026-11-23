package model;

import exceptions.InvalidFlightPlanException;

/**
 * An aircraft type. Each subclass knows its own physical needs, so the scheduler and
 * gate allocator never have to check which kind of aircraft they are dealing with
 * (polymorphism instead of if/else on type).
 */
public abstract class Aircraft {
    private final String model;

    protected Aircraft(String model) {
        this.model = model;
    }

    public String getModel() {
        return model;
    }

    /** How much turbulence it leaves behind (decides the gap to the next aircraft). */
    public abstract WakeCategory wakeCategory();

    /** Minutes the aircraft blocks the runway when landing or taking off. */
    public abstract int runwayOccupancyMinutes();

    /** The smallest gate it can park at. */
    public abstract GateSize requiredGateSize();

    /** Minutes needed at the gate between landing and being ready to leave. */
    public abstract int turnaroundMinutes();

    /** Short category name shown on screens, e.g. "Widebody". */
    public abstract String category();

    /** Every model code that Aircraft.of() accepts, for drop-down lists. */
    public static String[] knownModels() {
        return new String[]{"A320", "A321", "B737", "B738", "A330", "A350", "A380", "B777", "B787",
                "B747F", "B777F", "G650", "CL350", "E50P"};
    }

    /**
     * Factory: builds the right subclass from a model code such as "A380" or "B737".
     * Used when reading the flight schedule.
     */
    public static Aircraft of(String model) throws InvalidFlightPlanException {
        if (model == null) {
            throw new InvalidFlightPlanException("missing aircraft model");
        }
        String m = model.trim().toUpperCase();
        switch (m) {
            case "A320": case "A321": case "B737": case "B738":
                return new Narrowbody(m);
            case "A380": case "B777": case "A350": case "B787": case "A330":
                return new Widebody(m);
            case "B747F": case "B777F":
                return new Cargo(m);
            case "G650": case "CL350": case "E50P":
                return new BusinessJet(m);
            default:
                throw new InvalidFlightPlanException("unknown aircraft model '" + model + "'");
        }
    }

    @Override
    public String toString() {
        return model + " (" + category() + ")";
    }
}
