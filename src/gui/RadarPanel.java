package gui;

import engine.TowerSnapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import model.FlightSnapshot;
import model.FlightStatus;
import model.Priority;

/**
 * The tower radar. Aircraft are drawn by where they are in their journey:
 * <ul>
 *   <li>approaching flights move in from the edge along the bearing of their origin;
 *       the edge is 30 minutes out, the centre is the airport,</li>
 *   <li>holding flights circle close to the airport,</li>
 *   <li>departures fly outwards for 15 minutes after take-off.</li>
 * </ul>
 * Low-fuel flights are orange and emergencies flash red.
 */
public class RadarPanel extends JPanel implements Refreshable {
    private static final Map<String, Integer> BEARINGS = new HashMap<>();

    static {
        // rough compass bearing from Dubai to each airport, in degrees
        String[] data = {
                "LHR 310", "CDG 310", "FRA 315", "MUC 315", "AMS 315", "LUX 312", "MAN 310", "IST 310",
                "LTN 310", "NCE 305", "BOM 100", "DEL 75", "BLR 105", "HYD 100", "CCU 85", "COK 120",
                "TRV 125", "KHI 75", "ISB 60", "LHE 65", "KTM 75", "JFK 320", "SYD 115", "BKK 95",
                "SIN 105", "HKG 90", "CAI 285", "AMM 300", "BEY 305", "TBS 330", "ZNZ 225", "MCT 120",
                "DOH 290", "RUH 260", "JED 265", "BAH 290", "KWI 305", "AUH 210", "SHJ 30"};
        for (String d : data) {
            String[] p = d.split(" ");
            BEARINGS.put(p[0], Integer.parseInt(p[1]));
        }
    }

    private TowerSnapshot snap;

    public RadarPanel() {
        setBackground(Theme.RADAR_BG);
        setPreferredSize(new Dimension(560, 560));
    }

    @Override
    public void refresh(TowerSnapshot snapshot) {
        this.snap = snapshot;
        repaint();
    }

    static double bearing(String airport) {
        Integer b = BEARINGS.get(airport);
        return b != null ? b : Math.floorMod(airport == null ? 0 : airport.hashCode(), 360);
    }

    /** Spreads flights from the same region over a few degrees so their labels do not overlap. */
    static double spread(String flightNumber) {
        return (Math.floorMod(flightNumber.hashCode(), 9) - 4) * 4.5;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0 + 10;
        double r = Math.min(w, h) / 2.0 - 34;

        drawScope(g, cx, cy, r);
        if (snap == null) {
            g.dispose();
            return;
        }
        drawRunways(g, cx, cy, r);

        long millis = System.currentTimeMillis();
        boolean blinkOn = (millis / 400) % 2 == 0;
        int holdingIndex = 0;
        for (FlightSnapshot f : snap.flights()) {
            double[] pos = position(f, cx, cy, r, millis, holdingIndex);
            if (f.status() == FlightStatus.HOLDING) {
                holdingIndex++;
            }
            if (pos == null) {
                continue;
            }
            drawAircraft(g, f, pos[0], pos[1], pos[2], blinkOn);
        }
        drawHeader(g, w);
        drawLegend(g, h);
        g.dispose();
    }

    /** Returns {x, y, heading in degrees} or null if the flight is not on radar. */
    private double[] position(FlightSnapshot f, double cx, double cy, double r, long millis, int holdingIndex) {
        int now = snap.minute();
        double brg;
        double dist;
        double heading;
        switch (f.status()) {
            case APPROACHING: {
                brg = bearing(f.otherAirport()) + spread(f.flightNumber());
                double minutesOut = Math.max(0, f.scheduledTime() - now);
                dist = Math.max(0.3, Math.min(1.0, minutesOut / 30.0)) * r;
                heading = brg + 180;
                break;
            }
            case HOLDING: {
                double ring = 0.11 + 0.035 * (holdingIndex % 4);
                double speed = 18 + 3 * (holdingIndex % 3);              // degrees per second
                brg = (holdingIndex * 47 + millis / 1000.0 * speed) % 360;
                dist = ring * r;
                heading = brg + 90;
                break;
            }
            case DEPARTED: {
                int since = now - f.actualTime();
                if (since < 0 || since > 15) {
                    return null;
                }
                brg = bearing(f.otherAirport()) + spread(f.flightNumber());
                dist = Math.max(0.08, since / 15.0) * r;
                heading = brg;
                break;
            }
            default:
                return null;
        }
        double rad = Math.toRadians(brg);
        return new double[]{cx + dist * Math.sin(rad), cy - dist * Math.cos(rad), heading};
    }

    private void drawScope(Graphics2D g, double cx, double cy, double r) {
        g.setColor(Theme.RADAR_LINE);
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i <= 3; i++) {
            double rr = r * i / 3.0;
            g.draw(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
            g.setFont(Theme.SMALL);
            g.drawString((i * 10) + " min", (float) (cx + 4), (float) (cy - rr + 13));
        }
        g.drawLine((int) (cx - r), (int) cy, (int) (cx + r), (int) cy);
        g.drawLine((int) cx, (int) (cy - r), (int) cx, (int) (cy + r));
        g.setFont(Theme.BOLD);
        g.drawString("N", (float) cx - 4, (float) (cy - r - 6));
        g.drawString("S", (float) cx - 4, (float) (cy + r + 16));
        g.drawString("E", (float) (cx + r + 6), (float) cy + 5);
        g.drawString("W", (float) (cx - r - 18), (float) cy + 5);
    }

    private void drawRunways(Graphics2D g, double cx, double cy, double r) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(120 - 90));   // runways 12/30 point 120 degrees
        double len = r * 0.16;
        int i = 0;
        for (TowerSnapshot.RunwayStatus rw : snap.runways()) {
            double off = (i == 0 ? -1 : 1) * 6;
            g.setColor(rw.open() ? new Color(0xCBD5E1) : Theme.RED);
            g.fill(new Rectangle2D.Double(-len / 2, off - 1.5, len, 3));
            i++;
        }
        g.setTransform(old);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.drawString("DXB", (float) cx - 10, (float) (cy + 24));
    }

    private void drawAircraft(Graphics2D g, FlightSnapshot f, double x, double y, double heading, boolean blinkOn) {
        Color c;
        if (f.status() == FlightStatus.DEPARTED) {
            c = Theme.VIOLET;
        } else if (f.priority() == Priority.EMERGENCY) {
            c = blinkOn ? Theme.RED : new Color(0x7F1D1D);
        } else if (f.priority() == Priority.LOW_FUEL) {
            c = Theme.ORANGE;
        } else if (f.status() == FlightStatus.HOLDING) {
            c = Theme.YELLOW;
        } else {
            c = Theme.GREEN;
        }
        double size = "Widebody".equals(f.aircraftCategory()) || "Cargo".equals(f.aircraftCategory()) ? 9 : 7;

        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(heading));
        Path2D plane = new Path2D.Double();
        plane.moveTo(0, -size);
        plane.lineTo(size * 0.7, size * 0.8);
        plane.lineTo(0, size * 0.35);
        plane.lineTo(-size * 0.7, size * 0.8);
        plane.closePath();
        g.setColor(c);
        g.fill(plane);
        g.setTransform(old);

        if (f.priority() == Priority.EMERGENCY && f.status() != FlightStatus.DEPARTED && blinkOn) {
            g.setColor(Theme.RED);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(x - 14, y - 14, 28, 28));
        }

        g.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        g.setColor(c);
        g.drawString(f.flightNumber(), (float) x + 10, (float) y - 2);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        String sub = f.aircraftModel();
        if (f.isArrival() && f.status().isAirborne()) {
            sub += "  " + Math.round(f.fuelMinutes()) + "m fuel";
        }
        g.drawString(sub, (float) x + 10, (float) y + 10);
    }

    private void drawHeader(Graphics2D g, int w) {
        long approaching = snap.flights().stream().filter(f -> f.status() == FlightStatus.APPROACHING).count();
        long holding = snap.flights().stream().filter(f -> f.status() == FlightStatus.HOLDING).count();
        boolean anyClosed = snap.runways().stream().anyMatch(rw -> !rw.open());
        boolean allClosed = snap.runways().stream().noneMatch(TowerSnapshot.RunwayStatus::open);

        g.setFont(Theme.BOLD);
        g.setColor(Theme.GREEN);
        g.drawString("RADAR  " + snap.clock(), 12, 20);
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.drawString(approaching + " approaching   " + holding + " holding", 12, 36);
        if (allClosed) {
            g.setColor(Theme.RED);
            g.setFont(Theme.TITLE);
            String s = "ALL RUNWAYS CLOSED";
            g.drawString(s, w - g.getFontMetrics().stringWidth(s) - 12, 22);
        } else if (anyClosed) {
            g.setColor(Theme.ORANGE);
            g.setFont(Theme.BOLD);
            StringBuilder sb = new StringBuilder("Closed: ");
            snap.runways().stream().filter(rw -> !rw.open()).forEach(rw -> sb.append(rw.name()).append(' '));
            String s = sb.toString().trim();
            g.drawString(s, w - g.getFontMetrics().stringWidth(s) - 12, 22);
        }
    }

    private void drawLegend(Graphics2D g, int h) {
        Object[][] items = {
                {Theme.GREEN, "Approaching"}, {Theme.YELLOW, "Holding"}, {Theme.ORANGE, "Low fuel"},
                {Theme.RED, "Emergency"}, {Theme.VIOLET, "Departed"}};
        g.setFont(Theme.SMALL);
        int x = 12;
        for (Object[] it : items) {
            g.setColor((Color) it[0]);
            g.fillOval(x, h - 18, 8, 8);
            g.setColor(Theme.MUTED);
            g.drawString((String) it[1], x + 12, h - 10);
            x += g.getFontMetrics().stringWidth((String) it[1]) + 28;
        }
    }
}
