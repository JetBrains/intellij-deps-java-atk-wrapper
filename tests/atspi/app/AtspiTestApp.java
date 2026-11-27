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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
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

    /**
     * "A", an emoji outside the BMP (U+1F600) and "B". The emoji is one character for ATK and two {@code char}s
     * of a Java {@code String}, so every Swing index after it differs from the ATK offset.
     */
    static final String UNICODE_TEXT = "A\uD83D\uDE00B";
    static final String UNICODE_TEXT_FIELD_NAME = "Unicode text";

    /** The argument that makes the application run {@link #exitAfterDialog()} and not show the test window. */
    static final String EXIT_AFTER_DIALOG = "exit-after-dialog";
    static final String EXIT_TEST_DIALOG_TITLE = "Exit test dialog";
    /** Printed before the exit when the garbage collector released the dialog. */
    static final String DIALOG_RELEASED_MESSAGE = "AtspiTestApp: the garbage collector released the dialog";

    private AtspiTestApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals(EXIT_AFTER_DIALOG)) {
            exitAfterDialog();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel();
            panel.add(new JButton("First"));

            // A text with a character outside the BMP, for the text offsets of test_atspi.py.
            JTextField unicodeField = new JTextField(UNICODE_TEXT, 10);
            unicodeField.getAccessibleContext().setAccessibleName(UNICODE_TEXT_FIELD_NAME);
            panel.add(unicodeField);

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

    /**
     * Opens a dialog of a window, closes it when the test closes the standard input, and exits when the garbage
     * collector released the dialog. The exit code is 0.
     * <p>
     * This is an application that exits soon after a dialog, as an IDE does after the confirmation of the exit.
     * A shutdown hook takes a second, as the hooks of an IDE take time: the wrapper cleans up on its own thread
     * after the shutdown started, and the JVM must still be alive then.
     */
    private static void exitAfterDialog() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                // The JVM exits anyway.
            }
        }));

        JDialog[] dialog = new JDialog[1];
        List<WeakReference<AccessibleContext>> dialogContext = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            // A window with a dialog of its own, as in an IDE. The window stays open until the exit.
            JFrame owner = new JFrame("Exit test window");
            owner.add(new JButton("Owner"));
            owner.pack();
            owner.setVisible(true);

            dialog[0] = new JDialog(owner, EXIT_TEST_DIALOG_TITLE);
            dialog[0].add(new JButton("Close"));
            dialog[0].pack();
            dialog[0].setVisible(true);
            dialogContext.add(new WeakReference<>(dialog[0].getAccessibleContext()));
        });

        // The test closes the standard input when the event of the open dialog reached it.
        while (System.in.read() != -1) {
            // Skip what the test wrote.
        }

        SwingUtilities.invokeAndWait(() -> dialog[0].dispose());
        dialog[0] = null;
        for (int attempt = 0; attempt < 50 && dialogContext.get(0).get() != null; attempt++) {
            System.gc();
            Thread.sleep(100);
        }
        if (dialogContext.get(0).get() == null) {
            // The wrapper releases the native object of a collected context on its disposer and GLib threads.
            Thread.sleep(500);
            System.out.println(DIALOG_RELEASED_MESSAGE);
        }
        System.exit(0);
    }
}
