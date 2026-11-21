package data;

import engine.LogEntry;
import exceptions.StorageException;
import java.util.List;
import model.FlightSnapshot;
import report.Statistics;

/**
 * Where simulation runs are saved. Two implementations: SqliteStorage (a real database via
 * JDBC) and InMemoryStorage (used in tests and if the database driver is missing).
 */
public interface Storage extends AutoCloseable {
    void open() throws StorageException;

    /** Starts a new run and returns its id. */
    int startRun(String policy) throws StorageException;

    /** Saves the latest state of a flight in a run (replaces any earlier save). */
    void saveFlight(int runId, FlightSnapshot flight) throws StorageException;

    void saveEvent(int runId, LogEntry entry) throws StorageException;

    /** Stores the final figures for a run. */
    void finishRun(int runId, Statistics stats) throws StorageException;

    List<RunSummary> listRuns() throws StorageException;

    List<FlightSnapshot> loadFlights(int runId) throws StorageException;

    int countEvents(int runId) throws StorageException;

    /** Short description, e.g. "SQLite (data/skycontrol.db)". */
    String describe();

    @Override
    void close();
}
