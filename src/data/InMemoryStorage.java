package data;

import engine.LogEntry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.FlightSnapshot;
import report.Statistics;

/** Keeps runs in memory only. Nothing survives a restart. */
public class InMemoryStorage implements Storage {
    private final Map<Integer, RunSummary> runs = new LinkedHashMap<>();
    private final Map<Integer, Map<String, FlightSnapshot>> flights = new HashMap<>();
    private final Map<Integer, List<LogEntry>> events = new HashMap<>();
    private int nextId = 1;

    @Override public void open() { }

    @Override
    public synchronized int startRun(String policy) {
        int id = nextId++;
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        runs.put(id, new RunSummary(id, now, policy, 0, 0, 0, 0, 0));
        flights.put(id, new LinkedHashMap<>());
        events.put(id, new ArrayList<>());
        return id;
    }

    @Override
    public synchronized void saveFlight(int runId, FlightSnapshot f) {
        flights.computeIfAbsent(runId, k -> new LinkedHashMap<>()).put(f.flightNumber(), f);
    }

    @Override
    public synchronized void saveEvent(int runId, LogEntry entry) {
        events.computeIfAbsent(runId, k -> new ArrayList<>()).add(entry);
    }

    @Override
    public synchronized void finishRun(int runId, Statistics s) {
        RunSummary r = runs.get(runId);
        if (r != null) {
            runs.put(runId, new RunSummary(runId, r.startedAt(), r.policy(), s.getLandings(),
                    s.getDepartures(), s.getDiversions(), s.getAvgArrivalDelay(), s.getOnTimePercent()));
        }
    }

    @Override
    public synchronized List<RunSummary> listRuns() {
        return new ArrayList<>(runs.values());
    }

    @Override
    public synchronized List<FlightSnapshot> loadFlights(int runId) {
        return new ArrayList<>(flights.getOrDefault(runId, Map.of()).values());
    }

    @Override
    public synchronized int countEvents(int runId) {
        return events.getOrDefault(runId, List.of()).size();
    }

    @Override public String describe() { return "in memory (not saved)"; }
    @Override public void close() { }
}
