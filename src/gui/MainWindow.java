package gui;

import data.Storage;
import engine.ControlTower;
import engine.SimClock;
import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import model.FlightType;
import policy.Policies;

/**
 * The main window: radar on the left, boards and statistics in tabs on the right, events and
 * the tower log along the bottom, and a menu bar. A Swing Timer takes a fresh snapshot of the
 * tower four times a second and hands it to every Refreshable panel and open details window.
 */
public class MainWindow extends JFrame {
    private static final int[] SPEEDS = {1, 2, 5, 10, 20};
    private static final String AUTO = "Automatic";
    private static final String MANUAL = "Manual (you control)";

    private final ControlTower tower;
    private final SimClock clock;
    private final List<Refreshable> panels = new ArrayList<>();
    private final List<FlightDetailsDialog> detailWindows = new ArrayList<>();
    private final JLabel clockLabel = new JLabel("06:00");
    private final JLabel subtitle = new JLabel();
    private final JLabel scoreLabel = new JLabel();
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{AUTO, MANUAL});
    {
        modeBox.setPrototypeDisplayValue(MANUAL + "    ");
    }
    private final JRadioButtonMenuItem autoItem = new JRadioButtonMenuItem(AUTO);
    private final JRadioButtonMenuItem manualItem = new JRadioButtonMenuItem(MANUAL);
    private final StatusLine menuStatus = new StatusLine();
    private final Timer timer;
    private Consumer<Path> onOpenSchedule = p -> { };

    /**
     * @param onExit         called when the window closes (saves the run)
     * @param onOpenSchedule called with a new schedule file the user picked (starts a new run)
     */
    public MainWindow(ControlTower tower, SimClock clock, Storage storage, Runnable onExit,
                      Consumer<Path> onOpenSchedule) {
        super("SkyControl - Air Traffic Control Simulator");
        this.onOpenSchedule = onOpenSchedule;
        this.tower = tower;
        this.clock = clock;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
                onExit.run();
            }
        });

        Consumer<String> openDetails = this::openDetails;

        JPanel root = new JPanel(new BorderLayout());
        Theme.dark(root);
        root.add(topBar(), BorderLayout.NORTH);

        RadarPanel radar = new RadarPanel(openDetails);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.BOLD);
        tabs.addTab("Arrivals", register(new FlightBoardPanel(FlightType.ARRIVAL, openDetails)));
        tabs.addTab("Departures", register(new FlightBoardPanel(FlightType.DEPARTURE, openDetails)));
        tabs.addTab("Runways & gates", register(new GroundPanel(tower, openDetails)));
        StatsPanel stats = new StatsPanel(storage);
        tabs.addTab("Statistics", register(stats));
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == stats) {
                stats.loadHistory();
            }
        });
        register(radar);

        JSplitPane centre = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, radar, tabs);
        centre.setResizeWeight(0.45);
        centre.setBorder(null);
        root.add(centre, BorderLayout.CENTER);

        ControlPanel controls = new ControlPanel(tower);
        LogPanel log = new LogPanel();
        register(controls);
        register(log);
        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controls, log);
        bottom.setResizeWeight(0.45);
        bottom.setBorder(null);
        bottom.setPreferredSize(new Dimension(100, 175));
        root.add(bottom, BorderLayout.SOUTH);

        setJMenuBar(menuBar(tabs, onExit, onOpenSchedule));
        setContentPane(root);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1100, 720));
        setLocationRelativeTo(null);

        timer = new Timer(250, e -> refreshAll());
        timer.start();
        refreshAll();
    }

    private JPanel register(JPanel p) {
        panels.add((Refreshable) p);
        return p;
    }

    /** Stops refreshing and closes this window and any details windows. */
    public void closeWindow() {
        timer.stop();
        for (FlightDetailsDialog d : new ArrayList<>(detailWindows)) {
            d.dispose();
        }
        dispose();
    }

    /** Opens (or brings forward) the details window for a flight. */
    public void openDetails(String flightNumber) {
        if (flightNumber == null) {
            return;
        }
        for (FlightDetailsDialog d : detailWindows) {
            if (d.getFlightNumber().equals(flightNumber)) {
                d.toFront();
                return;
            }
        }
        FlightDetailsDialog d = new FlightDetailsDialog(this, tower, flightNumber);
        d.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                detailWindows.remove(d);
            }
        });
        d.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        detailWindows.add(d);
        d.setVisible(true);
    }

    /** Saves this run and starts a new simulation from another schedule file. */
    public void startNewRun(Path schedule) {
        onOpenSchedule.accept(schedule);
    }

    /** Opens the flight plan form. */
    public void openFlightPlanForm() {
        FlightPlanDialog dialog = new FlightPlanDialog(this, tower);
        dialog.setVisible(true);
        if (dialog.getResult() != null) {
            menuStatus.ok(dialog.getResult());
        }
    }

    private void refreshAll() {
        TowerSnapshot s = tower.snapshot();
        clockLabel.setText(s.clock());
        subtitle.setText(clock.isPaused() ? "PAUSED" : clock.getSpeed() + " min/sec");
        subtitle.setForeground(clock.isPaused() ? Theme.ORANGE : Theme.MUTED);
        String wanted = s.manualMode() ? MANUAL : AUTO;
        if (!wanted.equals(modeBox.getSelectedItem())) {
            modeBox.setSelectedItem(wanted);
        }
        autoItem.setSelected(!s.manualMode());
        manualItem.setSelected(s.manualMode());
        scoreLabel.setText(s.manualMode() ? "Score " + s.score() : "");
        for (Refreshable r : panels) {
            r.refresh(s);
        }
        for (FlightDetailsDialog d : detailWindows) {
            d.refresh(s);
        }
    }

    private JMenuBar menuBar(JTabbedPane tabs, Runnable onExit, Consumer<Path> onOpenSchedule) {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open schedule...");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        open.addActionListener(e -> {
            Path p = ReportDialogs.chooseSchedule(this);
            if (p != null) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "Start a new simulation from " + p.getFileName() + "?\nThe current run will be saved and closed.",
                        "Open schedule", JOptionPane.OK_CANCEL_OPTION);
                if (ok == JOptionPane.OK_OPTION) {
                    startNewRun(p);
                }
            }
        });
        JMenuItem export = new JMenuItem("Export report...");
        export.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        export.addActionListener(e -> {
            try {
                String msg = ReportDialogs.exportReport(this, tower);
                if (msg != null) {
                    menuStatus.ok(msg);
                }
            } catch (java.io.IOException ex) {
                menuStatus.error("Export failed: " + ex.getMessage());
            }
        });
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            closeWindow();
            onExit.run();
        });
        file.add(open);
        file.add(export);
        file.addSeparator();
        file.add(exit);

        JMenu flights = new JMenu("Flights");
        JMenuItem plan = new JMenuItem("File a flight plan...");
        plan.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        plan.addActionListener(e -> openFlightPlanForm());
        JMenuItem find = new JMenuItem("Find a flight...");
        find.addActionListener(e -> {
            String no = JOptionPane.showInputDialog(this, "Flight number (e.g. EK512):", "Find a flight",
                    JOptionPane.QUESTION_MESSAGE);
            if (no != null && !no.isBlank()) {
                String n = no.trim().toUpperCase();
                if (tower.getFlight(n) == null) {
                    menuStatus.error("No flight called " + n);
                } else {
                    openDetails(n);
                }
            }
        });
        flights.add(plan);
        flights.add(find);

        JMenu control = new JMenu("Control");
        ButtonGroup g = new ButtonGroup();
        g.add(autoItem);
        g.add(manualItem);
        autoItem.addActionListener(e -> tower.setManualMode(false));
        manualItem.addActionListener(e -> {
            tower.setManualMode(true);
            tabs.setSelectedIndex(2);
        });
        control.add(autoItem);
        control.add(manualItem);

        JMenu help = new JMenu("Help");
        JMenuItem how = new JMenuItem("How to play controller...");
        how.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "<html><b>Manual control</b><br>"
                        + "1. Choose Manual in the top bar (or Control menu).<br>"
                        + "2. Open the Runways &amp; gates tab, select a flight in the queue, click Clear on 12L or 12R.<br>"
                        + "3. Clearing too soon behind a heavy jet is refused (-25 points).<br>"
                        + "4. On-time movements +10, late +4, emergency landed +15 bonus, diversion -50.<br><br>"
                        + "<b>Anywhere</b><br>"
                        + "Double-click a flight on a board, click a plane on the radar, or click a parked aircraft<br>"
                        + "on a gate for its details: MAYDAY, divert, send to back of queue, move to a gate.<br>"
                        + "Flights &gt; File a flight plan adds your own flight.</html>",
                "How to play controller", JOptionPane.INFORMATION_MESSAGE));
        help.add(how);

        bar.add(file);
        bar.add(flights);
        bar.add(control);
        bar.add(help);
        bar.add(javax.swing.Box.createHorizontalStrut(20));
        bar.add(menuStatus);
        return bar;
    }

    private JPanel topBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), Theme.padded(8)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        JLabel title = new JLabel("SkyControl");
        title.setFont(Theme.TITLE.deriveFont(20f));
        title.setForeground(Theme.ACCENT);
        clockLabel.setFont(Theme.BIG);
        clockLabel.setForeground(Theme.GREEN);
        subtitle.setFont(Theme.SMALL);
        subtitle.setForeground(Theme.MUTED);
        left.add(title);
        left.add(clockLabel);
        left.add(subtitle);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);
        JButton play = new JButton(clock.isPaused() ? "Resume" : "Pause");
        play.addActionListener(e -> {
            if (clock.isPaused()) {
                clock.resume();
                play.setText("Pause");
            } else {
                clock.pause();
                play.setText("Resume");
            }
        });
        JComboBox<String> speed = new JComboBox<>();
        for (int s : SPEEDS) {
            speed.addItem(s + "x");
        }
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == clock.getSpeed()) {
                speed.setSelectedIndex(i);
            }
        }
        speed.addActionListener(e -> clock.setSpeed(SPEEDS[speed.getSelectedIndex()]));
        JComboBox<String> policy = new JComboBox<>(new String[]{"PRIORITY_FUEL", "FCFS"});
        policy.setPrototypeDisplayValue("PRIORITY_FUEL   ");
        policy.setSelectedItem(tower.getPolicy().code());
        policy.addActionListener(e -> tower.setPolicy(Policies.byCode((String) policy.getSelectedItem())));
        modeBox.addActionListener(e -> {
            boolean manual = MANUAL.equals(modeBox.getSelectedItem());
            if (manual != tower.isManualMode()) {
                tower.setManualMode(manual);
            }
        });
        JButton plan = new JButton("+ Flight plan");
        plan.addActionListener(e -> openFlightPlanForm());
        scoreLabel.setFont(Theme.BOLD);
        scoreLabel.setForeground(Theme.YELLOW);

        scoreLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        right.add(scoreLabel);
        right.add(label("Mode"));
        right.add(modeBox);
        right.add(label("Speed"));
        right.add(speed);
        right.add(label("Policy"));
        right.add(policy);
        right.add(plan);
        right.add(play);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(Theme.MUTED);
        return l;
    }
}
