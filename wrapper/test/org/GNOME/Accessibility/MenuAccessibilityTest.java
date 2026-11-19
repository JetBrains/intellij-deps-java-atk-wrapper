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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.event.ChangeListener;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void clearMenuSelection() throws Exception {
        onEdt(() -> {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            return null;
        });
    }

    private static AccessibleContext accessibleParent(AccessibleContext context) throws ReflectiveOperationException {
        return (AccessibleContext) invokeStatic(AtkObject.class, "get_accessible_parent",
                new Class<?>[] {AccessibleContext.class}, context);
    }

    // IJPL-242397

    /**
     * Checks: the {@code get_accessible_parent} upcall returns the owning {@code JMenu} for an item in its popup,
     * rather than the {@code JPopupMenu}.
     * <p>
     * Why (Swing, Orca): Swing initially reports the {@code JPopupMenu} as the item's parent.
     * Calling {@code AccessibleJMenu.getAccessibleChild} changes the parent to the {@code JMenu} as a side effect.
     * Orca announces newly encountered ancestors when focus changes. Without the menu in the ancestor chain,
     * Orca omits the menu name.
     * <p>
     * May change when: Swing assigns the menu as the parent when the item is added (the workaround becomes
     * unnecessary, but the test should still pass), or Orca changes how it derives focus announcements
     * (the original motivation may no longer apply).
     */
    @Test
    void theParentOfAMenuItemIsItsMenu() throws Exception {
        AccessibleContext item = openItem.getAccessibleContext();

        assertSame(fileMenu.getAccessibleContext(), accessibleParent(item));
    }

    /**
     * Checks: the parent is replaced at every level: a submenu reports the menu that contains it, and an item of
     * the submenu reports the submenu. No {@code JPopupMenu} is left in the ancestor chain.
     * <p>
     * Why (Swing): a {@code JMenu} is itself a component of the popup of its parent menu, so Swing reports the
     * popup as its parent just as for a plain item, see {@link #theParentOfAMenuItemIsItsMenu}. The replacement
     * must hold for a {@code JMenu} as well, or the chain breaks one level higher.
     */
    @Test
    void theParentOfASubmenuIsItsMenu() throws Exception {
        assertSame(fileMenu.getAccessibleContext(), accessibleParent(recentMenu.getAccessibleContext()));
        assertSame(recentMenu.getAccessibleContext(), accessibleParent(recentItem.getAccessibleContext()));
    }

    /**
     * Checks: the index that Swing reports for a component of a menu popup is its position among the children of
     * the {@code JMenu}. No wrapper code runs: the test pins a Swing behavior the wrapper relies on.
     * <p>
     * Why (Swing, wrapper): the wrapper replaces the parent of a popup component with the menu but passes the
     * index in the parent through unchanged. Swing computes the index against the parent it reports itself, which
     * is the popup at first. The two fit only because {@code AccessibleJMenu} enumerates the components of its
     * popup, so both parents list the same children in the same order. Otherwise the parent and the index that
     * reach ATK would not match.
     * <p>
     * May change when: the JDK makes the two enumerations differ. This test then fails, and the wrapper has to
     * compute the index against the menu. The test covers items and submenus, not separators.
     */
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

    /**
     * Checks: the wrapper creates a native resource for the menu that it returns as the parent.
     * <p>
     * Why (wrapper): the native layer looks up the ATK object of a context returned by an upcall and finds none
     * unless the Java layer has registered the context with {@code AtkWrapperDisposer}. The replaced
     * {@code JMenu} is not the parent that Swing reports, so the upcall cannot assume it was registered before.
     */
    @Test
    void theParentIsRegisteredWithTheNativeLayer() throws Exception {
        AccessibleContext parent = accessibleParent(openItem.getAccessibleContext());

        assertTrue(bridge.createdResources().contains(parent),
                "the native layer expects a native resource for every context returned by an upcall");
    }

    /**
     * Checks: an item of a popup menu without an invoker keeps the {@code JPopupMenu} as its parent.
     * <p>
     * Why (wrapper): the replacement of {@link #theParentOfAMenuItemIsItsMenu} applies only when the invoker of
     * the popup is a {@code JMenu}. Here no menu lists the item as its child, and the popup is the real parent.
     */
    @Test
    void theParentOfAnItemInAStandalonePopupIsThePopup() throws Exception {
        JPopupMenu popup = onEdt(JPopupMenu::new);
        JMenuItem item = onEdt(() -> popup.add("Copy"));

        assertSame(popup.getAccessibleContext(), accessibleParent(item.getAccessibleContext()));
    }

    /**
     * Checks: an item of a popup menu whose invoker is not a {@code JMenu}, here a {@code JButton}, keeps the
     * {@code JPopupMenu} as its parent.
     * <p>
     * Why (Swing, wrapper): an invoker alone is not a reason to replace the parent. A {@code JMenu} lists the
     * components of its popup as its accessible children; a button does not, so the button as the parent would
     * give the item a parent that does not have it as a child.
     */
    @Test
    void theParentOfAnItemInAPopupOfAButtonIsThePopup() throws Exception {
        JPopupMenu popup = onEdt(JPopupMenu::new);
        JMenuItem item = onEdt(() -> {
            popup.setInvoker(new JButton("More"));
            return popup.add("Copy");
        });

        assertSame(popup.getAccessibleContext(), accessibleParent(item.getAccessibleContext()));
    }

    // IJPL-253929

    /**
     * Checks: when the selected menu path shrinks because a submenu closes, the wrapper reports the focus once,
     * for the {@code JMenu} of the closed submenu. When the path grows, the listener reports nothing.
     * <p>
     * Why (Swing, Orca, test setup): Swing fires the accessible focus event of a {@code JMenu} when the menu
     * becomes selected, in {@code AccessibleJMenuItem.stateChanged}. The menu stays in the selection path, and
     * so selected, while its submenu is open; closing the submenu changes no state and fires nothing. Orca then
     * stays focused on an item of the closed submenu, which IJPL-253929 fixed for Orca 49 and later. The wrapper
     * listens to {@code MenuSelectionManager} and reports the focus itself. The menu contexts have no property
     * change listener in this test, so the bridge records only what the listener sends, not the events of Swing.
     * <p>
     * May change when: the JDK starts to fire a focus event for the menu when its submenu closes. The listener
     * becomes unnecessary then; the test should still pass, because {@code dispatchFocusEvent} drops a repeated
     * focus on the same context.
     */
    @Test
    void closingASubmenuFocusesItsMenu() throws Exception {
        ChangeListener listener = menuSelectionListener();
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        onEdt(() -> {
            manager.addChangeListener(listener);
            try {
                manager.setSelectedPath(new MenuElement[] {
                        menuBar, fileMenu, fileMenu.getPopupMenu(), recentMenu, recentMenu.getPopupMenu(), recentItem});
                assertTrue(bridge.calls("focusNotify").isEmpty(), "Swing reports the growth of the path itself");

                manager.setSelectedPath(new MenuElement[] {menuBar, fileMenu, fileMenu.getPopupMenu(), recentMenu});
            } finally {
                manager.removeChangeListener(listener);
            }
            return null;
        });

        List<RecordingNativeBridge.Call> focus = bridge.calls("focusNotify");
        assertEquals(1, focus.size(), () -> "focus events: " + focus);
        assertSame(recentMenu.getAccessibleContext(), focus.get(0).context());
    }

    /**
     * Checks: when the selection moves between two entries of the same menu, so that the selected path keeps its
     * length, the listener reports no focus.
     * <p>
     * Why (Swing, wrapper, test setup): Swing reports this move itself: the newly selected entry becomes armed,
     * or selected for a {@code JMenu}, and fires its accessible focus event. The listener exists only for the
     * case Swing leaves out, see {@link #closingASubmenuFocusesItsMenu}, and must not become a second source of
     * focus events. The menu contexts have no property change listener in this test, so the bridge would record
     * only a call of the listener.
     */
    @Test
    void movingWithinOneMenuDoesNotFocus() throws Exception {
        ChangeListener listener = menuSelectionListener();
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        onEdt(() -> {
            manager.addChangeListener(listener);
            try {
                manager.setSelectedPath(new MenuElement[] {menuBar, fileMenu, fileMenu.getPopupMenu(), openItem});
                manager.setSelectedPath(new MenuElement[] {menuBar, fileMenu, fileMenu.getPopupMenu(), recentMenu});
            } finally {
                manager.removeChangeListener(listener);
            }
            return null;
        });

        assertTrue(bridge.calls("focusNotify").isEmpty(), () -> "focus events: " + bridge.calls("focusNotify"));
    }

    /**
     * The listener is an instance field. The constructor of {@link AtkWrapper} does not register it while
     * accessibility is disabled, the test registers it itself.
     */
    private static ChangeListener menuSelectionListener() throws Exception {
        return onEdt(AtkWrapper::new).menuSelectionListener;
    }
}
