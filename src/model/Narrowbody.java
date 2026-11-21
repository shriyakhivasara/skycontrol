package model;

/** Single-aisle jets such as the A320 or B737. */
public class Narrowbody extends Aircraft {
    public Narrowbody(String model) {
        super(model);
    }

    @Override public WakeCategory wakeCategory() { return WakeCategory.MEDIUM; }
    @Override public int runwayOccupancyMinutes() { return 1; }
    @Override public GateSize requiredGateSize() { return GateSize.NORMAL; }
    @Override public int turnaroundMinutes() { return 45; }
    @Override public String category() { return "Narrowbody"; }
}
