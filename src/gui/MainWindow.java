package gui;

import data.Storage;
import engine.ControlTower;
import engine.SimClock;
import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import model.FlightType;
import policy.Policies;

/**
 * The main window: radar on the left, boards and statistics in tabs on the right,
 * events and the tower log along the bottom. A Swing Timer takes a fresh snapshot of the
 * tower four times a second and hands it to every Refreshable panel.
 */
public class MainWindow extends JFrame {
    private static final int[] SPEEDS = {1, 2, 5, 10, 20};

    private final ControlTower tower;
    private final SimClock clock;
    private final List<Refreshable> panels = new ArrayList<>();
    private final JLabel clockLabel = new JLabel("06:00");
    private final JLabel subtitle = new JLabel();
    private final Timer timer;

    public MainWindow(ControlTower tower, SimClock clock, Storage storage, Runnable onExit) {
        super("SkyControl - Air Traffic Control Simulator");
        this.tower = tower;
        this.clock = clock;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                timer.stop();
                dispose();
                onExit.run();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        Theme.dark(root);
        root.add(topBar(), BorderLayout.NORTH);

        RadarPanel radar = new RadarPanel();
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.BOLD);
        tabs.addTab("Arrivals", register(new FlightBoardPanel(FlightType.ARRIVAL)));
        tabs.addTab("Departures", register(new FlightBoardPanel(FlightType.DEPARTURE)));
        tabs.addTab("Runways & gates", register(new GroundPanel()));
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

    private void refreshAll() {
        TowerSnapshot s = tower.snapshot();
        clockLabel.setText(s.clock());
        subtitle.setText(tower.getAirport().getName() + "   |   " + s.policyName()
                + "   |   " + (clock.isPaused() ? "PAUSED" : clock.getSpeed() + " sim min per second"));
        for (Refreshable r : panels) {
            r.refresh(s);
        }
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

        right.add(label("Speed"));
        right.add(speed);
        right.add(label("Policy"));
        right.add(policy);
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
