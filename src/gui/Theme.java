package gui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import model.FlightStatus;
import model.Priority;

/** Colours and fonts shared by every screen, so the app looks consistent. */
public final class Theme {
    public static final Color BG = new Color(0x0F172A);
    public static final Color PANEL = new Color(0x1E293B);
    public static final Color PANEL_LIGHT = new Color(0x273449);
    public static final Color BORDER = new Color(0x334155);
    public static final Color TEXT = new Color(0xE2E8F0);
    public static final Color MUTED = new Color(0x94A3B8);
    public static final Color ACCENT = new Color(0x38BDF8);
    public static final Color GREEN = new Color(0x4ADE80);
    public static final Color YELLOW = new Color(0xFACC15);
    public static final Color ORANGE = new Color(0xFB923C);
    public static final Color RED = new Color(0xF87171);
    public static final Color VIOLET = new Color(0xA78BFA);
    public static final Color RADAR_BG = new Color(0x04140C);
    public static final Color RADAR_LINE = new Color(0x14532D);

    public static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font SMALL = BASE.deriveFont(11f);
    public static final Font TITLE = BASE.deriveFont(Font.BOLD, 15f);
    public static final Font BIG = new Font(Font.MONOSPACED, Font.BOLD, 28);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Theme() {
    }

    /** Switches Swing to the Nimbus look with dark colours. Falls back quietly if unavailable. */
    public static void install() {
        try {
            javax.swing.UIManager.put("control", PANEL);
            javax.swing.UIManager.put("info", PANEL);
            javax.swing.UIManager.put("nimbusBase", new Color(0x18263D));
            javax.swing.UIManager.put("nimbusFocus", ACCENT);
            javax.swing.UIManager.put("nimbusLightBackground", BG);
            javax.swing.UIManager.put("nimbusSelectionBackground", new Color(0x1D4ED8));
            javax.swing.UIManager.put("nimbusDisabledText", MUTED);
            javax.swing.UIManager.put("text", TEXT);
            javax.swing.UIManager.put("defaultFont", BASE);
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (Exception ignored) {
            // keep the default look and feel
        }
    }

    public static Border padded(int pad) {
        return BorderFactory.createEmptyBorder(pad, pad, pad, pad);
    }

    public static Border titled(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), " " + title + " ",
                        0, 0, BOLD, MUTED),
                padded(4));
    }

    public static void dark(JComponent c) {
        c.setBackground(PANEL);
        c.setForeground(TEXT);
        c.setFont(BASE);
    }

    /** Colour used for a flight's status on boards and lists. */
    public static Color statusColor(FlightStatus s, Priority p) {
        if (p == Priority.EMERGENCY && !s.isFinished() && s.isAirborne()) {
            return RED;
        }
        if (p == Priority.LOW_FUEL && s.isAirborne()) {
            return ORANGE;
        }
        switch (s) {
            case APPROACHING: return ACCENT;
            case HOLDING: return YELLOW;
            case LANDED: case AT_GATE: case DEPARTED: return GREEN;
            case BOARDING: case READY_FOR_DEPARTURE: return VIOLET;
            case REMOTE_STAND: return ORANGE;
            case DIVERTED: case CANCELLED: return RED;
            default: return MUTED;
        }
    }
}
