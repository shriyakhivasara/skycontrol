package model;

/** Small private jets. Light, so they need the biggest gap behind a heavy aircraft. */
public class BusinessJet extends Aircraft {
    public BusinessJet(String model) {
        super(model);
    }

    @Override public WakeCategory wakeCategory() { return WakeCategory.LIGHT; }
    @Override public int runwayOccupancyMinutes() { return 1; }
    @Override public GateSize requiredGateSize() { return GateSize.NORMAL; }
    @Override public int turnaroundMinutes() { return 30; }
    @Override public String category() { return "Business jet"; }
}
