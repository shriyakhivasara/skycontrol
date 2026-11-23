import data.AsyncStorageWriter;
import data.InMemoryStorage;
import data.ScheduleLoader;
import data.ScheduledFlight;
import data.SqliteStorage;
import data.Storage;
import engine.ControlTower;
import engine.SimClock;
import engine.TrafficGenerator;
import exceptions.InvalidFlightPlanException;
import exceptions.SkyControlException;
import gui.MainWindow;
import gui.Theme;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.Airport;
import policy.PriorityWithFuelPolicy;

/**
 * Starts SkyControl: loads the schedule, opens the database, starts the clock thread and opens
 * the main window. File > Open schedule closes the current run and starts a new one.
 *
 * Usage: java Main [schedule.csv]
 */
public class Main {
    private static final String DB_PATH = "data/skycontrol.db";
    private static final long TRAFFIC_SEED = 42;
    private static final double EXTRA_FLIGHTS_PER_MINUTE = 0.03;

    private static Storage storage;

    public static void main(String[] args) {
        Path schedule = Paths.get(args.length > 0 ? args[0] : "data/schedule.csv");
        Theme.install();
        storage = openStorage();
        ControlTower tower;
        try {
            tower = buildTower(schedule);
        } catch (Exception e) {
            fail("Could not load the flight schedule " + schedule + "\n\n" + e.getMessage());
            return;
        }
        ControlTower first = tower;
        SwingUtilities.invokeLater(() -> start(first));
    }

    /** Builds a control tower loaded with every flight in the schedule file. */
    static ControlTower buildTower(Path schedule) throws IOException, InvalidFlightPlanException {
        ControlTower tower = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(),
                new TrafficGenerator(TRAFFIC_SEED, EXTRA_FLIGHTS_PER_MINUTE));
        List<ScheduledFlight> flights = new ScheduleLoader().load(schedule);
        for (ScheduledFlight f : flights) {
            tower.addFlight(f.arrival(), f.departure());
        }
        System.out.println("Loaded " + flights.size() + " scheduled arrivals from " + schedule);
        return tower;
    }

    /** Starts one run: database run record, background writer, clock thread and window. */
    static void start(ControlTower tower) {
        int runId;
        try {
            runId = storage.startRun(tower.getPolicy().code());
        } catch (SkyControlException e) {
            System.err.println(e.getMessage() + " - continuing without saving");
            storage = new InMemoryStorage();
            runId = ((InMemoryStorage) storage).startRun(tower.getPolicy().code());
        }
        AsyncStorageWriter writer = new AsyncStorageWriter(storage, runId);
        tower.addListener(writer);
        SimClock clock = new SimClock(tower);
        clock.setSpeed(2);
        final int run = runId;

        Runnable saveRun = () -> {
            clock.stop();
            writer.stop();
            try {
                storage.finishRun(run, tower.statistics());
            } catch (SkyControlException e) {
                System.err.println(e.getMessage());
            }
            System.out.println("Run " + run + " saved: " + tower.statistics());
        };

        MainWindow[] window = new MainWindow[1];
        window[0] = new MainWindow(tower, clock, storage,
                () -> {
                    saveRun.run();
                    storage.close();
                    System.exit(0);
                },
                newSchedule -> {
                    ControlTower next;
                    try {
                        next = buildTower(newSchedule);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(window[0],
                                "Could not load " + newSchedule.getFileName() + "\n\n" + e.getMessage()
                                        + "\n\nThe current simulation keeps running.",
                                "Open schedule", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    saveRun.run();
                    window[0].closeWindow();
                    start(next);
                });
        window[0].setVisible(true);
        clock.start();
    }

    /** Uses SQLite if the driver is available, otherwise keeps runs in memory. */
    private static Storage openStorage() {
        if (SqliteStorage.driverAvailable()) {
            SqliteStorage s = new SqliteStorage(DB_PATH);
            try {
                s.open();
                System.out.println("Saving runs to " + s.describe());
                return s;
            } catch (SkyControlException e) {
                System.err.println(e.getMessage());
            }
        } else {
            System.err.println("SQLite driver not found in lib/ - runs will not be saved");
        }
        return new InMemoryStorage();
    }

    private static void fail(String message) {
        System.err.println(message);
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "SkyControl", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }
}
