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

/**
 * The native calls that the Java layer makes when it translates Swing events into ATK signals.
 * <p>
 * It covers every call made after initialization; only the calls that load and shut down
 * {@code libatk-wrapper.so} ({@code initNativeLibrary}, {@code loadAtkBridge}, {@code nativeCleanup}) stay direct.
 * <p>
 * The production implementation forwards every call to the native method of {@link AtkWrapper} with the same name.
 * Unit tests replace it through {@link AtkWrapper#nativeBridge} with an implementation that records the calls, so
 * the event translation runs without {@code libatk-wrapper.so} and without a display.
 */
interface NativeBridge {

    long createNativeResources(AccessibleContext ac);

    void releaseNativeResources(long ref);

    void focusNotify(AccessibleContext ac);

    void emitSignal(AccessibleContext ac, int id, Object[] args);

    void objectStateChange(AccessibleContext ac, Object state, boolean value);

    void windowOpen(AccessibleContext ac, boolean isToplevel);

    void windowClose(AccessibleContext ac, boolean isToplevel);

    void windowMinimize(AccessibleContext ac);

    void windowMaximize(AccessibleContext ac);

    void windowRestore(AccessibleContext ac);

    void windowActivate(AccessibleContext ac);

    void windowDeactivate(AccessibleContext ac);

    void windowStateChange(AccessibleContext ac);

    void componentAdded(AccessibleContext ac);

    void componentRemoved(AccessibleContext ac);

    void boundsChanged(AccessibleContext ac);

    void dispatchKeyEvent(AtkKeyEvent e);
}
