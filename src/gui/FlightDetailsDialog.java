package gui;

import engine.ControlTower;
import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import model.FlightSnapshot;
import model.FlightStatus;
import model.Priority;

/**
 * Opens when the user double-clicks a flight on a board, the queue or a gate, or clicks a plane
 * on the radar. Shows the flight live and offers every action that makes sense for it.
 */
public class FlightDetailsDialog extends JDialog implements Refreshable {
    private final ControlTower tower;
    private final String flightNumber;
    private final String[] fieldNames = {"Flight", "Airline", "Type", "From / to", "Aircraft", "Status",
            "Priority", "Fuel", "Scheduled", "Actual", "Delay", "Runway", "Gate"};
    private final JLabel[] fields = new JLabel[fieldNames.length];
    private final List<JButton> clearButtons = new ArrayList<>();
    private final JButton toBack = new JButton("Send to back of queue");
    private final JButton mayday = new JButton("Declare MAYDAY");
    private final JButton divert = new JButton("Divert to AUH");
    private final DefaultComboBoxModel<String> freeGates = new DefaultComboBoxModel<>();
    private final JButton move = new JButton("Move to gate");
    private final StatusLine status = new StatusLine();
    private List<String> lastFreeGates = new ArrayList<>();

    public FlightDetailsDialog(Window owner, ControlTower tower, String flightNumber) {
        super(owner, "Flight " + flightNumber, ModalityType.MODELESS);
        this.tower = tower;
        this.flightNumber = flightNumber;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.PANEL);
        root.setBorder(Theme.padded(12));

        JPanel info = new JPanel(new GridLayout(0, 2, 10, 3));
        info.setOpaque(false);
        for (int i = 0; i < fieldNames.length; i++) {
            JLabel n = new JLabel(fieldNames[i]);
            n.setForeground(Theme.MUTED);
            fields[i] = new JLabel("-");
            fields[i].setForeground(Theme.TEXT);
            fields[i].setFont(Theme.BOLD);
            info.add(n);
            info.add(fields[i]);
        }
        root.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 4));
        actions.setOpaque(false);
        actions.setBorder(Theme.titled("Controller actions"));

        JPanel clearRow = row();
        for (TowerSnapshot.RunwayStatus rw : tower.snapshot().runways()) {
            JButton b = new JButton("Clear on " + rw.name());
            b.addActionListener(e -> status.attempt(() -> {
                tower.clearFlight(flightNumber, rw.name());
                return flightNumber + " cleared on " + rw.name();
            }));
            clearButtons.add(b);
            clearRow.add(b);
        }
        toBack.addActionListener(e -> status.attempt(() -> {
            tower.sendToBackOfQueue(flightNumber);
            return flightNumber + " sent to the back of the queue";
        }));
        clearRow.add(toBack);
        actions.add(clearRow);

        JPanel airRow = row();
        mayday.addActionListener(e -> status.attempt(() -> {
            tower.fireEvent(events.Events.emergency(flightNumber));
            return "MAYDAY declared for " + flightNumber;
        }));
        divert.addActionListener(e -> status.attempt(() -> {
            tower.divertByController(flightNumber);
            return flightNumber + " diverted to Abu Dhabi";
        }));
        airRow.add(mayday);
        airRow.add(divert);
        actions.add(airRow);

        JPanel gateRow = row();
        JComboBox<String> gateBox = new JComboBox<>(freeGates);
        gateBox.setPrototypeDisplayValue("A6 (NORMAL)        ");
        move.addActionListener(e -> {
            String g = (String) gateBox.getSelectedItem();
            if (g == null) {
                status.error("No free gate to move to");
                return;
            }
            String id = g.split(" ")[0];
            status.attempt(() -> {
                tower.moveToGate(flightNumber, id);
                return flightNumber + " moved to gate " + id;
            });
        });
        gateRow.add(new JLabel("Free gates"));
        gateRow.add(gateBox);
        gateRow.add(move);
        actions.add(gateRow);
        actions.add(status);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
        refresh(tower.snapshot());
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    private static JPanel row() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder());
        return p;
    }

    @Override
    public void refresh(TowerSnapshot s) {
        FlightSnapshot f = null;
        for (FlightSnapshot x : s.flights()) {
            if (x.flightNumber().equals(flightNumber)) {
                f = x;
            }
        }
        if (f == null) {
            return;
        }
        String[] v = {
                f.flightNumber(), f.airline(), f.isArrival() ? "Arrival" : "Departure", f.otherAirport(),
                f.aircraftModel() + " (" + f.aircraftCategory() + ")", f.status().label(),
                f.priority() == Priority.EMERGENCY ? "MAYDAY" : f.priority() == Priority.LOW_FUEL ? "Low fuel" : "Normal",
                f.isArrival() && !f.status().isFinished() && f.actualTime() < 0
                        ? Math.round(f.fuelMinutes()) + " min" : "-",
                f.scheduledClock(), f.actualClock().isEmpty() ? "-" : f.actualClock(),
                f.delayMinutes() < 0 ? "-" : f.delayMinutes() + " min",
                f.runway() == null ? "-" : f.runway(), f.gate() == null ? "-" : f.gate()};
        for (int i = 0; i < v.length; i++) {
            fields[i].setText(v[i]);
        }
        fields[5].setForeground(Theme.statusColor(f.status(), f.priority()));
        fields[6].setForeground(f.priority() == Priority.NORMAL ? Theme.TEXT : Theme.statusColor(f.status(), f.priority()));

        boolean queued = f.status() == FlightStatus.HOLDING || f.status() == FlightStatus.READY_FOR_DEPARTURE;
        for (JButton b : clearButtons) {
            b.setEnabled(queued);
        }
        toBack.setEnabled(queued);
        boolean airborne = f.isArrival() && f.status().isAirborne();
        mayday.setEnabled(airborne && f.priority() != Priority.EMERGENCY);
        divert.setEnabled(airborne);
        boolean parked = f.isArrival() && (f.status() == FlightStatus.AT_GATE || f.status() == FlightStatus.REMOTE_STAND);
        move.setEnabled(parked);

        List<String> free = new ArrayList<>();
        for (TowerSnapshot.GateStatus g : s.gates()) {
            if (g.isFree()) {
                free.add(g.id() + " (" + g.size() + ")");
            }
        }
        if (!free.equals(lastFreeGates)) {
            Object sel = freeGates.getSelectedItem();
            freeGates.removeAllElements();
            free.forEach(freeGates::addElement);
            if (sel != null && free.contains(sel)) {
                freeGates.setSelectedItem(sel);
            }
            lastFreeGates = free;
        }
    }
}
