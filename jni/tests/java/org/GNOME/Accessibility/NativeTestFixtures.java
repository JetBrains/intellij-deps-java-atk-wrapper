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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * The Swing components of the native unit tests in {@code jni/tests/}. The C tests call these methods through JNI
 * and get the {@link AccessibleContext} of a component, built on the EDT.
 * <p>
 * The returned contexts must stay reachable while a test uses their ATK objects: the disposer releases the native
 * resource of a context that the garbage collector finds. The C tests hold them as JNI local references, which
 * stay alive on the test thread.
 */
final class NativeTestFixtures {

    private NativeTestFixtures() {
    }

    /** Loads {@code libatk-wrapper.so}, so that the JVM calls {@code JNI_OnLoad} and binds the native methods. */
    static void loadLibrary(String path) {
        System.load(path);
    }

    static AccessibleContext button(String text) throws Exception {
        return onEdt(() -> new JButton(text).getAccessibleContext());
    }






    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(action.call());
                } catch (Exception e) {
                    failure.set(e);
                }
            });
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
