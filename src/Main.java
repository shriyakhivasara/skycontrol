import data.AsyncStorageWriter;
import data.InMemoryStorage;
import data.ScheduleLoader;
import data.ScheduledFlight;
import data.SqliteStorage;
import data.Storage;
import engine.ControlTower;
import engine.SimClock;
import engine.TrafficGenerator;
import exceptions.SkyControlException;
import gui.MainWindow;
import gui.Theme;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import model.Airport;
import policy.PriorityWithFuelPolicy;

/**
 * Starts SkyControl: loads the schedule, opens the database, starts the clock thread
 * and opens the main window.
 *
 * Usage: java Main [schedule.csv]
 */
public class Main {
    private static final String DB_PATH = "data/skycontrol.db";
    private static final long TRAFFIC_SEED = 42;
    private static final double EXTRA_FLIGHTS_PER_MINUTE = 0.03;

    public static void main(String[] args) {
        Path schedule = Paths.get(args.length > 0 ? args[0] : "data/schedule.csv");
        Theme.install();

        ControlTower tower = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(),
                new TrafficGenerator(TRAFFIC_SEED, EXTRA_FLIGHTS_PER_MINUTE));
        try {
            List<ScheduledFlight> flights = new ScheduleLoader().load(schedule);
            for (ScheduledFlight f : flights) {
                tower.addFlight(f.arrival(), f.departure());
            }
            System.out.println("Loaded " + flights.size() + " scheduled arrivals from " + schedule);
        } catch (Exception e) {
            fail("Could not load the flight schedule " + schedule + "\n\n" + e.getMessage());
            return;
        }

        Storage storage = openStorage();
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

        final Storage store = storage;
        final int run = runId;
        Runnable onExit = () -> {
            clock.stop();
            writer.stop();
            try {
                store.finishRun(run, tower.statistics());
            } catch (SkyControlException e) {
                System.err.println(e.getMessage());
            }
            store.close();
            System.out.println("Run " + run + " saved: " + tower.statistics());
            System.exit(0);
        };

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(tower, clock, store, onExit);
            window.setVisible(true);
            clock.start();
        });
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
