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
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The ATK signals that the wrapper emits for the events of a {@link JTree}.
 */
class JTreeSignalsTest extends AtkTestBase {

    private JTree tree;
    private AccessibleContext treeContext;
    private TreePath pathA;
    private TreePath pathA1;
    private TreePath pathA2;

    @BeforeEach
    void createTree() throws Exception {
        onEdt(() -> {
            DefaultMutableTreeNode root = createTreeModelRoot();
            DefaultMutableTreeNode a = (DefaultMutableTreeNode) root.getChildAt(0);
            tree = new JTree(new DefaultTreeModel(root));
            tree.setRootVisible(false);
            pathA = new TreePath(new Object[] {root, a});
            pathA1 = pathA.pathByAddingChild(a.getChildAt(0));
            pathA2 = pathA.pathByAddingChild(a.getChildAt(1));
            treeContext = tree.getAccessibleContext();
            // What the native layer does for every object it exposes, see AtkWrapper.register_property_change_listener.
            treeContext.addPropertyChangeListener(AtkWrapper.propertyChangeListener);
            return null;
        });
    }

    @AfterEach
    void removeListener() throws Exception {
        onEdt(() -> {
            treeContext.removePropertyChangeListener(AtkWrapper.propertyChangeListener);
            return null;
        });
    }

    private AccessibleContext cachedContext(TreePath path) throws Exception {
        return onEdt(() -> {
            TreePath selection = tree.getSelectionPath();
            tree.setSelectionPath(path);
            AccessibleContext context = JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext);
            tree.setSelectionPath(selection);
            return context;
        });
    }

    // IJPL-242394

    @Test
    void focusingTheTreeEmitsFocusAndTheSelectedNodeAsActiveDescendant() throws Exception {
        onEdt(() -> {
            tree.expandPath(pathA);
            tree.setSelectionPath(pathA1);
            return null;
        });
        bridge.clear();

        onEdt(() -> {
            AtkWrapper.dispatchFocusEvent(tree);
            return null;
        });

        List<RecordingNativeBridge.Call> calls = bridge.calls();
        assertEquals(2, calls.size(), () -> "calls: " + calls);
        assertEquals("focusNotify", calls.get(0).method());
        assertSame(treeContext, calls.get(0).context());

        List<RecordingNativeBridge.Call> activeDescendant = bridge.signals(AtkSignal.OBJECT_ACTIVE_DESCENDANT_CHANGED);
        assertEquals(1, activeDescendant.size());
        assertSame(treeContext, activeDescendant.get(0).context());
        assertSame(cachedContext(pathA1), activeDescendantOf(activeDescendant.get(0)));
    }

    @Test
    void navigationEmitsStableNodeContexts() throws Exception {
        onEdt(() -> {
            tree.expandPath(pathA);
            AtkWrapper.dispatchFocusEvent(tree);
            return null;
        });
        bridge.clear();

        onEdt(() -> {
            tree.setSelectionPath(pathA1);
            tree.setSelectionPath(pathA2);
            tree.setSelectionPath(pathA1);
            return null;
        });

        List<RecordingNativeBridge.Call> signals = bridge.signals(AtkSignal.OBJECT_ACTIVE_DESCENDANT_CHANGED);
        assertEquals(3, signals.size(), () -> "active-descendant-changed signals: " + signals);
        AccessibleContext a1 = activeDescendantOf(signals.get(0));
        AccessibleContext a2 = activeDescendantOf(signals.get(1));
        assertSame(a1, activeDescendantOf(signals.get(2)), "the same node must be the same ATK object");
        onEdt(() -> {
            assertEquals("a1", a1.getAccessibleName());
            assertEquals("a2", a2.getAccessibleName());
            // Orca compares the ancestors of the old and the new locus of focus. A new parent object makes it
            // announce the whole path from the root again.
            assertSame(a1.getAccessibleParent(), a2.getAccessibleParent());
            return null;
        });
    }

    // Upstream fix 796cbec "Fix: Orca doesn't announce root in JTree"

    @Test
    void selectionChangeOfTheTreeIsNotEmitted() throws Exception {
        onEdt(() -> {
            AtkWrapper.dispatchFocusEvent(tree);
            tree.setSelectionPath(pathA);
            return null;
        });

        assertTrue(bridge.signals(AtkSignal.OBJECT_SELECTION_CHANGED).isEmpty(),
                "see https://gitlab.gnome.org/GNOME/orca/-/issues/552");
    }

    private static AccessibleContext activeDescendantOf(RecordingNativeBridge.Call emitSignal) {
        List<?> signalArgs = (List<?>) emitSignal.args().get(1);
        return (AccessibleContext) signalArgs.get(0);
    }
}
