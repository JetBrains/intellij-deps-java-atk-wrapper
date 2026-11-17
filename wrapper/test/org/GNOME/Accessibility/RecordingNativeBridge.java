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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.accessibility.AccessibleContext;

/**
 * A {@link NativeBridge} that records the calls instead of passing them to {@code libatk-wrapper.so}.
 */
final class RecordingNativeBridge implements NativeBridge {

    /**
     * One recorded call. {@code args} holds the arguments after the accessible context; an {@code Object[]} argument
     * is recorded as a list, so that calls compare and print by content.
     */
    record Call(String method, AccessibleContext context, List<Object> args) {

        static Call of(String method, AccessibleContext context, Object... args) {
            return new Call(method, context, asList(args));
        }

        private static List<Object> asList(Object[] args) {
            Object[] copy = args.clone();
            for (int i = 0; i < copy.length; i++) {
                if (copy[i] instanceof Object[] nested) {
                    copy[i] = asList(nested);
                }
            }
            return Collections.unmodifiableList(Arrays.asList(copy));
        }
    }

    /**
     * Shared by all instances: the disposer may release a handle of an earlier test while a later test runs, and the
     * handle must not be mistaken for one of the later test.
     */
    private static final AtomicLong NEXT_RESOURCE = new AtomicLong(1);

    private final List<Call> calls = new ArrayList<>();
    private final List<AccessibleContext> createdResources = new ArrayList<>();
    private final List<Long> createdHandles = new ArrayList<>();
    private final List<Long> releasedHandles = new ArrayList<>();

    synchronized List<Call> calls() {
        return new ArrayList<>(calls);
    }

    synchronized List<Call> calls(String method) {
        return calls.stream().filter(call -> call.method().equals(method)).toList();
    }

    /** Returns the {@code emitSignal} calls with the given {@link AtkSignal} id. */
    synchronized List<Call> signals(int signalId) {
        return calls.stream()
                .filter(call -> call.method().equals("emitSignal") && call.args().get(0).equals(signalId))
                .toList();
    }

    /** The contexts this bridge created a native resource for, in order. */
    synchronized List<AccessibleContext> createdResources() {
        return new ArrayList<>(createdResources);
    }

    /**
     * The contexts whose native resource created by this bridge was released, in order. Releases of resources
     * created by the bridges of earlier tests are ignored.
     */
    synchronized List<AccessibleContext> releasedResources() {
        List<AccessibleContext> released = new ArrayList<>();
        for (long handle : releasedHandles) {
            released.add(createdResources.get(createdHandles.indexOf(handle)));
        }
        return released;
    }

    /** Forgets the recorded calls and native resources. */
    synchronized void clear() {
        calls.clear();
        createdResources.clear();
        createdHandles.clear();
        releasedHandles.clear();
    }

    @Override
    public synchronized long createNativeResources(AccessibleContext ac) {
        long handle = NEXT_RESOURCE.getAndIncrement();
        createdResources.add(ac);
        createdHandles.add(handle);
        return handle;
    }

    @Override
    public synchronized void releaseNativeResources(long ref) {
        if (createdHandles.contains(ref)) {
            releasedHandles.add(ref);
        }
    }

    @Override
    public synchronized void focusNotify(AccessibleContext ac) {
        calls.add(Call.of("focusNotify", ac));
    }

    @Override
    public synchronized void emitSignal(AccessibleContext ac, int id, Object[] args) {
        calls.add(Call.of("emitSignal", ac, id, args));
    }

    @Override
    public synchronized void objectStateChange(AccessibleContext ac, Object state, boolean value) {
        calls.add(Call.of("objectStateChange", ac, state, value));
    }

    @Override
    public synchronized void windowOpen(AccessibleContext ac, boolean isToplevel) {
        calls.add(Call.of("windowOpen", ac, isToplevel));
    }

    @Override
    public synchronized void windowClose(AccessibleContext ac, boolean isToplevel) {
        calls.add(Call.of("windowClose", ac, isToplevel));
    }

    @Override
    public synchronized void windowMinimize(AccessibleContext ac) {
        calls.add(Call.of("windowMinimize", ac));
    }

    @Override
    public synchronized void windowMaximize(AccessibleContext ac) {
        calls.add(Call.of("windowMaximize", ac));
    }

    @Override
    public synchronized void windowRestore(AccessibleContext ac) {
        calls.add(Call.of("windowRestore", ac));
    }

    @Override
    public synchronized void windowActivate(AccessibleContext ac) {
        calls.add(Call.of("windowActivate", ac));
    }

    @Override
    public synchronized void windowDeactivate(AccessibleContext ac) {
        calls.add(Call.of("windowDeactivate", ac));
    }

    @Override
    public synchronized void windowStateChange(AccessibleContext ac) {
        calls.add(Call.of("windowStateChange", ac));
    }

    @Override
    public synchronized void componentAdded(AccessibleContext ac) {
        calls.add(Call.of("componentAdded", ac));
    }

    @Override
    public synchronized void componentRemoved(AccessibleContext ac) {
        calls.add(Call.of("componentRemoved", ac));
    }

    @Override
    public synchronized void boundsChanged(AccessibleContext ac) {
        calls.add(Call.of("boundsChanged", ac));
    }

    @Override
    public synchronized void dispatchKeyEvent(AtkKeyEvent e) {
        calls.add(Call.of("dispatchKeyEvent", null, e));
    }
}
