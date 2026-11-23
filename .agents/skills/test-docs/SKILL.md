---
name: test-docs
description: >-
  Write or review the documentation of tests in wrapper/test/, jni/tests/ and tests/atspi/: what a test checks,
  why, and when to revisit it. Use when adding tests, changing assertions, documenting or explaining tests,
  changing wrapper behavior, or removing a workaround that existing tests document.
---

# Test documentation

The tests here check more than an API: they pin how Swing, the wrapper, ATK, the AT-SPI bus and Orca fit
together. A test name rarely says why a workaround exists, so every test says what it checks and why. The model is
the same in every language; the last section says how it looks in Java, Python and C.

```java
/**
 * Checks: the {@code get_accessible_parent} upcall returns the owning {@code JMenu} for an item in its popup,
 * rather than the {@code JPopupMenu}.
 * <p>
 * Why (Swing, Orca): Swing initially reports the {@code JPopupMenu} as the item's parent.
 * Calling {@code AccessibleJMenu.getAccessibleChild} changes the parent to the {@code JMenu} as a side effect.
 * Orca announces newly encountered ancestors when focus changes. Without the menu in the ancestor chain,
 * Orca omits the menu name.
 * <p>
 * May change when: Swing assigns the menu as the parent when the item is added (the workaround becomes
 * unnecessary, but the test should still pass), or Orca changes how it derives focus announcements
 * (the original motivation may no longer apply).
 */
```

## Before writing

Establish the facts first, then write.

- Read the test, the code it exercises, and any related issue or nearby tests before writing. The commit that
  added the test and the commit of the fix it covers often record the reason.
- Describe what the test actually asserts, not what its name suggests it asserts. If the two differ, say so to
  the author instead of documenting the name.
- Do not infer the purpose of a test solely from its implementation details. This matters most for old
  regression tests, where the reason the test exists is not visible in its code.

## Checks

The contract the test protects, as observable behavior.

- Do not retell the steps of the test. `Checks: the wrapper reports the updated accessible name after a property
  change`, not `Checks: creates a JMenu, adds an item, and calls get_accessible_parent`.
- Name the condition under which the result is correct. `Checks: the method returns null` is not enough: say
  when null is the right answer.
- If the test runs no wrapper code and only pins a behavior of Swing, the JDK or ATK that the wrapper relies on,
  say so.

## Why (source)

Where the requirement comes from and what breaks without it. This is the most valuable part. Write the chain:
the quirk or problem, what the wrapper does about it, the effect on accessibility. Do not repeat `Checks`.

Name the source in the parentheses. Sources combine: `Why (Swing, Orca)`, `Why (Swing, wrapper)`.

| Source       | What it explains                                                    |
|--------------|---------------------------------------------------------------------|
| `Swing`      | How the Java Accessibility API implementation behaves               |
| `ATK`        | How ATK and the AT-SPI bridge behave                                |
| `Orca`       | Why the behavior matters to the screen reader                       |
| `wrapper`    | The contract between the Java layer and the native layer, or of ATK |
| `test setup` | Why the test needs special conditions                               |

Distinguish observed behavior from its internal cause. A reproduced test can confirm what happens, but claims
about why it happens require source evidence. Do not present assumptions as facts.

- For Swing, read the JDK sources (`$JAVA_HOME/lib/src.zip`) before saying why Swing behaves as it does.
- For ATK and the AT-SPI bridge, read their sources, and name the version when the behavior depends on it.
- For Orca, rely on a ticket, an upstream issue or the commit that records the observed behavior, and keep its
  id or link next to the tests. Such a record confirms what Orca did, not how Orca is built.
- If a cause is not confirmed, describe only the observed behavior, or leave the claim out.

## May change when (optional)

Use this section only when it adds information beyond `Checks` and `Why`.

Name a concrete external dependency, a temporary assumption, or a condition that could make
a workaround unnecessary. Explain what would need to be reconsidered and why.

Do not add this section merely to say:
- The test must change if the contract changes.
- The test should still pass if the implementation changes.
- The test may need updating if the wrapper is redesigned.

If no meaningful condition is known, omit the section.

When you add it:

- Name a specific assumption, not a generic possibility. `May change when: Swing changes its accessibility
  implementation` is correct and useless.
- Say whether the change would affect the workaround, the test expectation, or both. The reason for a
  workaround can disappear while the test stays valid: a new announcement mechanism in Orca can remove the
  original problem without making the check of `get_accessible_parent` wrong.
- Do not invent future scenarios just to fill this section.

## Size and style

- Explain what cannot be read from the test itself. Do not pad: three short sentences for a simple contract, more
  for a workaround that involves Swing and Orca.
- Lines are at most 120 characters.
- Keep the documentation of a class or file and the ticket comments (`// IJPL-…`) that group the tests. Do not
  copy them into each test.
- When a change alters what a test asserts, update its documentation in the same change. Do the same when a
  change to the wrapper removes a workaround or alters a behavior that a test's `Why` or `May change when`
  describes, even if the test code stays as it is.

## In each language

**Java, `wrapper/test/`.** Every `@Test` method has a Javadoc with `Checks` and `Why (source)`, in that order, and
`May change when` when it applies, as in the example above. Separate the parts with `<p>`. Write code names in
`{@code …}`. The class Javadoc says what the tests of the class cover.

**Python, `tests/atspi/`.** Every test function has a docstring that starts with `Checks:` and has a
`Why (source):` paragraph; `May change when:` only for a specific, confirmed dependency. Write code names in
backticks. Details of the launcher, the environment and the fixtures go into the docstring of `conftest.py` or
of the helper module, not into each test.

```python
def test_application_is_on_the_bus(app):
    """Checks: the application is on the AT-SPI desktop with the toolkit name of the wrapper, and its window is
    a child of the application with the role `frame` and the window title as its name.

    Why (wrapper): no lower-level test starts the wrapper the way an application does: the unit tests run
    without the native library, and the native tests never load the ATK bridge. The window reaches the
    application only through the AWT event WINDOW_OPENED, which no headless test produces.
    """
```

**C, `jni/tests/`.** Every test function has a block comment above it in prose, without the labels: the first
sentence is the contract, the next ones are the reason, with the ticket in parentheses. A new method of
`NativeTestFixtures` gets a one-line Javadoc that says what it builds.

```c
/*
 * A labeled combo box is named by its label, not by its selected item
 * (IJPL-262106). A JComboBox always has one accessible child, its popup, and
 * jaw_object_get_name used to return the name of that child's selection.
 */
static void test_combo_box_name_is_its_label(void) {
```
