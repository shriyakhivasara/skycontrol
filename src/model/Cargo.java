package model;

/** Freighters such as the B747F. Heavy, and slow to unload. */
public class Cargo extends Aircraft {
    public Cargo(String model) {
        super(model);
    }

    @Override public WakeCategory wakeCategory() { return WakeCategory.HEAVY; }
    @Override public int runwayOccupancyMinutes() { return 2; }
    @Override public GateSize requiredGateSize() { return GateSize.LARGE; }
    @Override public int turnaroundMinutes() { return 120; }
    @Override public String category() { return "Cargo"; }
}
