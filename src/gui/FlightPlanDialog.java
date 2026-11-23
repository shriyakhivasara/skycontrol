package gui;

import engine.ControlTower;
import exceptions.InvalidFlightPlanException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import model.Aircraft;
import model.Flight;
import model.SimTime;

/**
 * A form for filing a new flight plan. Every field is checked: a bad flight number, an
 * unknown time format, too little fuel, a duplicate number or a time in the past is shown
 * in red under the form, and nothing is added until everything is valid.
 */
public class FlightPlanDialog extends JDialog {
    private final ControlTower tower;
    private final JTextField number = new JTextField("XY101", 8);
    private final JTextField airline = new JTextField("SkyControl Air", 14);
    private final JTextField origin = new JTextField("BOM", 5);
    private final JComboBox<String> aircraft = new JComboBox<>(Aircraft.knownModels());
    private final JTextField arrival = new JTextField(6);
    private final JSpinner fuel = new JSpinner(new SpinnerNumberModel(60, 1, 300, 5));
    private final JCheckBox returns = new JCheckBox("Aircraft leaves again", true);
    private final JTextField depNumber = new JTextField("XY102", 8);
    private final JTextField destination = new JTextField("BOM", 5);
    private final JTextField departure = new JTextField(6);
    private final JLabel error = new JLabel(" ");
    private String result;

    public FlightPlanDialog(Window owner, ControlTower tower) {
        super(owner, "File a flight plan", ModalityType.APPLICATION_MODAL);
        this.tower = tower;

        int now = Math.max(0, tower.getMinute());
        arrival.setText(SimTime.format(now + 35));
        departure.setText(SimTime.format(now + 35 + 75));
        aircraft.setSelectedItem("A320");

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.PANEL);
        root.setBorder(Theme.padded(14));

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 6));
        form.setOpaque(false);
        add(form, "Flight number (e.g. EK512)", number);
        add(form, "Airline", airline);
        add(form, "Coming from (airport code)", origin);
        add(form, "Aircraft", aircraft);
        add(form, "Arrival time (HH:MM)", arrival);
        add(form, "Fuel when on radar (minutes)", fuel);
        form.add(new JLabel(""));
        returns.setOpaque(false);
        returns.setForeground(Theme.TEXT);
        form.add(returns);
        add(form, "Return flight number", depNumber);
        add(form, "Going to (airport code)", destination);
        add(form, "Departure time (HH:MM)", departure);
        returns.addActionListener(e -> {
            depNumber.setEnabled(returns.isSelected());
            destination.setEnabled(returns.isSelected());
            departure.setEnabled(returns.isSelected());
        });
        JLabel hint = new JLabel("Now " + SimTime.format(now) + ". Flights appear on radar 30 min before arrival.");
        hint.setForeground(Theme.MUTED);
        hint.setFont(Theme.SMALL);
        root.add(hint, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        error.setForeground(Theme.RED);
        bottom.add(error, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton file = new JButton("File flight plan");
        file.addActionListener(e -> submit());
        buttons.add(cancel);
        buttons.add(file);
        bottom.add(buttons, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(file);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void add(JPanel form, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setForeground(Theme.MUTED);
        form.add(l);
        form.add(field);
    }

    /** Builds the flights from the form and files them; shows any problem in red. */
    public void submit() {
        try {
            int arr = parseTime(arrival.getText(), "arrival time");
            Aircraft a = Aircraft.of((String) aircraft.getSelectedItem());
            String no = number.getText().trim().toUpperCase();
            Flight in = Flight.arrival(no, required(airline.getText(), "airline"),
                    required(origin.getText(), "origin").toUpperCase(), a, arr, ((Number) fuel.getValue()).doubleValue());
            Flight out = null;
            if (returns.isSelected()) {
                int dep = parseTime(departure.getText(), "departure time");
                if (dep <= arr) {
                    throw new InvalidFlightPlanException("the departure must be after the arrival");
                }
                if (dep < arr + a.turnaroundMinutes()) {
                    throw new InvalidFlightPlanException("the " + a.getModel() + " needs " + a.turnaroundMinutes()
                            + " min on the ground, so it cannot leave before " + SimTime.format(arr + a.turnaroundMinutes()));
                }
                out = Flight.departure(depNumber.getText().trim().toUpperCase(), airline.getText().trim(),
                        required(destination.getText(), "destination").toUpperCase(), a, dep);
            }
            tower.fileFlightPlan(in, out);
            result = "Flight plan filed: " + no + " due " + SimTime.format(arr);
            dispose();
        } catch (InvalidFlightPlanException ex) {
            error.setText(ex.getMessage().replace("Invalid flight plan: ", "Problem: "));
            pack();
        }
    }

    private static int parseTime(String text, String what) throws InvalidFlightPlanException {
        String t = text.trim();
        if (!t.matches("\\d{1,2}:\\d{2}")) {
            throw new InvalidFlightPlanException(what + " must look like 07:45");
        }
        int h = Integer.parseInt(t.split(":")[0]);
        int m = Integer.parseInt(t.split(":")[1]);
        if (h > 23 || m > 59) {
            throw new InvalidFlightPlanException(what + " " + t + " is not a real time");
        }
        int minute = SimTime.parse(t);
        if (minute < 0) {
            throw new InvalidFlightPlanException(what + " is before the simulation starts (06:00)");
        }
        return minute;
    }

    private static String required(String text, String what) throws InvalidFlightPlanException {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidFlightPlanException(what + " is empty");
        }
        return text.trim();
    }

    /** The success message, or null if the user cancelled. */
    public String getResult() {
        return result;
    }

    // test hooks: let the tests fill in the form without clicking
    public void fillForTesting(String no, String model, String arrTime, int fuelMin, boolean ret, String depNo, String depTime) {
        number.setText(no);
        aircraft.setSelectedItem(model);
        arrival.setText(arrTime);
        fuel.setValue(fuelMin);
        returns.setSelected(ret);
        depNumber.setText(depNo);
        departure.setText(depTime);
    }

    public String errorText() {
        return error.getText();
    }
}
