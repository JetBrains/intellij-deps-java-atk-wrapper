# Java ATK Wrapper for GNOME
# Copyright (C) 2026 JetBrains s.r.o.
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this library; if not, see <https://www.gnu.org/licenses/>.

"""What a real Swing application with the wrapper sends to the AT-SPI bus.

These tests cover only what the unit tests cannot reach: the start of the wrapper in an application, the AWT
window and focus events, the calls between the GLib thread and the EDT, and the delivery over D-Bus. A behavior
that a unit test in wrapper/test/ or jni/tests/ can check belongs there.
"""

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi  # noqa: E402

from conftest import FRAME_NAME, SANITIZER_MESSAGES  # noqa: E402


def test_application_is_on_the_bus(app):
    """Checks: a Swing application started with the wrapper as its assistive technology appears on the AT-SPI
    desktop, with the toolkit name of the wrapper, and its window is a child of the application with the role
    `frame` and the title as its name.

    Why (wrapper, test setup): this is the only test of the start of the wrapper. The static initializer of
    AtkWrapper loads the native library from the jar, looks for the AT-SPI bus and loads the ATK bridge; the unit
    tests run without the library on purpose, and the native tests never load the bridge. The window becomes a
    child of the application only through the AWT event WINDOW_OPENED and `jaw_toplevel_add_window`, which no
    headless test runs; the registration of top-level windows was broken before (IJPL-253929, IJPL-256097).
    """
    assert app.application.get_toolkit_name() == "J2SE-access-bridge"
    assert app.frame.get_role() == Atspi.Role.FRAME
    assert app.frame.get_name() == FRAME_NAME


# The test below looks at what the application did during the tests above, so it must stay the last one of this
# file: pytest runs the tests of a file in their order.


def test_application_is_still_running(app):
    """Checks: after the tests above the application is alive, and no sanitizer reported an error.

    Why (wrapper, test setup): a fatal error of -Xcheck:jni, a crash in the native layer or an error that a
    sanitizer found ends the application. The tests above then fail with a D-Bus error that does not name the
    cause; this test names it. The sanitizers report only when the library is built with
    `configure --enable-sanitizers`.
    """
    errors = app.output_lines_with(SANITIZER_MESSAGES)
    assert not errors, "a sanitizer reported an error, see app-output.log: " + "; ".join(errors)
    assert app.process.poll() is None, (
        "the application exited with code %s during the tests, see app-output.log" % app.process.returncode
    )
