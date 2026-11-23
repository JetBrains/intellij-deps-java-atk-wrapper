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

The text tests are here because a text offset crosses every layer: Swing counts in the `char`s of a Java
`String`, ATK in characters, and the AT-SPI bridge reads the text of an event back from the object. Only on the
bus are the answer to a request and the event of the same change seen together, as a screen reader sees them.
"""

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi  # noqa: E402

import pytest  # noqa: E402

from conftest import (  # noqa: E402
    FRAME_NAME, KEY_BACKSPACE, KEY_END, KEY_ESCAPE, KEY_F10, KEY_HOME, KEY_LEFT, KEY_X,
    SANITIZER_MESSAGES, find_descendant, has_focus, press, wait_for, window_is_active,
)

# The button of AtspiTestApp. It is the first component of the window, so it has the focus when the window opens.
BUTTON_NAME = "First"

# The text field of AtspiTestApp, see UNICODE_TEXT_FIELD_NAME and UNICODE_TEXT there: "A", an emoji outside the
# BMP and "B". The emoji is one character for ATK and two `char`s for Swing, so behind it the two count
# differently: the caret between the emoji and "B" is at the Swing index 3 and at the ATK offset 2.
TEXT_FIELD_NAME = "Unicode text"
EMOJI = "\U0001F600"
TEXT = "A" + EMOJI + "B"

FOCUSED = "object:state-changed:focused"
CARET_MOVED = "object:text-caret-moved"
TEXT_INSERTED = "object:text-changed:insert"
TEXT_DELETED = "object:text-changed:delete"


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


def test_key_reaches_the_application(app, journal):
    """Checks: F10, pressed through the X server as a user presses it, moves the focus to the menu `File`.

    Why (wrapper, test setup): the AWT key listener of AtkWrapper passes every key to the native layer, which
    reads the fields of AtkKeyEvent on the GLib thread for the key snoopers of ATK; a read of the long field
    `timestamp` with GetIntField was a fatal error of -Xcheck:jni, and the application ended at its first key.
    The keys reach the application only in the X11 session that run.sh sets up, also from a Wayland desktop.
    """
    # The keys go to the active window.
    wait_for(
        lambda: window_is_active(app.frame),
        "the state `active` of the window '%s'" % FRAME_NAME,
        journal=journal,
    )
    button = wait_for(
        lambda: find_descendant(app.frame, BUTTON_NAME),
        "the button '%s' below the frame" % BUTTON_NAME,
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
    # Swing gives the focus back to the button when the menu closes (`BasicPopupMenuUI.MenuKeyboardHelper`).
    # The next test moves the focus, and it must not do so before that.
    wait_for(
        lambda: has_focus(button),
        "the focus back on the button '%s' after the menu closed" % BUTTON_NAME,
        journal=journal,
    )


@pytest.fixture(scope="module")
def text_field(app, journal):
    """The text field of AtspiTestApp with the keyboard focus, so that the keys of a test go to it."""
    wait_for(
        lambda: window_is_active(app.frame),
        "the state `active` of the window '%s'" % FRAME_NAME,
        journal=journal,
    )
    field = wait_for(
        lambda: find_descendant(app.frame, TEXT_FIELD_NAME),
        "the text field '%s' below the frame" % TEXT_FIELD_NAME,
        journal=journal,
    )
    events_before = len(journal.events)
    if not Atspi.Component.grab_focus(field):
        pytest.fail("the text field '%s' refused the focus" % TEXT_FIELD_NAME)
    wait_for(
        lambda: journal.find(FOCUSED, name=TEXT_FIELD_NAME, detail1=1, start=events_before),
        "%s from the text field '%s'" % (FOCUSED, TEXT_FIELD_NAME),
        journal=journal,
    )
    return field


def _text(field):
    return Atspi.Text.get_text(field, 0, Atspi.Text.get_character_count(field))


def _press(journal, key, key_name, *event_types):
    """Presses a key and waits for the events of the text field that the key must cause, in their order.

    Returns the events. The last one must be the last event that the key causes: the next key then finds no
    late event of this one, which it would take for its own.
    """
    position = len(journal.events)
    press(key)
    found = []
    for event_type in event_types:
        def next_event():
            for index in range(position, len(journal.events)):
                event = journal.events[index]
                if event.type == event_type and event.name == TEXT_FIELD_NAME:
                    return index, event
            return None

        index, event = wait_for(
            next_event,
            "%s from the text field after the key %s" % (event_type, key_name),
            journal=journal,
        )
        position = index + 1
        found.append(event)
    return found


def _move_caret_to_start(field, journal):
    """Puts the caret before the first character, which also removes a selection."""
    if Atspi.Text.get_caret_offset(field) != 0:
        _press(journal, KEY_HOME, "Home", CARET_MOVED)


def _move_caret_behind_emoji(field, journal):
    """Puts the caret between the emoji and "B": End, then Left over "B", a character of one `char`.

    The caret never steps over the emoji here: how many `char`s an arrow key moves it over a surrogate pair is
    up to the view that Swing chooses for the text.
    """
    _move_caret_to_start(field, journal)
    _press(journal, KEY_END, "End", CARET_MOVED)
    _press(journal, KEY_LEFT, "Left", CARET_MOVED)
    assert Atspi.Text.get_caret_offset(field) == 2, "the setup did not put the caret behind the emoji"


def _type_and_erase(journal):
    """Types "x" and erases it, so that the next change of the text is not the first one of the text field.

    The native layer takes the length of a change from the character count that it stored at the previous
    change (`Previous_Count` in AtkWrapper.c), and before the first change it has stored none. The length of
    the first change is therefore not what these tests are about.
    """
    _press(journal, KEY_X, "x", TEXT_INSERTED, CARET_MOVED)
    _press(journal, KEY_BACKSPACE, "BackSpace", TEXT_DELETED, CARET_MOVED)


def test_text_with_emoji_is_read_in_characters(text_field):
    """Checks: a text with a character outside the BMP has one character per code point: the text "A", emoji,
    "B" has three characters, the emoji is the character 1 and "B" is the character 2.

    Why (Swing, ATK, wrapper): Swing counts a text in the `char`s of a Java `String`, where the emoji takes two,
    and ATK counts in characters of the UTF-8 text it returns. `AtkText` converts between the two. No unit test
    checks the offsets, and a screen reader that gets "B" at the offset 3 reads past the end of the text.
    """
    assert Atspi.Text.get_character_count(text_field) == 3
    assert _text(text_field) == TEXT
    assert Atspi.Text.get_text(text_field, 1, 2) == EMOJI
    assert Atspi.Text.get_text(text_field, 2, 3) == "B"
    assert Atspi.Text.get_character_at_offset(text_field, 1) == ord(EMOJI)


def test_erasing_emoji_removes_one_character(text_field, journal):
    """Checks: BackSpace behind the emoji removes the whole emoji and delivers `object:text-changed:delete`
    with the offset 1 and the length 1; the text is then "AB" with two characters and the caret at the offset 1.

    Why (Swing, wrapper): Swing removes both `char`s of a surrogate pair at once
    (`DefaultEditorKit.DeletePrevCharAction`), so the document shrinks by two. For ATK one character went away.

    This test changes the text of the field for good, so it must stay the last of the text tests.
    """
    _move_caret_behind_emoji(text_field, journal)
    _type_and_erase(journal)

    deleted, _ = _press(journal, KEY_BACKSPACE, "BackSpace", TEXT_DELETED, CARET_MOVED)

    assert _text(text_field) == "AB"
    assert Atspi.Text.get_character_count(text_field) == 2
    assert (deleted.detail1, deleted.detail2) == (1, 1), TEXT_DELETED
    assert Atspi.Text.get_caret_offset(text_field) == 1


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
