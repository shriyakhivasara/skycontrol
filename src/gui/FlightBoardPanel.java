package gui;

import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import model.FlightSnapshot;
import model.FlightStatus;
import model.FlightType;

/** An airport-style flight information board, like the screens in a terminal. */
public class FlightBoardPanel extends JPanel implements Refreshable {
    private final FlightTableModel model;
    private final JTable table;
    private final JLabel summary = new JLabel(" ");
    private final FlightType type;

    public FlightBoardPanel(FlightType type) {
        super(new BorderLayout());
        this.type = type;
        this.model = new FlightTableModel(type);
        Theme.dark(this);
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setBackground(Theme.BG);
        table.setForeground(Theme.TEXT);
        table.setGridColor(Theme.BORDER);
        table.setFont(Theme.BASE);
        table.setSelectionBackground(Theme.PANEL_LIGHT);
        table.getTableHeader().setFont(Theme.BOLD);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {55, 70, 50, 140, 60, 150, 60, 55, 55, 60};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setDefaultRenderer(Object.class, new BoardRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        summary.setForeground(Theme.MUTED);
        summary.setFont(Theme.SMALL);
        summary.setBorder(Theme.padded(6));
        add(summary, BorderLayout.SOUTH);
    }

    @Override
    public void refresh(TowerSnapshot s) {
        model.setFlights(s.flights());
        int total = model.getRowCount();
        int done = 0;
        for (int i = 0; i < total; i++) {
            if (model.getFlight(i).actualTime() >= 0 || model.getFlight(i).status().isFinished()) {
                done++;
            }
        }
        summary.setText(total + (type == FlightType.ARRIVAL ? " arrivals, " + done + " landed or diverted"
                : " departures, " + done + " departed or cancelled"));
    }

    /** Colours the status column and fades flights that have finished. */
    private class BoardRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, col);
            FlightSnapshot f = model.getFlight(row);
            boolean finished = f.status().isFinished() || f.status() == FlightStatus.LANDED;
            c.setBackground(selected ? Theme.PANEL_LIGHT : (row % 2 == 0 ? Theme.BG : new Color(0x131C31)));
            c.setForeground(finished ? Theme.MUTED : Theme.TEXT);
            c.setFont(col == 1 ? Theme.BOLD : Theme.BASE);
            if (col == 5) {
                c.setForeground(Theme.statusColor(f.status(), f.priority()));
                c.setFont(Theme.BOLD);
            } else if (col == 7 && f.delayMinutes() > 15) {
                c.setForeground(Theme.ORANGE);
            }
            c.setBorder(Theme.padded(3));
            return c;
        }
    }
}
