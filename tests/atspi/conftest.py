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

"""The harness of the AT-SPI integration tests.

The tests start AtspiTestApp with the wrapper as its assistive technology and look at it through the AT-SPI
bus with libatspi, the way a screen reader does. run.sh provides the X server, the session bus and the
environment variables read below; start the tests with `make check-atspi`, not with pytest directly.

The code runs on Ubuntu 20.04: Python 3.8, pytest 4.6, at-spi2-core 2.36.

Writing a test:
- Take `app` for the running application and `journal` for the events. The journal starts before the
  application, so it holds the startup events as well; `journal.find` returns the first match of the whole run.
- Wait with `wait_for`, which runs the GLib main context: libatspi delivers events and updates its cache only
  while it runs. Never use `time.sleep`.
- Match the source of an event, not only its type: role and name, or the object itself when identity matters.
- A test that checks that an event did not come must first show that its action happened.
- Send keys with `press`. Wait for the event of every key before the next one: the events of two keys can
  otherwise arrive in one batch, and `journal.find(..., start=...)` then takes the event of the earlier key.
- The tests of a file share one application, and the last tests of test_atspi.py read its output: keep them
  last.
"""

import os
import subprocess
import time

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi, GLib  # noqa: E402

import pytest  # noqa: E402

# The title of the window of AtspiTestApp, see AtspiTestApp.FRAME_TITLE.
FRAME_NAME = "ATK wrapper test"

# The assistive technology class that the JDK loads, as in accessibility.properties.
WRAPPER_CLASS = "org.GNOME.Accessibility.AtkWrapper"

# How long a synchronous D-Bus call may take, and how long the first call to a new application may take, in
# milliseconds. A hung application then fails a test instead of hanging the build.
CALL_TIMEOUT_MS = 5000
STARTUP_CALL_TIMEOUT_MS = 15000

STARTUP_TIMEOUT_S = 30.0
EVENT_TIMEOUT_S = 10.0

# What the sanitizers print for an error in a library built with configure --enable-sanitizers.
SANITIZER_MESSAGES = (
    "ERROR: AddressSanitizer",
    "runtime error:",
)

EVENT_TYPES = (
    "window:create",
    "window:activate",
    "window:deactivate",
    "object:state-changed:active",
    "object:state-changed:focused",
    "object:active-descendant-changed",
    "object:property-change:accessible-name",
    "object:text-caret-moved",
    "object:text-changed:insert",
    "object:text-changed:delete",
    "object:text-selection-changed",
)

# X11 keysyms for `press`, see keysymdef.h.
KEY_BACKSPACE = 0xFF08
KEY_ESCAPE = 0xFF1B
KEY_HOME = 0xFF50
KEY_LEFT = 0xFF51
KEY_RIGHT = 0xFF53
KEY_DOWN = 0xFF54
KEY_END = 0xFF57
KEY_F2 = 0xFFBF
KEY_F10 = 0xFFC7
KEY_X = 0x0078


def _environment(name):
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(name + " is not set; run the tests with `make check-atspi`")
    return value


class RecordedEvent:
    """An AT-SPI event with the role and the name of its source, read when the event arrived."""

    def __init__(self, event):
        self.type = event.type
        self.source = event.source
        self.detail1 = event.detail1
        self.detail2 = event.detail2
        self.event = event
        try:
            self.role = event.source.get_role()
            self.name = event.source.get_name()
        except GLib.Error as error:
            # The source can be gone already, for example a window that was closed.
            self.role = None
            self.name = "<%s>" % error.message

    def __str__(self):
        role = self.role.value_nick if self.role is not None else "?"
        return "%s  source=%s '%s'  detail1=%d detail2=%d" % (self.type, role, self.name, self.detail1, self.detail2)


class EventJournal:
    """Records every event of EVENT_TYPES from the moment it is created, in the order of arrival."""

    def __init__(self):
        self.events = []
        self._listener = Atspi.EventListener.new(self._on_event)
        for event_type in EVENT_TYPES:
            self._listener.register(event_type)

    def _on_event(self, event, *_user_data):
        self.events.append(RecordedEvent(event))

    def close(self):
        for event_type in EVENT_TYPES:
            self._listener.deregister(event_type)

    def find(self, event_type, role=None, name=None, detail1=None, start=0):
        """Returns the first recorded event that matches, or None. `start` skips the events before an action:
        pass the value that `len(journal.events)` had before it."""
        for event in self.events[start:]:
            if event.type != event_type:
                continue
            if role is not None and event.role != role:
                continue
            if name is not None and event.name != name:
                continue
            if detail1 is not None and event.detail1 != detail1:
                continue
            return event
        return None

    def dump(self):
        return "\n".join(str(event) for event in self.events) or "(no events)"


def wait_for(predicate, what, timeout=EVENT_TIMEOUT_S, journal=None):
    """Runs the GLib main context until `predicate()` returns a true value, and returns that value.

    libatspi delivers events and updates its cache only while the main context runs, so a test must wait here
    and never with time.sleep. Fails the test after `timeout` seconds, with the recorded events in the message.
    """
    context = GLib.MainContext.default()
    # Wakes the context up, so that a blocking iteration returns when nothing arrives.
    tick = GLib.timeout_add(50, lambda: True)
    deadline = time.monotonic() + timeout
    try:
        while True:
            result = predicate()
            if result:
                return result
            if time.monotonic() >= deadline:
                message = "timed out after %.0f s waiting for %s" % (timeout, what)
                if journal is not None:
                    message += "\nrecorded events:\n" + journal.dump()
                pytest.fail(message)
            context.iteration(True)
    finally:
        GLib.source_remove(tick)


def find_frame():
    """Returns (application, frame) of AtspiTestApp from the AT-SPI desktop, or None while it is not there."""
    desktop = Atspi.get_desktop(0)
    try:
        for i in range(desktop.get_child_count()):
            application = desktop.get_child_at_index(i)
            if application is None:
                continue
            for j in range(application.get_child_count()):
                frame = application.get_child_at_index(j)
                if frame is not None and frame.get_name() == FRAME_NAME:
                    return application, frame
    except GLib.Error:
        # Another application on the bus went away during the walk; the next attempt sees the new state.
        return None
    return None


def find_descendant(root, name, depth=10):
    """Returns the first accessible with the name `name` below `root`, in depth-first order, or None."""
    if depth == 0:
        return None
    try:
        for i in range(root.get_child_count()):
            child = root.get_child_at_index(i)
            if child is None:
                continue
            if child.get_name() == name:
                return child
            found = find_descendant(child, name, depth - 1)
            if found is not None:
                return found
    except GLib.Error:
        # A component went away during the walk; the next attempt sees the new state.
        return None
    return None


def press(keysym):
    """Presses and releases a key through the X server, as a user does, so the focused window gets it."""
    if not Atspi.generate_keyboard_event(keysym, None, Atspi.KeySynthType.SYM):
        pytest.fail("the AT-SPI registry did not generate the key 0x%x" % keysym)


class RunningApp:
    def __init__(self, process, application, frame, output_path):
        self.process = process
        self.application = application
        self.frame = frame
        # Everything the JVM and the native layer printed: -Xcheck:jni, the sanitizers, g_debug, Java logging.
        self.output_path = output_path

    def output_lines_with(self, messages):
        """Returns the lines of the output of the application that contain one of `messages`."""
        with open(self.output_path, errors="replace") as output:
            return [line.rstrip("\n") for line in output if any(message in line for message in messages)]


def application_environment():
    """The environment of the application: with the sanitizer runtime when the library was built with it."""
    environment = dict(os.environ)
    preload = os.environ.get("ATSPI_SANITIZER_PRELOAD")
    if preload:
        # The JVM is not built with AddressSanitizer, so its runtime must be the first library of the process.
        environment["LD_PRELOAD"] = preload
        # handle_segv=0: the JVM uses SIGSEGV itself. detect_leaks=0: the JVM never frees much of its memory.
        environment["ASAN_OPTIONS"] = "handle_segv=0:detect_leaks=0"
        environment["UBSAN_OPTIONS"] = "print_stacktrace=1"
        # Without it GLib hands out memory from its own pools, where AddressSanitizer sees no errors.
        environment["G_SLICE"] = "always-malloc"
    return environment


@pytest.fixture(scope="session")
def journal():
    """The events of the whole run. Created before the application starts, so that it sees its first events."""
    Atspi.init()
    Atspi.set_timeout(CALL_TIMEOUT_MS, STARTUP_CALL_TIMEOUT_MS)
    events = EventJournal()
    yield events
    events.close()
    with open(os.path.join(_environment("ATSPI_LOG_DIR"), "events.log"), "w") as log:
        log.write(events.dump() + "\n")


@pytest.fixture(scope="module")
def app(journal):
    """Starts AtspiTestApp with the wrapper and waits until its window is on the AT-SPI desktop."""
    log_dir = _environment("ATSPI_LOG_DIR")
    command = [
        _environment("ATSPI_JAVA"),
        "-cp", _environment("ATSPI_CLASSPATH"),
        # Always on: the tests also check how the native layer uses JNI with the real threads of an application.
        "-Xcheck:jni",
        "-Djavax.accessibility.assistive_technologies=" + WRAPPER_CLASS,
        "AtspiTestApp",
    ]
    # The wrapper writes jaw_log.txt to the working directory when JAW_DEBUG is set.
    output_path = os.path.join(log_dir, "app-output.log")
    with open(output_path, "wb") as output:
        process = subprocess.Popen(
            command, stdout=output, stderr=subprocess.STDOUT, cwd=log_dir, env=application_environment()
        )
        try:
            def frame_or_exit():
                if process.poll() is not None:
                    pytest.fail("the application exited with code %d, see app-output.log" % process.returncode)
                return find_frame()

            application, frame = wait_for(
                frame_or_exit,
                "the window '%s' on the AT-SPI desktop; if the wrapper did not start, app-output.log says why"
                % FRAME_NAME,
                timeout=STARTUP_TIMEOUT_S,
                journal=journal,
            )
            yield RunningApp(process, application, frame, output_path)
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
