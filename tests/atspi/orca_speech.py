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

"""Starts Orca for a test and reads what it says. A trial: `make check-orca` runs it, `make check-atspi` does not.

Orca writes every phrase that it sends to the speech synthesizer into its debug log, as a line with
`SPEECH OUTPUT: '<text>'`, also when no synthesizer works. So the tests need no audio: they read the log.

Orca opens the debug file of `--debug-file` with a block buffer and never flushes it, so a reader sees a phrase
late. Orca also runs `orca-customizations.py` from its data directory once at startup; the file written here
opens the debug file with a line buffer instead.

Written against the sources of Orca 3.36 and 49. Orca 50 and later keep neither the settings nor the log this
way, and need another way to read the speech.
"""

import os
import re
import shutil
import subprocess
import time

import gi

gi.require_version("Atspi", "2.0")
from gi.repository import Atspi  # noqa: E402

import pytest  # noqa: E402

from conftest import wait_for  # noqa: E402

# X11 keysyms, see keysymdef.h.
KEY_ESCAPE = 0xFF1B
KEY_LEFT = 0xFF51
KEY_RIGHT = 0xFF53
KEY_DOWN = 0xFF54
KEY_F10 = 0xFFC7

ORCA_STARTUP_TIMEOUT_S = 30.0
SPEECH_TIMEOUT_S = 10.0

# What Orca says first, see START_ORCA in messages.py of Orca. It is translated, so Orca runs in the C locale.
ORCA_READY_PHRASE = "Screen reader on."

SPEECH_MARKER = "SPEECH OUTPUT: '"
# The text ends at a quote that is the last character of the line or is followed by the voice: Orca 3.36
# appends the voice directly, Orca 49 after a space. A text with a quote before a space is cut there.
_SPEECH_TEXT = re.compile(r"(.*?)'(?:$|[ {<\[])")

# Orca 3.36 does not catch an exception of this file, and its startup would fail, so the file catches it.
ORCA_CUSTOMIZATIONS = '''\
# Written by tests/atspi/orca_speech.py: makes Orca log what it says to a line-buffered file.
import os
import traceback

try:
    from orca import debug

    debug.debugLevel = debug.LEVEL_INFO
    debug.debugFile = open(os.environ["JAW_ORCA_DEBUG_FILE"], "a", buffering=1, encoding="utf-8")
except Exception:
    traceback.print_exc()
'''


def press(keysym):
    """Presses and releases a key through the X server, as a user does, so the focused window gets it."""
    if not Atspi.generate_keyboard_event(keysym, None, Atspi.KeySynthType.SYM):
        pytest.fail("the AT-SPI registry did not generate the key 0x%x" % keysym)


class Orca:
    """A running Orca and the phrases it has said, in their order."""

    def __init__(self, process, debug_path, log_dir):
        self.process = process
        self.debug_path = debug_path
        self.log_dir = log_dir
        self._offset = 0
        self._phrases = []

    def _read(self):
        """Reads the complete lines that Orca added to its debug log since the last call."""
        try:
            with open(self.debug_path, "rb") as log:
                log.seek(self._offset)
                data = log.read()
        except FileNotFoundError:
            # Orca has not run orca-customizations.py yet.
            return
        end = data.rfind(b"\n")
        if end < 0:
            return
        self._offset += end + 1
        for line in data[:end].decode("utf-8", errors="replace").split("\n"):
            start = line.find(SPEECH_MARKER)
            if start < 0:
                continue
            rest = line[start + len(SPEECH_MARKER):]
            match = _SPEECH_TEXT.match(rest)
            self._phrases.append(match.group(1) if match else rest)

    def phrases(self):
        self._read()
        return list(self._phrases)

    def mark(self):
        """Returns a position in the speech; `since` and `wait_for_phrase` then see only what comes after it."""
        return len(self.phrases())

    def since(self, mark):
        return self.phrases()[mark:]

    def _alive(self):
        if self.process.poll() is not None:
            pytest.fail("Orca exited with code %d, see orca-output.log" % self.process.returncode)

    def wait_for_phrase(self, text, mark=0, what=None, timeout=SPEECH_TIMEOUT_S, journal=None):
        """Waits until Orca says a phrase that contains `text`, in any letter case, after `mark`."""
        wanted = text.lower()

        def said():
            self._alive()
            return [phrase for phrase in self.since(mark) if wanted in phrase.lower()]

        def description():
            return "%s; Orca said since then: %r" % (what or "Orca to say '%s'" % text, self.since(mark))

        return wait_for(said, _Lazy(description), timeout=timeout, journal=journal)

    def wait_until_quiet(self, quiet=1.0, timeout=10.0):
        """Waits until Orca has said nothing for `quiet` seconds, at most `timeout` seconds.

        After its first phrase Orca still starts its event listeners and announces the active window. A key
        pressed before that is missed or is announced together with the startup phrases.
        """
        state = {"count": len(self.phrases()), "since": time.monotonic()}
        deadline = time.monotonic() + timeout

        def quiet_or_late():
            self._alive()
            now = time.monotonic()
            count = len(self.phrases())
            if count != state["count"]:
                state["count"] = count
                state["since"] = now
            return now - state["since"] >= quiet or now >= deadline

        wait_for(quiet_or_late, "Orca to become quiet", timeout=timeout + 5.0)

    def stop(self):
        self.process.terminate()
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        with open(os.path.join(self.log_dir, "orca-speech.log"), "w") as log:
            log.write("\n".join(self.phrases()) + "\n")


class _Lazy:
    """Builds the text of a failure message only when the message is printed."""

    def __init__(self, text):
        self._text = text

    def __str__(self):
        return self._text()


def _write_version(executable, log_dir):
    """Records the version of Orca: what it says, and what this module can rely on, depends on it."""
    try:
        version = subprocess.run(
            [executable, "--version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20
        ).stdout.decode("utf-8", errors="replace")
    except (OSError, subprocess.TimeoutExpired) as error:
        version = "orca --version failed: %s\n" % error
    with open(os.path.join(log_dir, "orca-version.log"), "w") as log:
        log.write(version)


def start_orca(log_dir):
    """Starts Orca with settings of its own in `log_dir` and waits until it says its first phrase."""
    executable = shutil.which("orca")
    if executable is None:
        pytest.fail("orca not found: these tests need the package `orca`")
    _write_version(executable, log_dir)

    # Orca keeps its settings in $XDG_DATA_HOME/orca. A new directory gives the default settings, with speech
    # on, and keeps the settings of the user out of the test.
    data_home = os.path.join(log_dir, "orca-data")
    settings_dir = os.path.join(data_home, "orca")
    os.makedirs(settings_dir)
    with open(os.path.join(settings_dir, "orca-customizations.py"), "w") as customizations:
        customizations.write(ORCA_CUSTOMIZATIONS)

    debug_path = os.path.join(log_dir, "orca-debug.log")
    environment = dict(os.environ)
    environment.update(
        XDG_DATA_HOME=data_home,
        JAW_ORCA_DEBUG_FILE=debug_path,
        # The phrases must be the English ones.
        LANG="C.UTF-8",
        LC_ALL="C.UTF-8",
    )
    environment.pop("LANGUAGE", None)

    with open(os.path.join(log_dir, "orca-output.log"), "wb") as output:
        process = subprocess.Popen(
            [executable], stdout=output, stderr=subprocess.STDOUT, cwd=log_dir, env=environment
        )
    orca = Orca(process, debug_path, log_dir)
    try:
        orca.wait_for_phrase(
            ORCA_READY_PHRASE,
            what="Orca to start and say '%s'; orca-output.log and orca-debug.log say why it did not"
            % ORCA_READY_PHRASE,
            timeout=ORCA_STARTUP_TIMEOUT_S,
        )
        orca.wait_until_quiet()
    except BaseException:
        orca.stop()
        raise
    return orca
