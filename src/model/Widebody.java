package model;

/** Twin-aisle passenger jets such as the A380 or B777. */
public class Widebody extends Aircraft {
    public Widebody(String model) {
        super(model);
    }

    @Override public WakeCategory wakeCategory() { return WakeCategory.HEAVY; }
    @Override public int runwayOccupancyMinutes() { return 2; }
    @Override public GateSize requiredGateSize() { return GateSize.LARGE; }
    @Override public int turnaroundMinutes() { return 90; }
    @Override public String category() { return "Widebody"; }
}
