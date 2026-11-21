package gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import model.FlightSnapshot;
import model.FlightType;

/** Table data for one flight board (arrivals or departures), sorted by scheduled time. */
public class FlightTableModel extends AbstractTableModel {
    private static final String[] ARRIVAL_COLS =
            {"Sched", "Flight", "From", "Airline", "Aircraft", "Status", "Landed", "Delay", "Runway", "Gate"};
    private static final String[] DEPARTURE_COLS =
            {"Sched", "Flight", "To", "Airline", "Aircraft", "Status", "Departed", "Delay", "Runway", "Gate"};

    private final FlightType type;
    private List<FlightSnapshot> rows = new ArrayList<>();

    public FlightTableModel(FlightType type) {
        this.type = type;
    }

    public void setFlights(List<FlightSnapshot> all) {
        List<FlightSnapshot> mine = new ArrayList<>();
        for (FlightSnapshot f : all) {
            if (f.type() == type) {
                mine.add(f);
            }
        }
        mine.sort(Comparator.comparingInt(FlightSnapshot::scheduledTime).thenComparing(FlightSnapshot::flightNumber));
        if (!mine.equals(rows)) {
            rows = mine;
            fireTableDataChanged();
        }
    }

    public FlightSnapshot getFlight(int row) {
        return rows.get(row);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return ARRIVAL_COLS.length; }

    @Override
    public String getColumnName(int c) {
        return type == FlightType.ARRIVAL ? ARRIVAL_COLS[c] : DEPARTURE_COLS[c];
    }

    @Override
    public Object getValueAt(int row, int col) {
        FlightSnapshot f = rows.get(row);
        switch (col) {
            case 0: return f.scheduledClock();
            case 1: return f.flightNumber();
            case 2: return f.otherAirport();
            case 3: return f.airline();
            case 4: return f.aircraftModel();
            case 5: {
                String s = f.status().label();
                if (f.status().isAirborne() && f.priority() != model.Priority.NORMAL) {
                    s += " (" + (f.priority() == model.Priority.EMERGENCY ? "MAYDAY" : "low fuel") + ")";
                }
                return s;
            }
            case 6: return f.actualTime() >= 0 && f.status() != model.FlightStatus.DIVERTED ? f.actualClock() : "";
            case 7: return f.delayMinutes() > 0 && f.status() != model.FlightStatus.DIVERTED ? "+" + f.delayMinutes() + "m" : "";
            case 8: return f.runway() == null ? "" : f.runway();
            case 9: return f.gate() == null ? "" : f.gate();
            default: return "";
        }
    }
}
