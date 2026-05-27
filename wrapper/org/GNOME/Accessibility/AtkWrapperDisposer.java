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

import javax.accessibility.AccessibleContext;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the association between an AccessibleContext
 * and native resources. Can be used to create such
 * associations and ensures that the native resource associated
 * with the AccessibleContext is released when the AccessibleContext
 * is garbage collected.
 * <p>
 * Java classes are fully responsible for creating associations between
 * an AccessibleContext and native resources. For example, when a JNI upcall method
 * returns an AccessibleContext, native assumes that the corresponding native resource
 * has already been created and the association between the AccessibleContext and the
 * native resource exists.
 */
public class AtkWrapperDisposer implements Runnable {
    // Reference queue that holds objects ready for garbage collection
    private static final ReferenceQueue<AccessibleContext> queue = new ReferenceQueue<>();

    // Maps PhantomReferences and their associated native resource pointer
    private static final Map<PhantomReference<AccessibleContext>, Long> phantomMap = new HashMap<>();

    // Maps AccessibleContext object with native resource pointer
    private static final WeakHashMap<AccessibleContext, Long> weakHashMap = new WeakHashMap<>();

    private static final Object lock = new Object();
    private static final Logger log = Logger.getLogger("org.GNOME.Accessibility.AtkWrapperDisposer");
    private static volatile AtkWrapperDisposer INSTANCE = null;

    private AtkWrapperDisposer() {
    }

    private void init() {
        Thread t = new Thread(INSTANCE, "Atk Wrapper Disposer");
        t.setPriority(Thread.MAX_PRIORITY);
        t.setContextClassLoader(null);
        t.setDaemon(true);
        t.start();
    }

    public static synchronized AtkWrapperDisposer getInstance() {
        if (INSTANCE == null) {
            synchronized (AtkWrapperDisposer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AtkWrapperDisposer();
                    INSTANCE.init();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Monitors the reference queue and releases native resources when an associated
     * AccessibleContext is garbage collected. The native resource is released using
     * {@link AtkWrapper#releaseNativeResources}.
     */
    public void run() {
        while (true) {
            try {
                // When an AccessibleContext is freed, release the associated native resource
                Reference<? extends AccessibleContext> obj = queue.remove();
                long nativeReference;
                synchronized (lock) {
                    nativeReference = phantomMap.remove(obj);
                }
                AtkWrapper.releaseNativeResources(nativeReference);
                obj.clear();
                obj = null;
            } catch (Exception e) {
                if (log.isLoggable(Level.SEVERE)) {
                    log.log(Level.SEVERE, "Exception while removing reference: ", e);
                }
            }
        }
    }

    /**
     * Associates an AccessibleContext with a newly created native resource. If the AccessibleContext
     * is not already registered, a new native resource pointer is created using
     * {@link AtkWrapper#createNativeResources}
     *
     * @param ac The AccessibleContext to associate with a native resource.
     */
    public void addRecord(AccessibleContext ac) {
        if (ac == null) {
            return;
        }

        synchronized (lock) {
            if (weakHashMap.containsKey(ac)) {
                return;
            }
        }

        long nativeReference = AtkWrapper.createNativeResources(ac);
        if (nativeReference == -1) {
            return;
        }

        synchronized (lock) {
            if (!weakHashMap.containsKey(ac)) {
                PhantomReference<AccessibleContext> phantomReference = new PhantomReference<>(ac, queue);
                phantomMap.put(phantomReference, nativeReference);
                weakHashMap.put(ac, nativeReference);
                nativeReference = -1;
            }
        }

        if (nativeReference != -1) {
            AtkWrapper.releaseNativeResources(nativeReference);
        }
    }

    private long getRecord(AccessibleContext ac) {
        synchronized (lock) {
            if (weakHashMap.containsKey(ac)) {
                return weakHashMap.get(ac);
            }
        }
        return -1;
    }

    // JNI upcalls section

    /**
     * Retrieves the native resource associated with the given AccessibleContext.
     * If no record exists, returns -1.
     *
     * @param ac The AccessibleContext whose native resource is requested.
     * @return The native resource pointer associated with the given AccessibleContext,
     * or -1 if no record exists.
     */
    private static long get_resource(AccessibleContext ac) {
        return AtkWrapperDisposer.getInstance().getRecord(ac);
    }
}