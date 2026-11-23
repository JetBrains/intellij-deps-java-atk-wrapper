---
name: java-unit-tests
description: >-
  Write, review and run the JUnit tests in wrapper/test/ for the Java layer (wrapper/org/GNOME/Accessibility/):
  Swing event translation, JNI upcalls, focus handling, context registration. Use together with the testing skill
  when changing the Java layer, and for requests to add, fix or explain Java unit tests.
---

# Java unit tests

The `testing` skill says when a test is required, which layer gets it, what a test asserts and how to prove that
it protects the fix. Follow it first; this skill describes the Java layer.

The Java layer is `wrapper/org/GNOME/Accessibility/*.java` and `AtkWrapper.java.in`. `AtkWrapper.java` is generated
from the `.in` file, so edit and test the `.in` file only. The tests are in `wrapper/test/org/GNOME/Accessibility/`,
in the package of the wrapper, so they reach its package-private members.

## How the tests work

The tests run headless and without `libatk-wrapper.so`. Every native call that the Java layer makes after
initialization goes through `NativeBridge`, and the static field `AtkWrapper.nativeBridge` holds the
implementation. A test replaces it with a `RecordingNativeBridge`, which records the calls instead of making them.

- `AtkTestBase`: the base class of every test class. Before each test it installs a new `RecordingNativeBridge`
  and resets the static state of the wrapper with `AtkWrapper.forgetFocusHistory()` and
  `AtkWrapperDisposer.forgetRegisteredContexts()`. It gives `onEdt(...)`, which runs code on the EDT and rethrows
  its exceptions, `invokeStatic(...)`, which calls a private JNI upcall the way the native layer does, and
  `createTreeModelRoot()`, the tree model of the tree tests.
- `RecordingNativeBridge`: `calls()`, `calls("focusNotify")`, `signals(AtkSignal.…)` for the `emitSignal` calls
  with one id, `createdResources()` and `releasedResources()` for the contexts registered with the native layer,
  `clear()` to forget what the setup caused.
- `TestEnvironmentTest`: fails with a clear message when the tests run without `-Djava.awt.headless=true` or with
  the native library on the class path.

Examples to follow: `MenuAccessibilityTest` (upcalls, a listener of the `AtkWrapper` constructor, neighboring
cases), `JTreeSignalsTest` (signals from Swing property changes), `JTreeContextCacheManagerTest` (a cache, a
`precondition_…` test).

## What can be tested

- The signals of `AtkWrapper.propertyChangeListener` for Swing property changes: text caret, insert and delete,
  children, selection, visible data, value, name, description, table changes, `objectStateChange`.
- The focus logic of `AtkWrapper.dispatchFocusEvent(...)`: repeated focus, menus and the root pane, active
  descendants.
- The JNI upcalls of `AtkObject`, `AtkText`, `AtkTable`, `AtkComponent`, `AtkAction`, `AtkValue`, `AtkSelection` and
  the other `Atk*` classes, through `invokeStatic(...)`.
- The registration of every context passed to the native layer: `bridge.createdResources()`.
- The listeners that the `AtkWrapper` constructor creates, such as `menuSelectionListener`, and helpers such as
  `JTreeContextCacheManager`.

Not here: the ATK objects of the native layer, real windows and focus, the GLib thread, D-Bus. See `testing`.

## Writing a test

- Extend `AtkTestBase`. Run Swing component access and mutations on the EDT through `onEdt(...)`.
- Invoke a wrapper entry point from the thread used in production when thread behavior matters. Do not force JNI
  upcalls onto the EDT unless their contract requires it: the native layer calls them from its own thread, and
  many of them hop to the EDT themselves through `AtkUtil.invokeInSwingAndWait`.
- `AtkUtil.invokeInSwing` runs the code at once on the EDT but posts it with `invokeLater` from another thread.
  Wait for that work, for example with an empty `onEdt(...)`, before asserting on recorded calls.
- `AtkUtil.invokeInSwing` and `invokeInSwingAndWait` catch and log exceptions, so an exception in the wrapper does
  not fail the test by itself. A missing call proves nothing without a proof that the path ran, see `testing`.
- Compare contexts with `assertSame`: the native layer maps an `AccessibleContext` to its ATK object by identity.
- The native layer adds `AtkWrapper.propertyChangeListener` to every context it exposes. A test that relies on
  Swing property changes adds it to the contexts it needs and removes it in `@AfterEach`. Without it, the bridge
  records only what the code under test calls directly, ````which is useful when a test must not see the events of
  Swing.
- The constructor of `AtkWrapper` registers no listeners while accessibility is disabled, and it is disabled in the
  tests. Take the listener from `onEdt(AtkWrapper::new)` and register it in the test.
- There is no display and no real focus. Call `AtkWrapper.dispatchFocusEvent(...)` instead of requesting the focus,
  and do not show windows.
- A change that adds static state to `AtkWrapper` or `AtkWrapperDisposer` also resets it in
  `AtkWrapper.forgetFocusHistory()` or `AtkWrapperDisposer.forgetRegisteredContexts()`, or the state of one test
  leaks into the next one.
- Do not restore the production bridge and do not put `libatk-wrapper.so` on the class path: the disposer thread
  releases the fake handles of a recording bridge after the test, and native code would get them.
- Put a helper into `AtkTestBase` only when two test classes use it.
- Document every test as `test-docs` describes for Java.

## Run the tests

`make check` runs them, on any OS. `README.md` describes the options: `JUNIT_CONSOLE_JAR` for a local JUnit jar,
`MAVEN_REPOSITORY_URL` for a mirror, `JUNIT_REPORTS_DIR` for the reports.

Without a configured tree, or for a worktree without the fix, compile into a temporary directory the way
`check-local` in `Makefile.am` does. The JUnit jar is the one that `make check` downloaded into `test-lib/` of the
main tree; a worktree has none of its own. If it is missing, run `make check` once in the main tree, or download
the jar with the version and the SHA-256 from `Makefile.am`. Copying `AtkWrapper.java.in` works only while it has
no `@…@` placeholders for `configure`.

```bash
# test-lib/ of the main tree, also from a worktree.
junit=$(ls "$(git rev-parse --path-format=absolute --git-common-dir)"/../test-lib/junit-platform-console-standalone-*.jar)
tmp=$(mktemp -d)
cp wrapper/org/GNOME/Accessibility/AtkWrapper.java.in "$tmp/AtkWrapper.java"
javac -cp "$junit" -d "$tmp/classes" "$tmp/AtkWrapper.java" \
    $(find wrapper/org/GNOME/Accessibility wrapper/test -name '*.java' ! -name AtkWrapper.java)
java -Djava.awt.headless=true -jar "$junit" execute --disable-banner \
    --class-path "$tmp/classes" --scan-class-path "$tmp/classes"
# One class: --select-class org.GNOME.Accessibility.MenuAccessibilityTest instead of --scan-class-path.
rm -rf "$tmp"
```

A run, also `make check`, logs `SEVERE: Error during ATK accessibility initialization` with an
`IOException: Native library not found in resources` and then `AtkWrapper not initialized due to disabled
accessibility`. That is expected: the static initializer of `AtkWrapper` looks for `libatk-wrapper.so`, which the
tests leave out on purpose (`TestEnvironmentTest` checks it). The result is the summary at the end of the run.
