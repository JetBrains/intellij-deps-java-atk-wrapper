/*
 * Java ATK Wrapper for GNOME
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, see <https://www.gnu.org/licenses/>.
 */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The Swing application of the AT-SPI integration tests in {@code tests/atspi/}. The tests start it with the
 * wrapper as its assistive technology and look at it through the AT-SPI bus. It knows nothing about the wrapper:
 * the JDK loads the wrapper from {@code javax.accessibility.assistive_technologies}, as in production.
 * <p>
 * The tests find the window by {@link #FRAME_TITLE} and the components by their names, so a change of a name here
 * needs the same change in {@code conftest.py}, {@code test_atspi.py} or {@code test_orca.py}.
 */
public final class AtspiTestApp {

    static final String FRAME_TITLE = "ATK wrapper test";

    private AtspiTestApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel();
            panel.add(new JButton("First"));

            // File -> Open, Recent -> Project: a menu with a submenu, for the keyboard navigation of test_orca.py.
            JMenu recentMenu = new JMenu("Recent");
            recentMenu.add(new JMenuItem("Project"));
            JMenu fileMenu = new JMenu("File");
            fileMenu.add(new JMenuItem("Open"));
            fileMenu.add(recentMenu);
            JMenuBar menuBar = new JMenuBar();
            menuBar.add(fileMenu);

            JFrame frame = new JFrame(FRAME_TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setJMenuBar(menuBar);
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
