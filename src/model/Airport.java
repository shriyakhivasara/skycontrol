package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The airport: its runways and gates. */
public class Airport {
    private final String code;
    private final String name;
    private final List<Runway> runways = new ArrayList<>();
    private final List<Gate> gates = new ArrayList<>();

    public Airport(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** The default airport used in the simulation: 2 runways, 8 normal and 6 large gates. */
    public static Airport createDefault() {
        Airport a = new Airport("DXB", "Dubai International (simulated)");
        a.addRunway(new Runway("12L"));
        a.addRunway(new Runway("12R"));
        for (int i = 1; i <= 8; i++) {
            a.addGate(new Gate("B" + i, GateSize.NORMAL));
        }
        for (int i = 1; i <= 6; i++) {
            a.addGate(new Gate("A" + i, GateSize.LARGE));
        }
        return a;
    }

    public void addRunway(Runway r) { runways.add(r); }
    public void addGate(Gate g) { gates.add(g); }

    public Runway getRunway(String name) {
        for (Runway r : runways) {
            if (r.getName().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public List<Runway> getRunways() { return Collections.unmodifiableList(runways); }
    public List<Gate> getGates() { return Collections.unmodifiableList(gates); }
}
