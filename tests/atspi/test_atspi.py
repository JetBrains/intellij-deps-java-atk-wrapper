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

from conftest import FRAME_NAME, SANITIZER_MESSAGES, wait_for  # noqa: E402
from orca_speech import KEY_ESCAPE, KEY_F10, press  # noqa: E402


def test_application_is_on_the_bus(app):
    """Checks: the application is on the AT-SPI desktop with the toolkit name of the wrapper, and its window is
    a child of the application with the role `frame` and the window title as its name.

    Why (wrapper): no lower-level test starts the wrapper the way an application does: the unit tests run
    without the native library, and the native tests never load the ATK bridge. The window reaches the
    application only through the AWT event WINDOW_OPENED, which no headless test produces.
    """
    assert app.application.get_toolkit_name() == "J2SE-access-bridge"
    assert app.frame.get_role() == Atspi.Role.FRAME
    assert app.frame.get_name() == FRAME_NAME


def test_window_activation_is_reported(app, journal):
    """Checks: when the window becomes active, the bus delivers `window:activate` with the frame as its source.

    Why (wrapper): `windowActivated` of the window adapter of AtkWrapper sends the activation to the native
    layer, which emits the ATK signal `activate` on the window. Only a real window manager activates a window.
    """
    wait_for(
        lambda: journal.find("window:activate", role=Atspi.Role.FRAME, name=FRAME_NAME),
        "window:activate from the frame",
        journal=journal,
    )


def test_key_reaches_the_application(app, journal):
    """Checks: F10, pressed through the X server as a user presses it, moves the focus to the menu `File`.

    Why (wrapper, test setup): the AWT key listener of AtkWrapper passes every key to the native layer, which
    reads the fields of AtkKeyEvent on the GLib thread for the key snoopers of ATK; a read of the long field
    `timestamp` with GetIntField was a fatal error of -Xcheck:jni, and the application ended at its first key.
    The keys reach the application only in the X11 session that run.sh sets up, also from a Wayland desktop.
    """
    # The keys go to the active window.
    wait_for(
        lambda: journal.find("window:activate", role=Atspi.Role.FRAME, name=FRAME_NAME),
        "window:activate from the frame",
        journal=journal,
    )
    events_before = len(journal.events)
    press(KEY_F10)
    try:
        wait_for(
            lambda: any(
                event.type.startswith("object:state-changed:focused") and event.name == "File" and event.detail1 == 1
                for event in journal.events[events_before:]
            ),
            "object:state-changed:focused from 'File' after F10",
            journal=journal,
        )
    finally:
        # Close the menu, so that the application is left without an open popup.
        press(KEY_ESCAPE)
        press(KEY_ESCAPE)


# The test below looks at what the application did during the tests above, so it must stay the last one of this
# file: pytest runs the tests of a file in their order.


def test_application_is_still_running(app):
    """Checks: after the tests above the application is alive, and no sanitizer reported an error.

    Why (test setup): a crash, a fatal error of -Xcheck:jni or a sanitizer error ends the application, and the
    tests above then fail with a D-Bus error that does not say why. The sanitizers report only for a library
    built with `configure --enable-sanitizers`.
    """
    errors = app.output_lines_with(SANITIZER_MESSAGES)
    assert not errors, "a sanitizer reported an error, see app-output.log: " + "; ".join(errors)
    assert app.process.poll() is None, (
        "the application exited with code %s during the tests, see app-output.log" % app.process.returncode
    )
