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

    @Test
    void runsHeadless() {
        assertTrue(GraphicsEnvironment.isHeadless(), "the tests must run with -Djava.awt.headless=true");
    }

    @Test
    void nativeLibraryIsNotOnTheClassPath() {
        String resourcePath = AtkWrapper.getBundledNativeLibraryResourcePath();
        assertNull(AtkWrapper.class.getResource(resourcePath),
                resourcePath + " must not be on the test class path: the tests pass fake native handles to the"
                        + " native layer, and with a reachable AT-SPI bus they would talk to it");
    }
}
