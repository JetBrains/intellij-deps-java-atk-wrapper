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

EVENT_TYPES = (
    "window:create",
    "window:activate",
    "window:deactivate",
    "object:state-changed:active",
    "object:state-changed:focused",
    "object:active-descendant-changed",
    "object:property-change:accessible-name",
)


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

    def find(self, event_type, role=None, name=None, detail1=None):
        """Returns the first recorded event that matches, or None."""
        for event in self.events:
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
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, cwd=log_dir)
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
