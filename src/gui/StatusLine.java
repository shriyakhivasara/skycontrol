package gui;

import exceptions.SkyControlException;
import javax.swing.JLabel;

/** A one-line message area that shows the result of the user's last action in green or red. */
public class StatusLine extends JLabel {
    public StatusLine() {
        super(" ");
        setFont(Theme.SMALL);
        setForeground(Theme.MUTED);
    }

    /** Runs the action and shows its success message, or the exception's message in red. */
    public boolean attempt(TowerAction action) {
        try {
            ok(action.run());
            return true;
        } catch (SkyControlException e) {
            error(e.getMessage());
            return false;
        }
    }

    public void ok(String msg) {
        setText(msg);
        setForeground(Theme.GREEN);
    }

    public void error(String msg) {
        setText(msg);
        setForeground(Theme.RED);
    }
}
