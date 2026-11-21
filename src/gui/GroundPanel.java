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

    public GroundPanel() {
        super(new BorderLayout(0, 8));
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
        q.setRowHeight(22);
        q.setBackground(Theme.BG);
        q.setForeground(Theme.TEXT);
        q.setGridColor(Theme.BORDER);
        q.getTableHeader().setFont(Theme.BOLD);
        q.setDefaultRenderer(Object.class, new QueueRenderer());
        JScrollPane sp = new JScrollPane(q);
        sp.getViewport().setBackground(Theme.BG);
        queuePanel.add(sp, BorderLayout.CENTER);
        add(queuePanel, BorderLayout.CENTER);

        JPanel gates = new JPanel(new BorderLayout());
        gates.setOpaque(false);
        JLabel gt = new JLabel("Gates   (A = large, B = normal)");
        gt.setFont(Theme.TITLE);
        gt.setForeground(Theme.TEXT);
        gt.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        gates.add(gt, BorderLayout.NORTH);
        gateGrid.setOpaque(false);
        gates.add(gateGrid, BorderLayout.CENTER);
        add(gates, BorderLayout.SOUTH);
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

        gateGrid.removeAll();
        for (TowerSnapshot.GateStatus g : s.gates()) {
            gateGrid.add(gateTile(g));
        }
        gateGrid.revalidate();
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

    private JPanel gateTile(TowerSnapshot.GateStatus g) {
        JPanel p = new JPanel(new GridLayout(2, 1));
        boolean free = g.isFree();
        p.setBackground(free ? Theme.PANEL_LIGHT : new Color(0x14532D));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(g.size() == GateSize.LARGE ? Theme.ACCENT : Theme.BORDER), Theme.padded(4)));
        p.add(label(g.id(), Theme.BOLD, Theme.TEXT));
        p.add(label(free ? "free" : g.occupant(), Theme.SMALL, free ? Theme.MUTED : Theme.GREEN));
        return p;
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
