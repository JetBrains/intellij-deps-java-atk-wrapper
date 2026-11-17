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

package org.GNOME.Accessibility;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The accessible hierarchy and the focus events of Swing menus.
 */
class MenuAccessibilityTest extends AtkTestBase {

    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenuItem openItem;
    private JMenu recentMenu;
    private JMenuItem recentItem;

    @BeforeEach
    void createMenus() throws Exception {
        onEdt(() -> {
            menuBar = new JMenuBar();
            fileMenu = new JMenu("File");
            openItem = new JMenuItem("Open");
            recentMenu = new JMenu("Open Recent");
            recentItem = new JMenuItem("project");
            menuBar.add(fileMenu);
            fileMenu.add(openItem);
            fileMenu.add(recentMenu);
            recentMenu.add(recentItem);
            return null;
        });
    }

    private static AccessibleContext accessibleParent(AccessibleContext context) throws ReflectiveOperationException {
        return (AccessibleContext) invokeStatic(AtkObject.class, "get_accessible_parent",
                new Class<?>[] {AccessibleContext.class}, context);
    }

    // IJPL-242397

    @Test
    void theParentOfAMenuItemIsItsMenu() throws Exception {
        AccessibleContext item = openItem.getAccessibleContext();

        assertSame(fileMenu.getAccessibleContext(), accessibleParent(item));
    }

    @Test
    void theParentOfASubmenuIsItsMenu() throws Exception {
        assertSame(fileMenu.getAccessibleContext(), accessibleParent(recentMenu.getAccessibleContext()));
        assertSame(recentMenu.getAccessibleContext(), accessibleParent(recentItem.getAccessibleContext()));
    }

    @Test
    void theIndexInParentMatchesTheChildrenOfTheMenu() throws Exception {
        onEdt(() -> {
            AccessibleContext menu = fileMenu.getAccessibleContext();
            for (JMenuItem item : new JMenuItem[] {openItem, recentMenu}) {
                int index = item.getAccessibleContext().getAccessibleIndexInParent();
                assertSame(item, menu.getAccessibleChild(index), "child " + index + " of the menu");
            }
            return null;
        });
    }

    @Test
    void theParentIsRegisteredWithTheNativeLayer() throws Exception {
        AccessibleContext parent = accessibleParent(openItem.getAccessibleContext());

        assertTrue(bridge.createdResources().contains(parent),
                "the native layer expects a native resource for every context returned by an upcall");
    }

    @Test
    void theParentOfAnItemInAStandalonePopupIsThePopup() throws Exception {
        JPopupMenu popup = onEdt(JPopupMenu::new);
        JMenuItem item = onEdt(() -> popup.add("Copy"));

        assertSame(popup.getAccessibleContext(), accessibleParent(item.getAccessibleContext()));
    }

    @Test
    void theParentOfAnItemInAPopupOfAButtonIsThePopup() throws Exception {
        JPopupMenu popup = onEdt(JPopupMenu::new);
        JMenuItem item = onEdt(() -> {
            popup.setInvoker(new JButton("More"));
            return popup.add("Copy");
        });

        assertSame(popup.getAccessibleContext(), accessibleParent(item.getAccessibleContext()));
    }
}
