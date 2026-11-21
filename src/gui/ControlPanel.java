package gui;

import engine.ControlTower;
import engine.TowerSnapshot;
import events.Event;
import events.Events;
import exceptions.SkyControlException;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import model.FlightSnapshot;
import report.ReportExporter;

/**
 * Buttons to throw events at the airport during the demo: MAYDAY, runway closures,
 * a thunderstorm, and exporting the report. Errors from the engine (custom exceptions)
 * are shown here as a friendly message instead of crashing.
 */
public class ControlPanel extends JPanel implements Refreshable {
    private final ControlTower tower;
    private final DefaultComboBoxModel<String> airborne = new DefaultComboBoxModel<>();
    private final JComboBox<String> runwayBox = new JComboBox<>();
    private final JComboBox<String> closeFor = new JComboBox<>(new String[]{"15 min", "30 min", "until reopened"});
    private final JComboBox<String> stormFor = new JComboBox<>(new String[]{"10 min", "20 min", "30 min"});
    private final JLabel status = new JLabel(" ");
    private List<String> lastAirborne = new ArrayList<>();

    public ControlPanel(ControlTower tower) {
        super(new GridLayout(0, 1, 0, 2));
        this.tower = tower;
        Theme.dark(this);
        setBorder(Theme.titled("Events"));

        JComboBox<String> flightBox = new JComboBox<>(airborne);
        flightBox.setPrototypeDisplayValue("EK0000D  ");
        JButton mayday = button("Declare MAYDAY", Theme.RED);
        mayday.addActionListener(e -> {
            String f = (String) flightBox.getSelectedItem();
            fire(f == null ? null : Events.emergency(f), "Pick a flight in the air first");
        });
        add(row(new JLabel("Flight"), flightBox, mayday));

        for (TowerSnapshot.RunwayStatus rw : tower.snapshot().runways()) {
            runwayBox.addItem(rw.name());
        }
        JButton close = button("Close runway", Theme.ORANGE);
        close.addActionListener(e -> {
            int mins = closeFor.getSelectedIndex() == 0 ? 15 : closeFor.getSelectedIndex() == 1 ? 30 : 0;
            fire(Events.closeRunway((String) runwayBox.getSelectedItem(), mins), null);
        });
        JButton reopen = button("Reopen", Theme.GREEN);
        reopen.addActionListener(e -> fire(Events.reopenRunway((String) runwayBox.getSelectedItem()), null));
        add(row(new JLabel("Runway"), runwayBox, closeFor, close, reopen));

        JButton storm = button("Thunderstorm", Theme.ACCENT);
        storm.addActionListener(e -> fire(Events.storm(10 * (stormFor.getSelectedIndex() + 1)), null));
        JButton export = button("Export report (CSV)", Theme.TEXT);
        export.addActionListener(e -> export());
        add(row(new JLabel("Weather"), stormFor, storm, export));

        status.setFont(Theme.SMALL);
        status.setForeground(Theme.MUTED);
        add(row(status));
    }

    private void fire(Event event, String ifNull) {
        if (event == null) {
            show(ifNull, true);
            return;
        }
        try {
            tower.fireEvent(event);
            show(event.describe(), false);
        } catch (SkyControlException ex) {
            show(ex.getMessage(), true);
        }
    }

    private void export() {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path dir = Paths.get("data", "exports");
            ReportExporter ex = new ReportExporter();
            ex.exportFlights(tower.flightSnapshots(), dir.resolve("flights_" + stamp + ".csv"));
            ex.exportSummary(tower.statistics(), tower.getPolicy().code(), tower.getMinute(),
                    dir.resolve("summary_" + stamp + ".csv"));
            show("Saved flights_" + stamp + ".csv and summary_" + stamp + ".csv in data/exports", false);
        } catch (java.io.IOException ex) {
            show("Export failed: " + ex.getMessage(), true);
        }
    }

    private void show(String msg, boolean error) {
        status.setText(msg);
        status.setForeground(error ? Theme.RED : Theme.GREEN);
    }

    @Override
    public void refresh(TowerSnapshot s) {
        List<String> now = new ArrayList<>();
        for (FlightSnapshot f : s.flights()) {
            if (f.isArrival() && f.status().isAirborne()) {
                now.add(f.flightNumber());
            }
        }
        if (!now.equals(lastAirborne)) {
            Object selected = airborne.getSelectedItem();
            airborne.removeAllElements();
            for (String n : now) {
                airborne.addElement(n);
            }
            if (selected != null && now.contains(selected)) {
                airborne.setSelectedItem(selected);
            }
            lastAirborne = now;
        }
    }

    private static JButton button(String text, java.awt.Color accent) {
        JButton b = new JButton(text);
        b.setFont(Theme.BOLD);
        b.setToolTipText(text);
        b.setFocusPainted(false);
        return b;
    }

    private static JPanel row(java.awt.Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        for (java.awt.Component c : parts) {
            if (c instanceof JLabel l && l.getFont() != Theme.SMALL) {
                l.setForeground(Theme.MUTED);
                l.setPreferredSize(new java.awt.Dimension(55, 20));
            }
            p.add(c);
        }
        p.setBorder(BorderFactory.createEmptyBorder());
        return p;
    }
}
