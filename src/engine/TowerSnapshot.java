package engine;

import java.util.List;
import model.FlightSnapshot;
import model.GateSize;
import model.SimTime;
import report.Statistics;

/** Everything the screens need, copied at one moment so they can draw without locking. */
public record TowerSnapshot(
        int minute,
        String policyCode,
        String policyName,
        List<FlightSnapshot> flights,
        List<RunwayStatus> runways,
        List<GateStatus> gates,
        List<FlightSnapshot> queue,
        List<LogEntry> log,
        Statistics stats) {

    public String clock() {
        return SimTime.format(Math.max(0, minute));
    }

    public record RunwayStatus(String name, boolean open, int reopenAt, String lastFlight, int operations) {
    }

    public record GateStatus(String id, GateSize size, String occupant) {
        public boolean isFree() {
            return occupant == null;
        }
    }
}
