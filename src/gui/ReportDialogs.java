package gui;

import engine.ControlTower;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import report.ReportExporter;

/** File chooser dialogs for exporting reports and opening a schedule. */
public final class ReportDialogs {
    private ReportDialogs() {
    }

    /**
     * Asks where to save, then writes the flights CSV and a summary CSV next to it.
     * @return a message for the status line, or null if the user cancelled
     */
    public static String exportReport(Component parent, ControlTower tower) throws IOException {
        File dir = new File("data", "exports");
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        JFileChooser chooser = new JFileChooser(dir);
        chooser.setDialogTitle("Export report");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        chooser.setSelectedFile(new File(dir, "flights_" + stamp + ".csv"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path flights = chooser.getSelectedFile().toPath();
        if (!flights.getFileName().toString().toLowerCase().endsWith(".csv")) {
            flights = flights.resolveSibling(flights.getFileName() + ".csv");
        }
        String base = flights.getFileName().toString().replaceFirst("(?i)\\.csv$", "");
        Path summary = flights.resolveSibling(base + "_summary.csv");
        ReportExporter ex = new ReportExporter();
        ex.exportFlights(tower.flightSnapshots(), flights);
        ex.exportSummary(tower.statistics(), tower.getPolicy().code(), tower.getMinute(), summary);
        return "Saved " + flights.getFileName() + " and " + summary.getFileName() + " in " + flights.getParent();
    }

    /** Lets the user pick a schedule CSV. Returns null if cancelled. */
    public static Path chooseSchedule(Component parent) {
        JFileChooser chooser = new JFileChooser(new File("data"));
        chooser.setDialogTitle("Open a flight schedule");
        chooser.setFileFilter(new FileNameExtensionFilter("Schedule CSV files", "csv"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }
}
