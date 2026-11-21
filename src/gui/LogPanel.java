package gui;

import engine.LogEntry;
import engine.TowerSnapshot;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** The control tower log: newest message at the bottom. */
public class LogPanel extends JPanel implements Refreshable {
    private static final int SHOW = 150;
    private final JTextArea text = new JTextArea();
    private LogEntry lastShown;

    public LogPanel() {
        super(new BorderLayout());
        Theme.dark(this);
        setBorder(Theme.titled("Tower log"));
        text.setEditable(false);
        text.setFont(Theme.MONO);
        text.setBackground(Theme.BG);
        text.setForeground(Theme.TEXT);
        text.setLineWrap(false);
        JScrollPane sp = new JScrollPane(text);
        sp.setBorder(null);
        add(sp, BorderLayout.CENTER);
    }

    @Override
    public void refresh(TowerSnapshot s) {
        List<LogEntry> log = s.log();
        if (log.isEmpty() || log.get(log.size() - 1) == lastShown) {
            return;
        }
        lastShown = log.get(log.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, log.size() - SHOW); i < log.size(); i++) {
            sb.append(log.get(i)).append('\n');
        }
        text.setText(sb.toString());
        text.setCaretPosition(text.getDocument().getLength());
    }
}
