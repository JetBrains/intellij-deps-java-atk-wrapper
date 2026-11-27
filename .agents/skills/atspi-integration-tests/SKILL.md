---
name: atspi-integration-tests
description: >-
  Write, review and run the pytest tests in tests/atspi/ that start a real Swing application with the wrapper and
  check it through libatspi: registration on the AT-SPI desktop, window and focus events, GLib-to-EDT
  interaction, D-Bus delivery, and the trial tests of what Orca says. Use together with the testing skill.
---

# AT-SPI integration tests

The `testing` skill says when a test is required, which layer gets it, what a test asserts and how to prove that
it protects the fix. Follow it first; this skill describes the AT-SPI layer. It is the slowest layer: add a test
here only for what the unit tests in `wrapper/test/` and `jni/tests/` cannot see.

## How the tests work

`tests/atspi/run.sh` starts an X server (`xvfb-run`), a session bus with the AT-SPI bus (`dbus-run-session`) and a
window manager (`openbox`), marks accessibility as enabled and runs pytest. `conftest.py` starts
`app/AtspiTestApp.java` with the jar of the wrapper as its assistive technology, as in production, and with
`-Xcheck:jni`. The tests look at the application through PyGObject `Atspi`, the way a screen reader does.

The code must run on Ubuntu 20.04: Python 3.8, pytest 4.6, at-spi2-core 2.36. Read the docstring of `conftest.py`
before writing a test.

- `app` (scope `module`): the running application, with `application`, `frame` and `output_lines_with(...)` for
  what the JVM and the native layer printed.
- `journal` (scope `session`): every event of `EVENT_TYPES` from before the start of the application, as
  `RecordedEvent` with the role and the name of its source. `journal.find(...)` returns the first match of the
  whole run; a test that needs the events after its action keeps `len(journal.events)` before it and passes it
  as `start`.
- `wait_for(predicate, what, journal=...)`: runs the GLib main context until the predicate holds, and fails with
  the recorded events.
- `window_is_active(frame)`: whether the window has the state `active`, so that the keys of a test go to it. Wait
  for it before the first key; the wrapper delivers no `window:activate`.
- `has_focus(accessible)`: whether a component has the state `focused`. A menu that closes gives the focus back
  to the component that had it before; wait for that before a test that moves the focus.
- `press(keysym)`: sends a real key through the X server, with the `KEY_…` keysyms. Wait for the event of a key
  before the next key, or the events of two keys arrive together and a test takes the wrong one.
- `FRAME_NAME`, `find_frame()`, `find_descendant(root, name)`, `application_environment()`.
- `start_application(arguments, stdin, subdirectory)`: an application of its own for a test that cannot share
  `app`, for example a test of how the application exits. Its log goes into the subdirectory of the log directory.

Tests are in `test_atspi.py`. Its last test reads the output of the application for sanitizer messages and
checks that the application still runs: keep it last.

## What can be tested

- The application and its windows on the AT-SPI desktop: toolkit name, roles, names, parents.
- Events delivered over the bus: focus, active descendant, property changes, text changes.
- Text offsets as a client sees them: the answer to a request and the event of the same change together.
- What needs the real threads of an application: the GLib main loop, the EDT, the AWT event threads.
- `-Xcheck:jni` and the sanitizers with those threads.

## Writing a test

- Wait with `wait_for`, never with `time.sleep`: libatspi delivers events and updates its cache only while the
  main context runs.
- A new event type goes into `EVENT_TYPES` of `conftest.py`, or the journal does not record it.
- Match the source of an event, not only its type: role and name, or the object itself when identity matters.
- Drive the application the way a user does: through the window manager, keys or the application itself. Do not
  call the wrapper directly to produce an event: the test exists to check the path from AWT to the bus.
- A component the test needs goes into `AtspiTestApp` with a stable name; a rename there needs the same change in
  the tests.
- A test that checks that an event did not come first shows that its action happened, and waits a bounded time.
- Tell a failure of the setup (the application did not start, no bus, a missing package) apart from a failure of
  the assertion, in the message of the test.
- Document every test as `test-docs` describes for Python.

## What Orca says (a trial)

`test_orca.py` and `orca_speech.py` are a trial of tests of what Orca says. `make check-orca` runs them, and
`make check-atspi` does not. The fixture `orca` starts Orca with settings of its own after the window is active;
`orca_speech.py` reads the phrases from the debug log of Orca, so no audio is needed.

- `press(keysym)` sends a real key through the X server.
- `orca.mark()` before an action, then `orca.wait_for_phrase(text, mark=...)`: waits for a phrase after the mark
  that contains `text`, in any letter case.
- Wait for the event on the bus first, then for the phrase: a missing event is a failure of the wrapper or of the
  key, a missing phrase after the event is one of Orca.
- Assert on parts of a phrase, not on whole phrases: the wording differs between the versions of Orca.
- The module relies on Orca 3.36 to 50.

## Run the tests

```bash
make check-atspi                       # test_atspi.py
make check-orca                        # test_orca.py, also needs the package orca
```

The header of `run.sh` lists the packages and the environment variables, such as `JAW_DEBUG` and `ATSPI_TESTS`.
After `configure --enable-sanitizers` the application runs with the AddressSanitizer runtime preloaded.

The report is `test-reports/TEST-atspi.xml` (`TEST-atspi-orca.xml` for Orca). The logs of a run are in
`tests/atspi/logs/` (`logs-orca/`): `app-output.log`, `events.log`, and for Orca
`orca-speech.log`, `orca-debug.log`, `orca-version.log`.
