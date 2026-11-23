package gui;

import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import model.FlightSnapshot;
import model.GateSize;
import model.Priority;
import model.SimTime;

/** Runways, the runway queue in clearance order, and the gates. */
public class GroundPanel extends JPanel implements Refreshable {
    private final JPanel runwayRow = new JPanel(new GridLayout(1, 0, 8, 0));
    private final QueueModel queueModel = new QueueModel();
    private final JPanel gateGrid = new JPanel(new GridLayout(0, 7, 6, 6));
    private final JLabel queueTitle = new JLabel();
    private final JTable queueTable;
    private final engine.ControlTower tower;
    private final java.util.function.Consumer<String> openDetails;
    private final StatusLine status = new StatusLine();
    private final JLabel modeHint = new JLabel();
    private final List<javax.swing.JButton> clearButtons = new ArrayList<>();
    private String selectedFlight;
    private final java.util.Map<String, JPanel> gateTiles = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, JLabel> gateOccupants = new java.util.HashMap<>();

    public GroundPanel(engine.ControlTower tower, java.util.function.Consumer<String> openDetails) {
        super(new BorderLayout(0, 8));
        this.tower = tower;
        this.openDetails = openDetails;
        Theme.dark(this);
        setBorder(Theme.padded(8));

        runwayRow.setOpaque(false);
        add(runwayRow, BorderLayout.NORTH);

        JPanel queuePanel = new JPanel(new BorderLayout());
        queuePanel.setOpaque(false);
        queueTitle.setFont(Theme.TITLE);
        queueTitle.setForeground(Theme.TEXT);
        queueTitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        queuePanel.add(queueTitle, BorderLayout.NORTH);
        JTable q = new JTable(queueModel);
        queueTable = q;
        q.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        q.getSelectionModel().addListSelectionListener(e -> {
            int r = q.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0 && r < queueModel.getRowCount()) {
                selectedFlight = queueModel.get(r).flightNumber();
            }
        });
        q.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int r = q.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && r >= 0) {
                    openDetails.accept(queueModel.get(r).flightNumber());
                }
            }
        });
        q.setRowHeight(22);
        q.setBackground(Theme.BG);
        q.setForeground(Theme.TEXT);
        q.setGridColor(Theme.BORDER);
        q.getTableHeader().setFont(Theme.BOLD);
        q.setDefaultRenderer(Object.class, new QueueRenderer());
        JScrollPane sp = new JScrollPane(q);
        sp.getViewport().setBackground(Theme.BG);
        queuePanel.add(sp, BorderLayout.CENTER);
        queuePanel.add(clearanceBar(), BorderLayout.SOUTH);
        add(queuePanel, BorderLayout.CENTER);

        JPanel gates = new JPanel(new BorderLayout());
        gates.setOpaque(false);
        JLabel gt = new JLabel("Gates   (A = large, B = normal; click a parked aircraft for details)");
        gt.setFont(Theme.TITLE);
        gt.setForeground(Theme.TEXT);
        gt.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        gates.add(gt, BorderLayout.NORTH);
        gateGrid.setOpaque(false);
        gates.add(gateGrid, BorderLayout.CENTER);
        add(gates, BorderLayout.SOUTH);
    }

    /** Buttons under the queue: clear the selected flight on a runway, or send it to the back. */
    private JPanel clearanceBar() {
        JPanel bar = new JPanel(new java.awt.GridLayout(0, 1, 0, 2));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        buttons.setOpaque(false);
        JLabel l = new JLabel("Selected flight:");
        l.setForeground(Theme.MUTED);
        buttons.add(l);
        for (TowerSnapshot.RunwayStatus rw : tower.snapshot().runways()) {
            javax.swing.JButton b = new javax.swing.JButton("Clear on " + rw.name());
            b.addActionListener(e -> withSelected(f -> status.attempt(() -> {
                tower.clearFlight(f, rw.name());
                return f + " cleared on " + rw.name();
            })));
            clearButtons.add(b);
            buttons.add(b);
        }
        javax.swing.JButton back = new javax.swing.JButton("Send to back");
        back.addActionListener(e -> withSelected(f -> status.attempt(() -> {
            tower.sendToBackOfQueue(f);
            return f + " sent to the back of the queue";
        })));
        javax.swing.JButton details = new javax.swing.JButton("Details...");
        details.addActionListener(e -> withSelected(openDetails));
        buttons.add(back);
        buttons.add(details);
        bar.add(buttons);
        modeHint.setFont(Theme.SMALL);
        bar.add(modeHint);
        bar.add(status);
        return bar;
    }

    private void withSelected(java.util.function.Consumer<String> action) {
        int r = queueTable.getSelectedRow();
        if (r < 0 || r >= queueModel.getRowCount()) {
            status.error("Select a flight in the queue first");
            return;
        }
        action.accept(queueModel.get(r).flightNumber());
    }

    @Override
    public void refresh(TowerSnapshot s) {
        runwayRow.removeAll();
        for (TowerSnapshot.RunwayStatus rw : s.runways()) {
            runwayRow.add(runwayCard(rw));
        }
        runwayRow.revalidate();

        queueTitle.setText("Runway queue   " + s.queue().size() + " waiting   (" + s.policyName() + ")");
        queueModel.setRows(s.queue(), s.minute());
        if (selectedFlight != null) {
            for (int i = 0; i < queueModel.getRowCount(); i++) {
                if (queueModel.get(i).flightNumber().equals(selectedFlight)) {
                    queueTable.getSelectionModel().setSelectionInterval(i, i);
                }
            }
        }
        if (s.manualMode()) {
            modeHint.setText("MANUAL control: nothing moves until you clear it. Select a flight, then pick a runway. "
                    + "Score: " + s.scoreText());
            modeHint.setForeground(Theme.YELLOW);
        } else {
            modeHint.setText("Automatic control: the scheduler clears flights in this order. "
                    + "You can still clear a flight yourself.");
            modeHint.setForeground(Theme.MUTED);
        }

        updateGates(s.gates());
        repaint();
    }

    private JPanel runwayCard(TowerSnapshot.RunwayStatus rw) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.PANEL_LIGHT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, rw.open() ? Theme.GREEN : Theme.RED), Theme.padded(8)));
        p.add(label("Runway " + rw.name(), Theme.TITLE, Theme.TEXT));
        String state = rw.open() ? "OPEN" : "CLOSED" + (rw.reopenAt() >= 0 ? " until " + SimTime.format(rw.reopenAt()) : "");
        p.add(label(state, Theme.BOLD, rw.open() ? Theme.GREEN : Theme.RED));
        p.add(label("Last: " + (rw.lastFlight() == null ? "none" : rw.lastFlight()) + "    Movements: " + rw.operations(),
                Theme.SMALL, Theme.MUTED));
        return p;
    }

    /** Creates the gate tiles once; later refreshes only change their text and colour. */
    private void updateGates(List<TowerSnapshot.GateStatus> gates) {
        if (gateTiles.isEmpty()) {
            for (TowerSnapshot.GateStatus g : gates) {
                JPanel p = new JPanel(new GridLayout(2, 1));
                p.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(g.size() == GateSize.LARGE ? Theme.ACCENT : Theme.BORDER),
                        Theme.padded(4)));
                p.add(label(g.id(), Theme.BOLD, Theme.TEXT));
                JLabel who = label("free", Theme.SMALL, Theme.MUTED);
                p.add(who);
                p.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        String occupant = (String) p.getClientProperty("occupant");
                        if (occupant != null) {
                            openDetails.accept(occupant);
                        }
                    }
                });
                gateTiles.put(g.id(), p);
                gateOccupants.put(g.id(), who);
                gateGrid.add(p);
            }
            gateGrid.revalidate();
        }
        for (TowerSnapshot.GateStatus g : gates) {
            JPanel p = gateTiles.get(g.id());
            JLabel who = gateOccupants.get(g.id());
            boolean free = g.isFree();
            p.putClientProperty("occupant", g.occupant());
            p.setBackground(free ? Theme.PANEL_LIGHT : new Color(0x14532D));
            p.setToolTipText(free ? g.size() + " gate, free" : "Click for " + g.occupant() + " details");
            p.setCursor(java.awt.Cursor.getPredefinedCursor(free ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
            who.setText(free ? "free" : g.occupant());
            who.setForeground(free ? Theme.MUTED : Theme.GREEN);
        }
    }

    private static JLabel label(String text, java.awt.Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Table of waiting flights, in the order they will be cleared. */
    private static class QueueModel extends AbstractTableModel {
        private static final String[] COLS = {"#", "Flight", "Wants to", "Aircraft", "Priority", "Fuel", "Waiting"};
        private List<FlightSnapshot> rows = new ArrayList<>();
        private int now;

        void setRows(List<FlightSnapshot> r, int now) {
            this.rows = r;
            this.now = now;
            fireTableDataChanged();
        }

        FlightSnapshot get(int row) { return rows.get(row); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            FlightSnapshot f = rows.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return f.flightNumber();
                case 2: return f.isArrival() ? "Land" : "Take off";
                case 3: return f.aircraftModel() + " (" + f.aircraftCategory() + ")";
                case 4: return f.priority() == Priority.EMERGENCY ? "MAYDAY" : f.priority() == Priority.LOW_FUEL ? "Low fuel" : "Normal";
                case 5: return f.isArrival() ? Math.round(f.fuelMinutes()) + " min" : "";
                case 6: {
                    int waited = f.isArrival() ? now - f.scheduledTime() : 0;
                    return f.isArrival() ? Math.max(0, waited) + " min" : "";
                }
                default: return "";
            }
        }
    }

    private class QueueRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel c = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, row, col);
            FlightSnapshot f = queueModel.get(row);
            c.setBackground(row % 2 == 0 ? Theme.BG : new Color(0x131C31));
            c.setForeground(col == 4 ? Theme.statusColor(f.status(), f.priority()) : Theme.TEXT);
            if (col == 4 && f.priority() == Priority.NORMAL) {
                c.setForeground(Theme.MUTED);
            }
            return c;
        }
    }
}
