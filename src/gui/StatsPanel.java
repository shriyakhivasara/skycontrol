package gui;

import data.RunSummary;
import data.Storage;
import engine.TowerSnapshot;
import exceptions.StorageException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import report.Statistics;

/** Live performance figures, a movements-per-hour chart and past runs from the database. */
public class StatsPanel extends JPanel implements Refreshable {
    private final JLabel[] values = new JLabel[8];
    private final String[] names = {"Landings", "Take-offs", "Avg arrival delay", "Avg departure delay",
            "On time (within 15 min)", "Diversions", "Holding now", "Runway use"};
    private final HourChart chart = new HourChart();
    private final DefaultTableModel history = new DefaultTableModel(
            new String[]{"Run", "Started", "Policy", "Landings", "Take-offs", "Diversions", "Avg delay", "On time"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final Storage storage;

    public StatsPanel(Storage storage) {
        super(new BorderLayout(0, 10));
        this.storage = storage;
        Theme.dark(this);
        setBorder(Theme.padded(10));

        JPanel tiles = new JPanel(new GridLayout(2, 4, 8, 8));
        tiles.setOpaque(false);
        for (int i = 0; i < values.length; i++) {
            JPanel t = new JPanel(new BorderLayout());
            t.setBackground(Theme.PANEL_LIGHT);
            t.setBorder(Theme.padded(10));
            JLabel n = new JLabel(names[i]);
            n.setFont(Theme.SMALL);
            n.setForeground(Theme.MUTED);
            values[i] = new JLabel("-");
            values[i].setFont(Theme.TITLE.deriveFont(22f));
            values[i].setForeground(Theme.TEXT);
            t.add(n, BorderLayout.NORTH);
            t.add(values[i], BorderLayout.CENTER);
            tiles.add(t);
        }
        add(tiles, BorderLayout.NORTH);

        chart.setBorder(Theme.titled("Movements per hour"));
        add(chart, BorderLayout.CENTER);

        JPanel hist = new JPanel(new BorderLayout());
        hist.setOpaque(false);
        hist.setBorder(Theme.titled("Saved runs (" + storage.describe() + ")"));
        JTable ht = new JTable(history);
        ht.setRowHeight(20);
        ht.setBackground(Theme.BG);
        ht.setForeground(Theme.TEXT);
        ht.setGridColor(Theme.BORDER);
        JScrollPane sp = new JScrollPane(ht);
        sp.setPreferredSize(new Dimension(100, 120));
        sp.getViewport().setBackground(Theme.BG);
        hist.add(sp, BorderLayout.CENTER);
        JButton reload = new JButton("Refresh saved runs");
        reload.addActionListener(e -> loadHistory());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bp.setOpaque(false);
        bp.add(reload);
        hist.add(bp, BorderLayout.SOUTH);
        add(hist, BorderLayout.SOUTH);
        loadHistory();
    }

    public void loadHistory() {
        history.setRowCount(0);
        try {
            List<RunSummary> runs = storage.listRuns();
            for (int i = runs.size() - 1; i >= 0; i--) {
                RunSummary r = runs.get(i);
                history.addRow(new Object[]{r.id(), r.startedAt(), r.policy(), r.landings(), r.departures(),
                        r.diversions(), String.format("%.1f min", r.avgArrivalDelay()),
                        String.format("%.0f%%", r.onTimePercent())});
            }
        } catch (StorageException e) {
            history.addRow(new Object[]{"", e.getMessage(), "", "", "", "", "", ""});
        }
    }

    @Override
    public void refresh(TowerSnapshot s) {
        Statistics st = s.stats();
        values[0].setText(String.valueOf(st.getLandings()));
        values[1].setText(String.valueOf(st.getDepartures()));
        values[2].setText(String.format("%.1f min", st.getAvgArrivalDelay()));
        values[3].setText(String.format("%.1f min", st.getAvgDepartureDelay()));
        values[4].setText(String.format("%.0f%%", st.getOnTimePercent()));
        values[5].setText(String.valueOf(st.getDiversions()));
        values[6].setText(String.valueOf(st.getHoldingNow()));
        values[7].setText(String.format("%.0f%%", st.getRunwayUtilisation()));
        values[5].setForeground(st.getDiversions() > 0 ? Theme.RED : Theme.TEXT);
        values[6].setForeground(st.getHoldingNow() > 5 ? Theme.ORANGE : Theme.TEXT);
        chart.setData(st.getMovementsByHour());
    }

    /** A simple bar chart drawn with Java 2D. */
    private static class HourChart extends JPanel {
        private Map<Integer, Integer> data = Map.of();

        HourChart() {
            setBackground(Theme.PANEL);
            setPreferredSize(new Dimension(300, 200));
        }

        void setData(Map<Integer, Integer> d) {
            if (!d.equals(data)) {
                data = Map.copyOf(d);
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.Insets in = getInsets();
            int x0 = in.left + 30;
            int y0 = getHeight() - in.bottom - 22;
            int w = getWidth() - in.left - in.right - 40;
            int h = y0 - in.top - 10;
            g.setColor(Theme.BORDER);
            g.drawLine(x0, y0, x0 + w, y0);
            if (data.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.drawString("No movements yet", x0 + 10, y0 - 10);
                return;
            }
            int max = 1;
            for (int v : data.values()) {
                max = Math.max(max, v);
            }
            int n = data.size();
            int slot = Math.max(1, w / Math.max(n, 6));
            int i = 0;
            g.setFont(Theme.SMALL);
            for (Map.Entry<Integer, Integer> e : new java.util.TreeMap<>(data).entrySet()) {
                int bh = (int) ((double) e.getValue() / max * h);
                int bx = x0 + i * slot + slot / 6;
                int bw = slot * 2 / 3;
                g.setColor(Theme.ACCENT);
                g.fillRoundRect(bx, y0 - bh, bw, bh, 4, 4);
                g.setColor(Theme.TEXT);
                g.drawString(String.valueOf(e.getValue()), bx + bw / 2 - 6, y0 - bh - 4);
                g.setColor(Theme.MUTED);
                g.drawString(String.format("%02d:00", e.getKey() % 24), bx + bw / 2 - 15, y0 + 15);
                i++;
            }
            g.setColor(new Color(0, 0, 0, 0));
        }
    }
}
