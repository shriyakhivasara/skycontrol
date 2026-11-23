package gui;

import exceptions.SkyControlException;

/**
 * One thing the user asks the tower to do. Lets every button share the same
 * "try it, show the result or the exception message" code.
 */
@FunctionalInterface
public interface TowerAction {
    /** @return a short success message */
    String run() throws SkyControlException;
}
