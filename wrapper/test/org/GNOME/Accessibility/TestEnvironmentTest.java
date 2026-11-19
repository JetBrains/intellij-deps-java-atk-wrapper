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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Test;

/**
 * Checks the assumptions the other tests rely on: no display and no native library.
 */
class TestEnvironmentTest {

    /**
     * Checks: the test JVM runs in headless mode.
     * <p>
     * Why (test setup): the tests build Swing components without showing them and must run on a build machine
     * without a display. {@code make check} passes {@code -Djava.awt.headless=true}; this test reports it with a
     * clear message when the tests are started in another way without the option.
     */
    @Test
    void runsHeadless() {
        assertTrue(GraphicsEnvironment.isHeadless(), "the tests must run with -Djava.awt.headless=true");
    }

    /**
     * Checks: the bundled native library is not a resource on the test class path.
     * <p>
     * Why (test setup): the static initializer of {@link AtkWrapper} loads the library from the class path and
     * enables accessibility when it finds an AT-SPI bus. The tests assume that this did not happen: they replace
     * the native calls with a {@link RecordingNativeBridge} and pass fake native handles. The test looks for the
     * resource, not for the disabled accessibility, because accessibility also stays disabled when the library
     * loads and no bus is found.
     * <p>
     * May change when: the wrapper finds or loads the native library in another way than as a class path
     * resource. The check has to follow, or it passes while the library is loaded.
     */
    @Test
    void nativeLibraryIsNotOnTheClassPath() {
        String resourcePath = AtkWrapper.getBundledNativeLibraryResourcePath();
        assertNull(AtkWrapper.class.getResource(resourcePath),
                resourcePath + " must not be on the test class path: the tests pass fake native handles to the"
                        + " native layer, and with a reachable AT-SPI bus they would talk to it");
    }
}
