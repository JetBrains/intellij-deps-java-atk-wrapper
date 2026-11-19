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

    /**
     * Checks: asking the tree for the same child twice returns two different node contexts. No wrapper code runs:
     * the test pins the Swing behavior that makes the cache necessary.
     * <p>
     * Why (Swing): {@code AccessibleJTree.getAccessibleChild} creates a new {@code AccessibleJTreeNode} on every
     * call. The native layer keeps one ATK object per {@code AccessibleContext}, so without the cache the same
     * node reaches an assistive technology as a new object each time.
     * <p>
     * May change when: the JDK starts to return the same node object. This test then fails, which is the signal
     * that the reason for {@link JTreeContextCacheManager} is gone and the cache can be simplified or removed.
     */
    @Test
    void precondition_swingCreatesANewNodeContextOnEveryRequest() throws Exception {
        onEdt(() -> {
            assertNotSame(treeContext.getAccessibleChild(0).getAccessibleContext(),
                    treeContext.getAccessibleChild(0).getAccessibleContext());
            return null;
        });
    }

    /**
     * Checks: the active descendant of a tree is the context of its selected node, at any depth.
     * <p>
     * Why (wrapper): the wrapper takes the node from the selection of the tree and walks to it from the tree
     * context. It does not use the node that Swing puts into the active-descendant event, which is a new object
     * for every event.
     */
    @Test
    void resolvesTheSelectedNode() throws Exception {
        assertEquals("a1", name(select(pathA1)));
        assertEquals("b", name(select(pathB)));
    }

    /**
     * Checks: a node keeps its context: selecting another node and coming back returns the same object.
     * <p>
     * Why (wrapper, Orca): the native layer finds the ATK object of a node by its {@code AccessibleContext}, so
     * a new context is a new object for an assistive technology. Before the cache, Orca announced the whole path
     * from the root again on every move (IJPL-242394).
     */
    @Test
    void returnsTheSameContextForTheSamePath() throws Exception {
        AccessibleContext first = select(pathA1);
        select(pathB);
        AccessibleContext second = select(pathA1);

        assertSame(first, second);
    }

    /**
     * Checks: two sibling nodes report the same parent object, and it is the context that the cache returns for
     * the parent node itself.
     * <p>
     * Why (Swing, Orca): an {@code AccessibleJTreeNode} that was not created by its parent builds a new parent
     * node when it is asked for one. The wrapper therefore walks down from the tree with
     * {@code getAccessibleChild}, which links each node to the cached context of its parent. With a new parent
     * object for every node, Orca announced the whole path from the root again on every move (IJPL-242394).
     * <p>
     * May change when: Orca stops announcing the path again when the parent of the same node is a new object.
     * The motivation for the shared parent is then gone, but the expectation is still a valid contract.
     */
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

    /**
     * Checks: with a visible root, the root node itself can be the active descendant, and it is the parent of the
     * first-level nodes.
     * <p>
     * Why (Swing): the accessible hierarchy of a tree depends on {@code isRootVisible}. With a visible root the
     * only child of the tree is the root node; with a hidden root the children of the root hang on the tree
     * directly. The wrapper skips the root in its walk when it is hidden, and this test covers the other branch.
     */
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

    /**
     * Checks: after the model of the tree is replaced, a node with the same name at the same place gets a new
     * context, which describes the node of the new model. The test pins this result, not the clearing of the
     * cache: the new model consists of new node objects, so its paths are not equal to the cached ones and the
     * lookup misses even without the invalidation.
     * <p>
     * Why (wrapper): a cached context holds a node of the old model and must not be reported for the new one.
     * The cache drops all contexts when the tree gets another model object.
     */
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

    /**
     * Checks: after the visibility of the root changes, the same path gets a new context.
     * <p>
     * Why (Swing, wrapper): the root visibility changes the accessible hierarchy: the first-level nodes hang on
     * the tree while the root is hidden and on the root node while it is visible. A context cached before the
     * change keeps the old parent chain, so the cache drops all contexts when {@code isRootVisible} changes.
     */
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

    /**
     * Checks: a tree without a selection has no active descendant: the result is {@code null}, not a default
     * node.
     * <p>
     * Why (wrapper): {@code null} makes the wrapper skip the active-descendant signal when such a tree gets the
     * focus. The tree still gets its focus notification, and no node that the user did not select is reported.
     */
    @Test
    void returnsNullWithoutSelection() throws Exception {
        assertNull(onEdt(() -> JTreeContextCacheManager.resolveActiveDescendant(tree, treeContext)));
    }

    /**
     * Checks: the active descendant can be resolved from the context of the tree alone, but only after the
     * wrapper has seen the tree; before that the result is {@code null}. Both ways return the same cached context.
     * <p>
     * Why (Swing, wrapper): Swing fires the active-descendant change with the {@code AccessibleContext} of the
     * tree as the event source, while the wrapper needs the {@code JTree} for its selection path. The wrapper
     * keeps a registry from the context to the tree and fills it when the tree gets the focus. For a tree it has
     * not seen, {@code null} makes the property change listener fall back to the node from the Swing event.
     */
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

    /**
     * Checks: the wrapper creates a native resource for the resolved node and for its parent node.
     * <p>
     * Why (wrapper): the native layer looks up the ATK object of a context that it receives in a signal and finds
     * none unless the Java layer has registered the context with {@code AtkWrapperDisposer} before. The cache
     * registers every context on the path it walks, so the parent is covered as well.
     */
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
