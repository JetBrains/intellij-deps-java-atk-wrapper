---
name: java-unit-tests
description: >-
  Write, update, review and run JUnit tests in wrapper/test/ when changing the Java layer
  (wrapper/org/GNOME/Accessibility/). Use for bug fixes, behavior changes, workaround removal,
  test coverage reviews, and requests to add or fix Java unit tests.
---

# Java unit tests

A change that touches only the Java layer comes with unit tests. The Java layer is
`wrapper/org/GNOME/Accessibility/*.java` and `AtkWrapper.java.in`; `AtkWrapper.java` is generated from the `.in`
file, so edit and test the `.in` file only.

## When a test is required

- A change in the Java layer only, with no change in `jni/`: add or change tests that cover the new behavior.
- A change in both layers: test the Java part when it can be driven without native code.
- No new test is needed for a change that does not change behavior: comments, formatting, a rename, or a refactor
  that the existing tests already cover. Run the tests anyway.
- If the behavior cannot be tested here, because it needs native code, a display, the real keyboard focus or
  Orca, do not skip it silently. Tell the user what is not covered and why.

## What to test

Test the contract that reaches the native layer or an assistive technology, not the internals of the fix:

- The native calls the wrapper makes for a Swing event: `bridge.calls()`, `bridge.calls("focusNotify")`,
  `bridge.signals(AtkSignal.…)`. Assert on the count and on the objects with `assertSame`: the native layer maps an
  `AccessibleContext` to its ATK object by identity, so a new context is a new object for Orca.
- The result of a JNI upcall, such as `AtkObject.get_accessible_parent`, called the way the native layer calls it.
- The registration of every context that is passed to the native layer: `bridge.createdResources()`.
- The cases that must not change: the neighbors of the fixed case, and the cases a workaround must leave alone.
  A workaround for menus needs a test that a popup of a button keeps its old behavior.

If the fix relies on a Swing behavior, pin that behavior in its own test named `precondition_…`. It runs no wrapper
code, and it fails when a JDK update removes the reason for the workaround.

## How the tests run

The tests run headless and without `libatk-wrapper.so`. Extend `AtkTestBase`; it installs a new
`RecordingNativeBridge` for each test in `AtkWrapper.nativeBridge` and resets the static state of the wrapper.

- Run Swing component access and mutations on the EDT through `onEdt(...)`.
- Invoke wrapper entry points from the thread used in production when thread behavior matters. Do not force JNI
  upcalls onto the EDT unless their contract requires it: the native layer calls them from its own thread, and
  many of them hop to the EDT themselves through `AtkUtil.invokeInSwingAndWait`.
- Wait for asynchronous EDT work to complete before asserting on recorded native calls. `AtkUtil.invokeInSwing`
  runs the code at once on the EDT but posts it with `invokeLater` from another thread, so the call may not have
  happened yet when the test asserts.
- `AtkUtil.invokeInSwing` and `invokeInSwingAndWait` catch and log exceptions, so an exception in the wrapper does
  not fail the test by itself:
  - A missing native call or signal does not prove that the wrapper completed successfully: an exception may have
    stopped execution before the call. For negative assertions, also verify that the relevant code path ran, for
    example that a neighboring call was recorded or that the same setup emits the call in a positive case.
  - Surface unexpected wrapper exceptions when possible. Do not treat logged exceptions as successful execution.
- Call private JNI upcalls through `invokeStatic(...)` when testing their native-facing contract.
- Prefer testing other behavior through existing entry points. Do not weaken member visibility solely to test
  implementation details. Introduce a package-private test seam only when a necessary contract cannot otherwise
  be observed. Tell the user about a new seam: `docs/fork-notes.md` lists the test seams as a permanent diff with
  upstream, and only the user edits that file.
- The native layer adds `AtkWrapper.propertyChangeListener` to every context it exposes. A test that relies on
  Swing property changes adds it to the contexts it needs and removes it in `@AfterEach`. Without it, the bridge
  records only what the code under test calls directly, which is useful when a test must not see the events of
  Swing.
- The constructor of `AtkWrapper` registers no listeners while accessibility is disabled, and it is disabled in the
  tests. Take the listener from `new AtkWrapper()` and register it in the test, see `MenuAccessibilityTest`.
- There is no display and no real focus. Call `AtkWrapper.dispatchFocusEvent(...)` instead of requesting the focus,
  and do not show windows.
- A change that adds static state to `AtkWrapper` or `AtkWrapperDisposer` also resets it in
  `AtkWrapper.forgetFocusHistory()` or `AtkWrapperDisposer.forgetRegisteredContexts()`, or the state of one test
  leaks into the next one.
- Do not restore the production bridge and do not put `libatk-wrapper.so` on the class path: the disposer thread
  releases the fake handles of a recording bridge after the test, and native code would get them.

The tree tests share `createTreeModelRoot()` from `AtkTestBase`. Put a helper there only when two test classes use
it.

## Prove that the tests protect the fix

The tests of a fix have different jobs. A regression test reproduces the original bug and fails without the fix.
A test of the neighboring cases checks that the fix leaves them alone, and a `precondition_…` test pins a Swing
behavior; both may pass without the fix, and that is correct. Know which job each new test has.

1. Run the new tests with the fix: they all pass.
2. Run them against the code without the fix: at least one regression test fails, for the reason the fix
   addresses.
3. If no test fails without the fix, the set has no regression test: add one, or change the test meant to
   reproduce the bug until it does. Do not change a test of a neighboring case or a precondition only to make it
   fail without the fix.

Run the code without the fix in isolation, never in the user's working tree. Do not use `git stash`, `git reset`
or `git checkout` for this check: the working tree can hold uncommitted or partly staged changes of the user,
which would be hidden, restored with conflicts, or mixed with the old version of the code. Copy the sources and
the tests into a temporary directory and revert the fix there, or use a separate worktree
(`git worktree add <tmp> <commit>`), and remove it afterwards.

## Run the tests

```bash
make check
```

`README.md` describes the options: a local JUnit jar, a Maven mirror, the reports directory. Without a configured
tree, compile the Java sources and the tests into a temporary directory and run the JUnit console launcher with
`-Djava.awt.headless=true`, the way `check-local` in `Makefile.am` does. Copy `AtkWrapper.java.in` to
`AtkWrapper.java` there; this works only while the `.in` file has no `@…@` placeholders for `configure`. Use the
JUnit version and the SHA-256 from `Makefile.am`. Do not leave build output in the repository.

## Document and report

- Give every new test a Javadoc in the format of the `test-javadoc` skill.
- Report to the user which tests you added or changed, the runs with and without the fix, and what stays
  untested and why.
- This skill writes and runs tests; it does not commit. Commit only when the user asks, and follow the
  `git-commit` skill then.

When preparing a commit:

- Include the tests with the change they cover. Tests for a change that is already committed go into their own
  commit with the ticket of that change; its body starts with `A test commit for <sha>.`
- Put the runs with and without the fix, and what stays untested, into `Evidence:`.
