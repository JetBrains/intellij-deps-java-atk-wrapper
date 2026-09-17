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
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;

/**
 * Handles accessibility contexts ({@link AccessibleContext}) specifically for the {@link JList} component.
 * <p>
 * <b>The Problem:</b><br>
 * {@code AccessibleJList} reports the correct item in a property change event, but it builds that item with
 * {@code getAccessibleChild}, which creates a new {@code AccessibleJListChild} on every call. The ATK bridge
 * receives a different object for the same item each time, so the item has no stable identity at the native
 * level. A screen reader cannot match the item against the list it already knows. It then reads the whole path
 * from the frame down to the item, or it stops the announcement early.
 * <p>
 * <b>The Solution:</b><br>
 * This class keeps a stable {@link AccessibleContext} for each item it reports. The wrapper substitutes the
 * cached context for the fresh object that Swing supplies, so the ATK bridge sees one identifier per item while
 * the user navigates the list. It mirrors {@link JTreeContextCacheManager}.
 * <p>
 * The cache keys an item by its index, because {@code AccessibleJListChild} reads the model for its index on
 * every call. It drops an index that the model no longer holds. A code completion list grows while the results
 * arrive, so this happens often.
 * <p>
 * An item context holds the cell renderer that it captured in its constructor, so the cache is cleared when the
 * list takes another renderer. A cache extends the life of an item context, so it must also drop a context that
 * reports through stale state.
 * <p>
 * See: IJPL-242489, IJPL-242490
 */
final class JListContextCacheManager {

    /**
     * The maximum number of item contexts we retain in memory for a single list.
     * Used to limit the LRU cache size to prevent excessive memory consumption.
     */
    private static final int MAX_RETAINED_LIST_CONTEXTS = 512;

    /**
     * A reverse-lookup registry mapping a list's abstract {@link AccessibleContext} back to its original
     * {@link JList} object.
     * <p>
     * Must be accessed on EDT.
     * <p>
     * <b>Why this is needed:</b><br>
     * An {@code active-descendant-changed} event is fired from the context of the list itself. To find the
     * selected item we need the underlying {@link JList} component and its lead selection index. A
     * {@link WeakHashMap} keeps the registry from holding a closed list in memory.
     */
    private static final Map<AccessibleContext, WeakReference<JList<?>>> listsByAccessibleContext = new WeakHashMap<>();

    /**
     * A unique key used to bind the item cache ({@link JListContextCache}) directly to the {@link JList}
     * instance through {@link javax.swing.JComponent#putClientProperty}.
     * <p>
     * The cache is then collected together with the list itself, which prevents a memory leak.
     */
    private static final Object LIST_CONTEXT_CACHE_KEY = new Object();

    private JListContextCacheManager() {
    }

    /**
     * Remembers the association between a {@link JList} component and its accessibility context in the global
     * weak reference registry.
     */
    static void rememberJList(JList<?> list, AccessibleContext listAccContext) {
        if (list == null || listAccContext == null) {
            return;
        }
        listsByAccessibleContext.put(listAccContext, new WeakReference<JList<?>>(list));
    }

    /**
     * Resolves the accessible context of the selected item, for a list known only by its own context.
     *
     * @param listAccContext the accessibility context of the list itself, not of an item
     * @return the accessible context of the selected item, or {@code null} when the context is null, the list
     * is already collected, or the list has no selection
     */
    static AccessibleContext resolveActiveDescendant(AccessibleContext listAccContext) {
        if (listAccContext == null) {
            return null;
        }
        WeakReference<JList<?>> listRef = listsByAccessibleContext.get(listAccContext);
        JList<?> list = listRef == null ? null : listRef.get();
        return resolveActiveDescendant(list, listAccContext);
    }

    /**
     * Resolves the accessible context of the selected item in the specified list. It populates the cache, so
     * that ATK receives a stable identifier.
     *
     * @param list           the list component
     * @param listAccContext the accessibility context of the list
     * @return the accessible context of the selected item, or {@code null}
     */
    static AccessibleContext resolveActiveDescendant(JList<?> list, AccessibleContext listAccContext) {
        if (list == null || listAccContext == null) {
            return null;
        }

        rememberJList(list, listAccContext);

        ListModel<?> model = list.getModel();
        if (model == null) {
            return null;
        }

        int index = list.getLeadSelectionIndex();
        if (index < 0) {
            index = list.getSelectedIndex();
        }
        if (index < 0 || index >= model.getSize()) {
            return null;
        }

        return getAccessibleContextForIndex(list, listAccContext, index);
    }

    /**
     * Returns the cached context of the item at the given index, or reads it from the list and caches it.
     */
    private static AccessibleContext getAccessibleContextForIndex(JList<?> list,
                                                                 AccessibleContext listAccContext,
                                                                 int index) {
        JListContextCache cache = getListContextCache(list);
        AccessibleContext cachedContext = cache.get(index);
        if (cachedContext != null) {
            AtkWrapperDisposer.getInstance().addRecord(cachedContext);
            return cachedContext;
        }

        Accessible child = listAccContext.getAccessibleChild(index);
        if (child == null) {
            return null;
        }

        AccessibleContext childContext = child.getAccessibleContext();
        if (childContext == null) {
            return null;
        }

        cache.put(index, childContext);
        AtkWrapperDisposer.getInstance().addRecord(childContext);
        return childContext;
    }

    /**
     * Returns the LRU cache bound to a specific list, and creates it when it does not exist yet.
     * <p>
     * It calls {@code validate()} first, which drops the entries that the current model no longer holds.
     */
    private static JListContextCache getListContextCache(JList<?> list) {
        Object value = list.getClientProperty(LIST_CONTEXT_CACHE_KEY);
        JListContextCache cache;
        if (value instanceof JListContextCache) {
            cache = (JListContextCache) value;
        } else {
            cache = new JListContextCache(list);
            list.putClientProperty(LIST_CONTEXT_CACHE_KEY, cache);
        }
        cache.validate(list);
        return cache;
    }

    /**
     * A local cache for each {@link JList} instance. It retains the contexts of recently reported items and
     * prevents their release.
     * <p>
     * <b>Why this is needed:</b><br>
     * An item context is collected quickly once a direct reference to it is lost. This extends its lifetime, up
     * to the {@link #MAX_RETAINED_LIST_CONTEXTS} limit, while the user navigates the list.
     */
    private static final class JListContextCache {
        private final LinkedHashMap<Integer, AccessibleContext> contexts =
                new LinkedHashMap<Integer, AccessibleContext>(16, 0.75f, true);
        private ListModel<?> model;
        private ListCellRenderer<?> renderer;

        JListContextCache(JList<?> list) {
            model = list.getModel();
            renderer = list.getCellRenderer();
        }

        /**
         * Validates the cached data against the current state of the list.
         * <p>
         * A new model invalidates every index, so the cache is cleared. A new cell renderer does the same,
         * because {@code AccessibleJListChild} keeps the renderer that it captured in its constructor and
         * reports its text through that renderer. A shorter model invalidates the indexes it no longer holds,
         * and only those entries are dropped.
         */
        void validate(JList<?> list) {
            ListModel<?> currentModel = list.getModel();
            ListCellRenderer<?> currentRenderer = list.getCellRenderer();
            if (model != currentModel || renderer != currentRenderer) {
                contexts.clear();
                model = currentModel;
                renderer = currentRenderer;
                return;
            }

            int size = currentModel == null ? 0 : currentModel.getSize();
            Iterator<Map.Entry<Integer, AccessibleContext>> iterator = contexts.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey() >= size) {
                    iterator.remove();
                }
            }
        }

        AccessibleContext get(int index) {
            return contexts.get(Integer.valueOf(index));
        }

        /**
         * Adds an item context to the cache. The least recently used entry is removed when the cache exceeds
         * {@link #MAX_RETAINED_LIST_CONTEXTS}.
         */
        void put(int index, AccessibleContext context) {
            contexts.put(Integer.valueOf(index), context);
            while (contexts.size() > MAX_RETAINED_LIST_CONTEXTS) {
                Iterator<Map.Entry<Integer, AccessibleContext>> iterator = contexts.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }
}
