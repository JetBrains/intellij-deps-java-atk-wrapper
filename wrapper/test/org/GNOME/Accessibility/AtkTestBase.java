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
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.BeforeEach;

/**
 * Installs a {@link RecordingNativeBridge} for every test and gives access to the private parts of the wrapper
 * that the native layer calls.
 * <p>
 * The production bridge is never restored: the disposer thread releases the fake handles of a recording bridge
 * whenever the garbage collector finds their contexts, also after the test, and the production bridge would pass
 * them to native code. The bridge of the last test stays installed instead.
 */
abstract class AtkTestBase {

    protected RecordingNativeBridge bridge;

    @BeforeEach
    final void installRecordingBridge() throws Exception {
        bridge = new RecordingNativeBridge();
        AtkWrapper.nativeBridge = bridge;
        // Forget the state left by the previous test: the focus history and the contexts registered with
        // the disposer, which would otherwise not reach the createNativeResources of the new bridge.
        onEdt(() -> {
            AtkWrapper.forgetFocusHistory();
            AtkWrapperDisposer.forgetRegisteredContexts();
            return null;
        });
    }

    /** Runs the action on the EDT, where the wrapper handles Swing events, and returns its result. */
    static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() instanceof Error error) {
            throw error;
        }
        if (failure.get() instanceof Exception exception) {
            throw exception;
        }
        return result.get();
    }

    /** Calls a private JNI upcall the way the native layer does, ignoring its access modifier. */
    static Object invokeStatic(Class<?> type, String name, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * Builds the tree model used by the tree tests:
     * <pre>
     * root
     * ├── a
     * │   ├── a1
     * │   └── a2
     * └── b
     * </pre>
     */
    static DefaultMutableTreeNode createTreeModelRoot() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
        a.add(new DefaultMutableTreeNode("a1"));
        a.add(new DefaultMutableTreeNode("a2"));
        root.add(a);
        root.add(new DefaultMutableTreeNode("b"));
        return root;
    }
}
