package data;

import exceptions.InvalidFlightPlanException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import model.Aircraft;
import model.Flight;
import model.SimTime;

/**
 * Reads the flight schedule from a CSV file. Lines starting with # are comments.
 * Columns: arrival_flight, airline, origin, aircraft, arrival_time, fuel_min,
 *          departure_flight, destination, departure_time
 * The last three columns may be left empty for an aircraft that does not leave again.
 */
public class ScheduleLoader {
    private static final int COLUMNS = 9;

    public List<ScheduledFlight> load(Path file) throws IOException, InvalidFlightPlanException {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(in);
        }
    }

    public List<ScheduledFlight> parse(BufferedReader in) throws IOException, InvalidFlightPlanException {
        List<ScheduledFlight> out = new ArrayList<>();
        String line;
        int lineNo = 0;
        boolean headerSkipped = false;
        while ((line = in.readLine()) != null) {
            lineNo++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!headerSkipped && trimmed.toLowerCase().startsWith("arrival_flight")) {
                headerSkipped = true;
                continue;
            }
            out.add(parseLine(trimmed, lineNo));
        }
        return out;
    }

    private ScheduledFlight parseLine(String line, int lineNo) throws InvalidFlightPlanException {
        String[] p = line.split(",", -1);
        if (p.length != COLUMNS) {
            throw new InvalidFlightPlanException("line " + lineNo + ": expected " + COLUMNS
                    + " columns but found " + p.length);
        }
        for (int i = 0; i < p.length; i++) {
            p[i] = p[i].trim();
        }
        try {
            Aircraft aircraft = Aircraft.of(p[3]);
            int arrives = SimTime.parse(p[4]);
            double fuel = Double.parseDouble(p[5]);
            Flight arrival = Flight.arrival(p[0], p[1], p[2], aircraft, arrives, fuel);
            Flight departure = null;
            if (!p[6].isEmpty()) {
                departure = Flight.departure(p[6], p[1], p[7], aircraft, SimTime.parse(p[8]));
                if (departure.getScheduledTime() <= arrives) {
                    throw new InvalidFlightPlanException(p[6] + " leaves before its aircraft arrives");
                }
            }
            return new ScheduledFlight(arrival, departure);
        } catch (InvalidFlightPlanException e) {
            throw new InvalidFlightPlanException("line " + lineNo + ": "
                    + e.getMessage().replace("Invalid flight plan: ", ""));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new InvalidFlightPlanException("line " + lineNo + ": bad number or time ("
                    + e.getMessage() + ")");
        }
    }
}
