package report;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import model.FlightSnapshot;
import model.SimTime;

/** Writes reports as CSV files that open directly in Excel or Google Sheets. */
public class ReportExporter {

    /** One row per flight: schedule, what actually happened, delay. */
    public Path exportFlights(List<FlightSnapshot> flights, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("flight,airline,type,from_to,aircraft,category,status,priority,scheduled,actual,delay_min,runway,gate");
            for (FlightSnapshot f : flights) {
                out.println(String.join(",",
                        f.flightNumber(), csv(f.airline()), f.type().name(), f.otherAirport(),
                        f.aircraftModel(), f.aircraftCategory(), f.status().name(), f.priority().name(),
                        f.scheduledClock(), f.actualClock(),
                        f.delayMinutes() < 0 ? "" : String.valueOf(f.delayMinutes()),
                        f.runway() == null ? "" : f.runway(), f.gate() == null ? "" : f.gate()));
            }
        }
        return file;
    }

    /** The headline figures and the movements per hour. */
    public Path exportSummary(Statistics s, String policy, int minute, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("measure,value");
            out.println("policy," + policy);
            out.println("simulated_until," + SimTime.format(Math.max(0, minute)));
            out.println("landings," + s.getLandings());
            out.println("departures," + s.getDepartures());
            out.println("diversions," + s.getDiversions());
            out.println("cancellations," + s.getCancellations());
            out.printf("avg_arrival_delay_min,%.2f%n", s.getAvgArrivalDelay());
            out.printf("avg_departure_delay_min,%.2f%n", s.getAvgDepartureDelay());
            out.println("max_arrival_delay_min," + s.getMaxArrivalDelay());
            out.printf("on_time_percent,%.1f%n", s.getOnTimePercent());
            out.printf("runway_utilisation_percent,%.1f%n", s.getRunwayUtilisation());
            out.println();
            out.println("hour,movements");
            for (Map.Entry<Integer, Integer> e : s.getMovementsByHour().entrySet()) {
                out.printf("%02d:00,%d%n", e.getKey() % 24, e.getValue());
            }
        }
        return file;
    }

    /** Quotes a value if it contains a comma. */
    static String csv(String v) {
        if (v == null) {
            return "";
        }
        return v.contains(",") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
