package gui;

import engine.TowerSnapshot;

/** Every live panel implements this; the main window calls refresh() a few times a second. */
public interface Refreshable {
    void refresh(TowerSnapshot snapshot);
}
