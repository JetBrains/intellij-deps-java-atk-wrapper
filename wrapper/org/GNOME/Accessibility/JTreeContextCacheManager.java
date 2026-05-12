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

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Handles accessibility contexts ({@link AccessibleContext}) specifically for the {@link JTree} component.
 * <p>
 * <b>The Problem:</b><br>
 * In the Swing architecture, {@code AccessibleJTreeNode} objects are generated "on the fly" each time a node is accessed.
 * When a screen reader requests the parent of the current node, the ATK core
 * (via the {@code jaw_impl_find_instance} function) attempts to find its existing instance in memory.
 * <p>
 * If the Java Garbage Collector (GC) has already removed the parent's {@link AccessibleContext},
 * the function cannot return the same instance, and Swing creates a completely new object for the node.
 * Because of this, the object change at the native (C/C++) level.
 * As a result, Orca loses the hierarchy context (assuming these are completely different sibling elements)
 * and incorrectly reads out the entire path from the root down to the current node, severely degrading the user experience.
 * <p>
 * <b>The Solution:</b><br>
 * This class implements and maintains a caching mechanism (see {@link JTreeContextCache}) that artificially
 * keeps stable references to the {@link AccessibleContext} of recently selected nodes and their parents in memory.
 * This ensures that the ATK bridge works with consistent object identifiers as long as the user interacts with the tree.
 */
final class JTreeContextCacheManager {

    /**
     * The maximum number of node contexts we retain in memory for a single tree.
     * Used to limit the LRU cache size to prevent excessive memory consumption.
     */
    private static final int MAX_RETAINED_TREE_CONTEXTS = 512;

    /**
     * A reverse-lookup registry mapping a tree's abstract {@link AccessibleContext}
     * back to its original {@link JTree} object.
     * <p>
     * Must be accessed on EDT.
     * <p>
     * <b>Why this is needed:</b><br>
     * Navigation events ({@code active-descendant-changed}) are fired from the context of the tree itself.
     * To find out exactly which child node is currently selected and send the correct signal to the screen reader,
     * we need the underlying {@code JTree} component and its {@code SelectionPath}.
     * Using a {@link WeakHashMap} ensures the registry does not prevent closed trees from being garbage collected.
     */
    private static final Map<AccessibleContext, WeakReference<JTree>> treesByAccessibleContext = new WeakHashMap<>();

    /**
     * A unique key used to bind the node cache ({@link JTreeContextCache}) directly to the {@link JTree}
     * instance via the {@link javax.swing.JComponent#putClientProperty} mechanism.
     * <p>
     * This guarantees that the cache is safely destroyed by the Garbage Collector exactly
     * when the tree itself is removed, preventing memory leaks.
     */
    private static final Object TREE_CONTEXT_CACHE_KEY = new Object();

    private JTreeContextCacheManager() {
    }

    /**
     * Remembers the association between a JTree component and its accessibility context
     * in the global weak reference registry.
     */
    static void rememberJTree(JTree tree, AccessibleContext treeAccContext) {
        if (tree == null || treeAccContext == null) {
            return;
        }
        treesByAccessibleContext.put(treeAccContext, new WeakReference<JTree>(tree));
    }

    /**
     * Resolves the accessible context for the currently selected (active) tree node.
     * This method looks up the underlying {@link JTree} object in the global weak reference registry.
     *
     * @param treeAccContext the accessibility context of the tree itself (not a node).
     * @return the accessible context of the active node, or {@code null} if the context is null,
     * the tree has been garbage collected, or there is no selection.
     */
    static AccessibleContext resolveActiveDescendant(AccessibleContext treeAccContext) {
        if (treeAccContext == null) {
            return null;
        }
        WeakReference<JTree> treeRef = treesByAccessibleContext.get(treeAccContext);
        JTree tree = treeRef == null ? null : treeRef.get();
        return resolveActiveDescendant(tree, treeAccContext);
    }

    /**
     * Resolves the accessible context for the currently selected node in the specified tree.
     * It populates the cache to ensure stable identifiers for ATK.
     *
     * @param tree           the tree component.
     * @param treeAccContext the accessibility context of the tree.
     * @return the accessible context of the active node, or {@code null}.
     */
    static AccessibleContext resolveActiveDescendant(JTree tree, AccessibleContext treeAccContext) {
        if (tree == null || treeAccContext == null) {
            return null;
        }

        rememberJTree(tree, treeAccContext);

        TreePath selectionPath = tree.getLeadSelectionPath();
        if (selectionPath == null) {
            selectionPath = tree.getSelectionPath();
        }
        if (selectionPath == null) {
            return null;
        }

        return getAccessibleContextForPath(tree, treeAccContext, selectionPath);
    }

    /**
     * Iterates through the given path (from the root down to the selected node), extracts the
     * {@link AccessibleContext} for each element, and saves them in the tree's cache.
     *
     * @param tree           the target tree.
     * @param treeAccContext the accessibility context of the tree itself.
     * @param selectionPath  the path to the selected node.
     * @return the {@link AccessibleContext} of the target selected node.
     */
    private static AccessibleContext getAccessibleContextForPath(
            JTree tree,
            AccessibleContext treeAccContext,
            TreePath selectionPath) {
        Object[] nodes = selectionPath.getPath();
        if (nodes.length == 0) {
            return null;
        }

        JTreeContextCache cache = getTreeContextCache(tree);
        AccessibleContext cachedContext = cache.get(selectionPath);
        if (cachedContext != null) {
            AtkWrapperDisposer.getInstance().addRecord(cachedContext);
            return cachedContext;
        }

        TreeModel model = tree.getModel();
        if (model == null) {
            return null;
        }

        AccessibleContext current = treeAccContext;
        TreePath prefixPath = new TreePath(nodes[0]);
        int start = tree.isRootVisible() ? 0 : 1;

        for (int pathIndex = start; pathIndex < nodes.length; pathIndex++) {
            if (pathIndex > 0) {
                prefixPath = prefixPath.pathByAddingChild(nodes[pathIndex]);
            }

            AccessibleContext cachedPrefix = cache.get(prefixPath);
            if (cachedPrefix != null) {
                current = cachedPrefix;
                AtkWrapperDisposer.getInstance().addRecord(current);
                continue;
            }

            int childIndex = pathIndex == 0
                    ? 0
                    : model.getIndexOfChild(nodes[pathIndex - 1], nodes[pathIndex]);
            if (childIndex < 0) {
                return null;
            }

            Accessible child = current.getAccessibleChild(childIndex);
            if (child == null) {
                return null;
            }

            AccessibleContext childContext = child.getAccessibleContext();
            if (childContext == null) {
                return null;
            }

            current = childContext;
            cache.put(prefixPath, current);
            AtkWrapperDisposer.getInstance().addRecord(current);
        }

        return current;
    }

    /**
     * Returns the LRU cache structure ({@link JTreeContextCache}) bound to a specific tree.
     * If the cache is not yet created, it initializes it and binds it to the tree's client properties.
     * <p>
     * Before returning, it strictly calls {@code validate()}, which clears the cache if
     * the tree's structure (model) has changed.
     *
     * @param tree the target tree.
     * @return the up-to-date valid cache for the given tree.
     */
    private static JTreeContextCache getTreeContextCache(JTree tree) {
        Object value = tree.getClientProperty(TREE_CONTEXT_CACHE_KEY);
        JTreeContextCache cache;
        if (value instanceof JTreeContextCache) {
            cache = (JTreeContextCache) value;
        } else {
            cache = new JTreeContextCache(tree);
            tree.putClientProperty(TREE_CONTEXT_CACHE_KEY, cache);
        }
        cache.validate(tree);
        return cache;
    }

    /**
     * A local cache for each {@link JTree} instance. Retains the recently selected
     * paths ({@code TreePath -> AccessibleContext}) and prevents them from being released.
     * <p>
     * <b>Why this is needed:</b><br>
     * In a normal scenario, node context objects are quickly collected by the GC once direct
     * references to them are lost. We extend their lifecycle (up to the
     * {@link #MAX_RETAINED_TREE_CONTEXTS} limit) while the user actively interacts with them.
     * This solves the issue where ATK requests a node's parent, but it has already been garbage collected.
     */
    private static final class JTreeContextCache {
        private final LinkedHashMap<TreePath, AccessibleContext> contexts =
                new LinkedHashMap<TreePath, AccessibleContext>(16, 0.75f, true);
        private TreeModel model;
        private boolean rootVisible;

        JTreeContextCache(JTree tree) {
            model = tree.getModel();
            rootVisible = tree.isRootVisible();
        }

        /**
         * Validates the cached data against the current state of the tree.
         * <p>
         * If the tree's data model has changed OR the root node
         * visibility has changed, the old {@code TreePath} keys
         * become invalid. In this case, the cache is completely cleared.
         */
        void validate(JTree tree) {
            TreeModel currentModel = tree.getModel();
            boolean currentRootVisible = tree.isRootVisible();
            if (model != currentModel || rootVisible != currentRootVisible) {
                contexts.clear();
                model = currentModel;
                rootVisible = currentRootVisible;
            }
        }

        AccessibleContext get(TreePath path) {
            return contexts.get(path);
        }

        /**
         * Adds a node context to the cache.
         * If the cache size exceeds {@link #MAX_RETAINED_TREE_CONTEXTS}, the oldest
         * (least recently used) entry is removed following the LRU eviction policy.
         */
        void put(TreePath path, AccessibleContext context) {
            contexts.put(path, context);
            while (contexts.size() > MAX_RETAINED_TREE_CONTEXTS) {
                Iterator<Map.Entry<TreePath, AccessibleContext>> iterator = contexts.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }
}
