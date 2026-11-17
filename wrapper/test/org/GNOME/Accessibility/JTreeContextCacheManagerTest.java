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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.accessibility.AccessibleContext;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IJPL-242394: the node contexts of a {@link JTree} must stay the same objects while the tree does not change.
 * {@code AccessibleJTree} creates a new {@code AccessibleJTreeNode} on every request, so without the cache the
 * native layer gets a new ATK object for the same node.
 */
class JTreeContextCacheManagerTest extends AtkTestBase {

    private JTree tree;
    private AccessibleContext treeContext;
    private TreePath pathA;
    private TreePath pathA1;
    private TreePath pathB;

    @BeforeEach
    void createTree() throws Exception {
        onEdt(() -> {
            DefaultMutableTreeNode root = createTreeModelRoot();
            DefaultMutableTreeNode a = (DefaultMutableTreeNode) root.getChildAt(0);
            tree = new JTree(new DefaultTreeModel(root));
            tree.setRootVisible(false);
            treeContext = tree.getAccessibleContext();
            pathA = new TreePath(new Object[] {root, a});
            pathA1 = pathA.pathByAddingChild(a.getChildAt(0));
            pathB = new TreePath(new Object[] {root, root.getChildAt(1)});
            return null;
        });
    }

    private AccessibleContext select(TreePath path) throws Exception {
        return onEdt(() -> {
            tree.setSelectionPath(path);
            return JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext);
        });
    }

    private static String name(AccessibleContext context) throws Exception {
        return onEdt(context::getAccessibleName);
    }

    @Test
    void precondition_swingCreatesANewNodeContextOnEveryRequest() throws Exception {
        onEdt(() -> {
            assertNotSame(treeContext.getAccessibleChild(0).getAccessibleContext(),
                    treeContext.getAccessibleChild(0).getAccessibleContext());
            return null;
        });
    }

    @Test
    void resolvesTheSelectedNode() throws Exception {
        assertEquals("a1", name(select(pathA1)));
        assertEquals("b", name(select(pathB)));
    }

    @Test
    void returnsTheSameContextForTheSamePath() throws Exception {
        AccessibleContext first = select(pathA1);
        select(pathB);
        AccessibleContext second = select(pathA1);

        assertSame(first, second);
    }

    @Test
    void sharesTheParentContextBetweenSiblings() throws Exception {
        AccessibleContext a1 = select(pathA1);
        AccessibleContext a2 = select(pathA.pathByAddingChild(((DefaultMutableTreeNode) pathA.getLastPathComponent()).getChildAt(1)));
        AccessibleContext a = select(pathA);

        onEdt(() -> {
            assertSame(a, a1.getAccessibleParent());
            assertSame(a, a2.getAccessibleParent());
            return null;
        });
    }

    @Test
    void resolvesTheRootWhenItIsVisible() throws Exception {
        onEdt(() -> {
            tree.setRootVisible(true);
            return null;
        });
        AccessibleContext root = select(pathA.getParentPath());
        AccessibleContext a = select(pathA);

        assertEquals("root", name(root));
        onEdt(() -> {
            assertSame(root, a.getAccessibleParent());
            return null;
        });
    }

    @Test
    void invalidatesTheCacheWhenTheModelChanges() throws Exception {
        AccessibleContext before = select(pathA1);

        DefaultMutableTreeNode newRoot = createTreeModelRoot();
        DefaultMutableTreeNode newA = (DefaultMutableTreeNode) newRoot.getChildAt(0);
        TreePath newPathA1 = new TreePath(new Object[] {newRoot, newA, newA.getChildAt(0)});
        AccessibleContext after = onEdt(() -> {
            tree.setModel(new DefaultTreeModel(newRoot));
            tree.setSelectionPath(newPathA1);
            return JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext);
        });

        assertNotSame(before, after);
        assertEquals("a1", name(after));
    }

    @Test
    void invalidatesTheCacheWhenTheRootVisibilityChanges() throws Exception {
        AccessibleContext hiddenRoot = select(pathA1);
        assertEquals("a1", name(hiddenRoot));
        onEdt(() -> {
            tree.setRootVisible(true);
            return null;
        });
        AccessibleContext visibleRoot = select(pathA1);

        assertNotSame(hiddenRoot, visibleRoot);
        assertEquals("a1", name(visibleRoot));
    }

    @Test
    void returnsNullWithoutSelection() throws Exception {
        assertNull(onEdt(() -> JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext)));
    }

    @Test
    void resolvesTheTreeThroughItsContextAfterItWasRemembered() throws Exception {
        onEdt(() -> {
            tree.setSelectionPath(pathB);
            assertNull(JTreeContextCacheManager.resolveActiveDescendant(treeContext), "the tree is not known yet");
            JTreeContextCacheManager.rememberJTree(tree, treeContext);
            AccessibleContext b = JTreeContextCacheManager.resolveActiveDescendant(treeContext);
            assertSame(JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext), b);
            return null;
        });
    }

    @Test
    void registersTheResolvedContextsWithTheNativeLayer() throws Exception {
        AccessibleContext a1 = select(pathA1);

        onEdt(() -> {
            assertTrue(bridge.createdResources().contains(a1));
            assertTrue(bridge.createdResources().contains(a1.getAccessibleParent().getAccessibleContext()));
            return null;
        });
    }
}
