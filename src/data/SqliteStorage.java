package data;

import engine.LogEntry;
import exceptions.StorageException;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import model.FlightSnapshot;
import model.FlightStatus;
import model.FlightType;
import model.Priority;
import report.Statistics;

/**
 * Saves runs to a SQLite database file using JDBC.
 * Tables: runs (one row per simulation run), flights (final state of each flight in a run),
 * events (the control tower log).
 */
public class SqliteStorage implements Storage {
    private final String path;
    private Connection conn;

    public SqliteStorage(String path) {
        this.path = path;
    }

    /** True if the SQLite JDBC driver jar is on the classpath. */
    public static boolean driverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public synchronized void open() throws StorageException {
        try {
            File parent = new File(path).getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS runs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, started_at TEXT, policy TEXT,"
                        + "landings INTEGER DEFAULT 0, departures INTEGER DEFAULT 0, diversions INTEGER DEFAULT 0,"
                        + "avg_arrival_delay REAL DEFAULT 0, on_time_pct REAL DEFAULT 0)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS flights ("
                        + "run_id INTEGER, flight TEXT, airline TEXT, type TEXT, other_airport TEXT,"
                        + "aircraft TEXT, category TEXT, status TEXT, priority TEXT, fuel REAL,"
                        + "scheduled INTEGER, actual INTEGER, delay INTEGER, runway TEXT, gate TEXT,"
                        + "PRIMARY KEY (run_id, flight))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS events ("
                        + "run_id INTEGER, minute INTEGER, flight TEXT, message TEXT)");
            }
        } catch (SQLException e) {
            throw new StorageException("Could not open database " + path, e);
        }
    }

    @Override
    public synchronized int startRun(String policy) throws StorageException {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO runs (started_at, policy) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, now);
            ps.setString(2, policy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Could not start a run", e);
        }
    }

    @Override
    public synchronized void saveFlight(int runId, FlightSnapshot f) throws StorageException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO flights VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, runId);
            ps.setString(2, f.flightNumber());
            ps.setString(3, f.airline());
            ps.setString(4, f.type().name());
            ps.setString(5, f.otherAirport());
            ps.setString(6, f.aircraftModel());
            ps.setString(7, f.aircraftCategory());
            ps.setString(8, f.status().name());
            ps.setString(9, f.priority().name());
            ps.setDouble(10, f.fuelMinutes());
            ps.setInt(11, f.scheduledTime());
            ps.setInt(12, f.actualTime());
            ps.setInt(13, f.delayMinutes());
            ps.setString(14, f.runway());
            ps.setString(15, f.gate());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save " + f.flightNumber(), e);
        }
    }

    @Override
    public synchronized void saveEvent(int runId, LogEntry entry) throws StorageException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO events VALUES (?,?,?,?)")) {
            ps.setInt(1, runId);
            ps.setInt(2, entry.minute());
            ps.setString(3, entry.flight());
            ps.setString(4, entry.message());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save event", e);
        }
    }

    @Override
    public synchronized void finishRun(int runId, Statistics s) throws StorageException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE runs SET landings=?, departures=?, diversions=?, avg_arrival_delay=?, on_time_pct=? WHERE id=?")) {
            ps.setInt(1, s.getLandings());
            ps.setInt(2, s.getDepartures());
            ps.setInt(3, s.getDiversions());
            ps.setDouble(4, s.getAvgArrivalDelay());
            ps.setDouble(5, s.getOnTimePercent());
            ps.setInt(6, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not finish run " + runId, e);
        }
    }

    @Override
    public synchronized List<RunSummary> listRuns() throws StorageException {
        List<RunSummary> out = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM runs ORDER BY id")) {
            while (rs.next()) {
                out.add(new RunSummary(rs.getInt("id"), rs.getString("started_at"), rs.getString("policy"),
                        rs.getInt("landings"), rs.getInt("departures"), rs.getInt("diversions"),
                        rs.getDouble("avg_arrival_delay"), rs.getDouble("on_time_pct")));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not list runs", e);
        }
        return out;
    }

    @Override
    public synchronized List<FlightSnapshot> loadFlights(int runId) throws StorageException {
        List<FlightSnapshot> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM flights WHERE run_id=? ORDER BY scheduled")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FlightSnapshot(rs.getString("flight"), rs.getString("airline"),
                            FlightType.valueOf(rs.getString("type")), rs.getString("other_airport"),
                            rs.getString("aircraft"), rs.getString("category"),
                            FlightStatus.valueOf(rs.getString("status")), Priority.valueOf(rs.getString("priority")),
                            rs.getDouble("fuel"), rs.getInt("scheduled"), rs.getInt("actual"), rs.getInt("delay"),
                            rs.getString("runway"), rs.getString("gate")));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load flights of run " + runId, e);
        }
        return out;
    }

    @Override
    public synchronized int countEvents(int runId) throws StorageException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM events WHERE run_id=?")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Could not count events", e);
        }
    }

    @Override
    public String describe() {
        return "SQLite (" + path + ")";
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ignored) {
            // closing on exit; nothing useful to do
        }
    }
}
