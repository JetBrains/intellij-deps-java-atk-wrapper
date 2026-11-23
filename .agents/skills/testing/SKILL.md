---
name: testing
description: >-
  Decide whether a change to the Java ATK Wrapper needs tests and at which layer, and prove that the tests protect
  the fix. Use whenever a change needs tests, or tests are written, reviewed or run, before the skill of a layer:
  java-unit-tests, native-unit-tests or atspi-integration-tests.
---

# Testing

The Java ATK Wrapper (JAW) has three test layers. This skill holds what is the same for all of them: when a test is
required, which layer gets it, what a test asserts, how to prove that it protects the fix, how to run the tests and
what to report. The skill of each layer describes its infrastructure and how to write a test there:

- `java-unit-tests`: JUnit tests in `wrapper/test/`;
- `native-unit-tests`: GLib tests in `jni/tests/`;
- `atspi-integration-tests`: pytest tests in `tests/atspi/`, and the trial tests of what Orca says.

`test-docs` describes how a test is documented in each of them.

## When a test is required

- A change in behavior comes with tests: a bug fix, a new behavior, the removal of a workaround.
- No new test is needed for a change that does not change behavior: comments, formatting, a rename, or a refactor
  that the existing tests already cover. Run the tests anyway.
- If a behavior cannot be tested at any layer, do not skip it silently. Tell the user what is not covered and why.

## Choose the layer

| Layer | What it observes | What it needs |
|---|---|---|
| Java unit | How the Java layer translates Swing events into the calls of `NativeBridge`, the results of the JNI upcalls, the registration of contexts with `AtkWrapperDisposer`, the static state of the Java layer | Nothing: any OS, headless, no `libatk-wrapper.so` |
| Native unit | The ATK objects that the native layer builds for Swing components (names, roles, states, parents, interfaces), JNI contracts, resource lifetime, `-Xcheck:jni`, AddressSanitizer and UBSan | Linux, ATK, `libjvm.so` |
| AT-SPI integration | The start of the wrapper in an application, the application and its windows on the AT-SPI desktop, real window and focus events, the calls between the GLib thread and the EDT, the delivery over D-Bus | Linux, an X server, a session bus, a window manager, Python with libatspi |
| Orca (trial) | Parts of what Orca says after a key, see `atspi-integration-tests` | What AT-SPI needs, and the package `orca` |
| Manual | The rest of what Orca says, the IDE itself, Wayland, braille | Orca |

- Test a contract at the lowest layer that observes the regression. A Java unit test runs everywhere in seconds;
  an AT-SPI test needs a desktop session and can be flaky.
- A change in both `wrapper/` and `jni/` is tested in parts, each at the layer where it lives.
- Add a test at a higher layer only for what the lower layers cannot see, and do not repeat a lower-level test
  there to grow coverage. That the wrapper emits a signal is a Java unit test; that the signal reaches the bus is
  an AT-SPI test.
- An AT-SPI event does not prove what Orca says. When a fix is about an announcement, say whether a trial Orca
  test covers it, and which part remains a manual check.

## What a test asserts

Assert on the contract at the boundary of the layer, not on the internals of the fix: the calls that reach the
native layer, the ATK object that the AT-SPI bridge reads, the event that arrives over the bus.

- **Identity.** The native layer keeps one ATK object per `AccessibleContext`, so a new context is a new object for
  Orca. Where identity matters, compare objects by identity (`assertSame`, pointers), not by name.
- **Absence.** A missing call, signal or event does not prove that the code ran: the Java layer catches and logs
  the exceptions of the code it runs on the EDT, and an event can be late. Prove that the path ran: a neighboring
  call was recorded, or the same setup produces the call in a positive case.
- **Neighbors.** Test the cases the fix must leave alone. A workaround for menus needs a test that a popup of a
  button keeps its old behavior.
- **Preconditions.** If a fix relies on a behavior of Swing, the JDK or ATK, pin that behavior in its own test named
  `precondition_…`. It runs no wrapper code, and it fails when an update removes the reason for the workaround.
- **Test seams.** Do not weaken visibility only to test an implementation detail. Add a test seam only when a
  necessary contract cannot be observed otherwise, and tell the user: `docs/fork-notes.md` lists the test seams as
  a permanent diff with upstream, and only the user edits that file.

## Prove that the tests protect the fix

The tests of a fix have different jobs. A regression test reproduces the original bug and fails without the fix.
A test of a neighboring case and a `precondition_…` test may pass without the fix, and that is correct. Know which
job each new test has.

1. Run the new tests with the fix: they all pass.
2. Run them against the code without the fix: at least one regression test fails, for the reason the fix
   addresses. Read the failure. A crash, a failed start of the application or a timeout of a fixture is not that
   reason.
3. If no test fails without the fix, the set has no regression test: add one, or change the test meant to
   reproduce the bug until it does. Do not change a test of a neighboring case or a precondition only to make it
   fail without the fix.

Run the code without the fix in a separate worktree, never in the user's working tree. Do not use `git stash`,
`git reset` or `git checkout` for this: the working tree can hold uncommitted or partly staged changes of the user.

```bash
base=$(mktemp -d)/base
git worktree add --detach "$base" <commit without the fix>
cp <new and changed test files> "$base"/<same paths>
# build and run the tests in "$base"
git worktree remove --force "$base"
```

The commit without the fix is `HEAD` when the fix is not committed yet, and `<fix>^` when it is. If the tests
need other changes of the same work, such as a new fixture, copy them too, and revert only the fix. The native and
the AT-SPI tests need a full build in the worktree (`./autogen.sh && make`). The Java unit tests can also be
compiled into a temporary directory, see `java-unit-tests`.

## Run the tests

| Command | What runs | Results |
|---|---|---|
| `make check` | The Java unit tests, and the native unit tests where `configure` found what they need | `test-reports/TEST-junit-*.xml`, `test-reports/TEST-native-*.xml`, `jni/tests/*.log` |
| `make check JAW_TEST_JNI_WARNINGS=fail` | The same; a message of `-Xcheck:jni` fails the test that caused it | |
| `make check-atspi` | The AT-SPI integration tests, which `make check` does not run | `test-reports/TEST-atspi.xml`, `tests/atspi/logs/` |
| `make check-orca` | The trial tests of what Orca says | `test-reports/TEST-atspi-orca.xml`, `tests/atspi/logs-orca/` |
| `./autogen.sh --enable-sanitizers && make && make check` | The native unit tests with AddressSanitizer and UBSan; `make check-atspi` runs the application with them too | as above |

- `configure` turns off a layer whose requirements are missing, with a warning, and `make check` then prints
  `Skipping`. A skipped layer is not a passed one. `--enable-java-tests=yes` and `--enable-native-tests=yes` make
  missing requirements an error. On macOS only the Java unit tests run.
- Build with sanitizers in a separate copy of the sources, never in the user's tree, and never install or package
  that build.
- Run the tests of the change first, then the full suite of each layer it touches.
- Do not leave build output in the repository outside the directories the build uses.

## Report

Tell the user:

- which tests were added or changed, and the job of each: regression, neighbor or precondition;
- the commands, and the results with and without the fix;
- which layers did not run, and why;
- what stays untested, and why, including the checks that remain manual with Orca.

Every new test says what it checks and why, as `test-docs` describes for its language.

These skills write and run tests; they do not commit. Commit only when the user asks, and follow `git-commit`
then:

- The tests go into the commit of the change they cover. Tests for a change that is already committed go into
  their own commit with the ticket of that change; its body starts with `A test commit for <sha>.`
- The body names the tests that protect the change and what stays untested. It describes the change, not the
  session: no runs of the session, no machine, no "not run here".
