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

"""What Orca says for a real Swing application with the wrapper. A trial, run by `make check-orca`.

The tests of test_atspi.py check what reaches the AT-SPI bus; these check what a screen reader makes of it.
They assert on parts of a phrase, not on whole phrases: the wording differs between the versions of Orca.
"""

import os

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi  # noqa: E402

import pytest  # noqa: E402

from conftest import (  # noqa: E402
    FRAME_NAME, KEY_DOWN, KEY_ESCAPE, KEY_F10, KEY_LEFT, KEY_RIGHT, press, wait_for,
)
from orca_speech import start_orca  # noqa: E402

FOCUSED = "object:state-changed:focused"

# The key to press, its name, the accessible name of the menu entry that gets the focus, and the part of that
# name that Orca must say. The names are those of the menus of AtspiTestApp.
MENU_STEPS = (
    (KEY_F10, "F10", "File", "file"),
    (KEY_DOWN, "Down", "Open", "open"),
    (KEY_DOWN, "Down", "Recent", "recent"),
    (KEY_RIGHT, "Right", "Project", "project"),
    # The submenu closes. Swing fires no focus event here; the menu selection listener of AtkWrapper does.
    (KEY_LEFT, "Left", "Recent", "recent"),
)


@pytest.fixture(scope="module")
def orca(app, journal):
    """Starts Orca when the window of the application is active, so that the keys of a test go to it."""
    wait_for(
        lambda: journal.find("window:activate", role=Atspi.Role.FRAME, name=FRAME_NAME),
        "window:activate from the frame",
        journal=journal,
    )
    running = start_orca(os.environ["ATSPI_LOG_DIR"])
    yield running
    running.stop()


def _focused_since(journal, start, name):
    for event in journal.events[start:]:
        if event.type.startswith(FOCUSED) and event.name == name and event.detail1 == 1:
            return event
    return None


def test_orca_announces_menu_navigation(app, journal, orca):
    """Checks: while the user walks through a menu with the keyboard, Orca says the name of every menu entry
    that gets the focus, also of the menu that gets the focus back when its submenu closes.

    Why (Swing, Orca, wrapper): Orca speaks an entry when the bus delivers `object:state-changed:focused` for
    it. Swing fires the accessible focus event when an entry becomes selected, and the wrapper passes it on.
    When a submenu closes, its menu stays selected and Swing fires nothing; the wrapper then reports the focus
    itself, and without that Orca stays silent (IJPL-253929). Each step waits for the event on the bus first:
    a missing event is a failure of the wrapper or of the key, a missing phrase after the event is one of Orca.
    """
    try:
        for number, (key, key_name, focus_name, phrase) in enumerate(MENU_STEPS, start=1):
            step = "step %d, the key %s" % (number, key_name)
            events_before = len(journal.events)
            speech_before = orca.mark()

            press(key)

            wait_for(
                lambda: _focused_since(journal, events_before, focus_name),
                "%s: %s from '%s'" % (step, FOCUSED, focus_name),
                journal=journal,
            )
            orca.wait_for_phrase(
                phrase,
                mark=speech_before,
                what="%s: Orca to say '%s' after the focus moved to '%s'" % (step, phrase, focus_name),
                journal=journal,
            )
    finally:
        # Close the menu, also after a failed step, so that the application is left without an open popup.
        press(KEY_ESCAPE)
        press(KEY_ESCAPE)
