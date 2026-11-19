---
name: test-javadoc
description: >-
  Write or review Javadoc for Java unit tests in wrapper/test/ using the Checks / Why / May change when format.
  Use when adding tests, changing assertions, documenting or explaining tests, changing wrapper behavior,
  or removing a workaround that existing tests document.
---

# Test Javadoc

The tests here check more than a Java API: they pin how Swing, the wrapper, ATK and Orca fit together. A test
name rarely says why a workaround exists.

Every `@Test` method has a Javadoc with `Checks` and `Why (source)`, in that order.
Add `May change when` only when a specific, non-obvious change would justify revisiting
the test, its expectation, or a workaround it protects. Separate the parts with `<p>`.

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

- Read the test, the code it exercises, and any related issue or nearby tests before writing the Javadoc. The
  commit that added the test and the commit of the fix it covers often record the reason.
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
- If the test runs no wrapper code and only pins a Swing behavior the wrapper relies on, say so.

## Why (source)

Where the requirement comes from and what breaks without it. This is the most valuable part. Write the chain:
the quirk or problem, what the wrapper does about it, the effect on accessibility. Do not repeat `Checks`.

Name the source in the parentheses. Sources combine: `Why (Swing, Orca)`, `Why (Swing, wrapper)`.

| Source       | What it explains                                         |
|--------------|----------------------------------------------------------|
| `Swing`      | How the Java Accessibility API implementation behaves    |
| `Orca`       | Why the behavior matters to the screen reader            |
| `wrapper`    | The contract between the Java layer and native ATK       |
| `test setup` | Why the test needs special conditions                    |

Distinguish observed behavior from its internal cause. A reproduced test can confirm what happens, but claims
about why it happens require source evidence. Do not present assumptions as facts.

- For Swing, read the JDK sources (`$JAVA_HOME/lib/src.zip`) before saying why Swing behaves as it does.
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
- Lines are at most 120 characters. Write code names in `{@code …}`.
- Keep the class Javadoc and the ticket comments (`// IJPL-…`) that group the tests. Do not copy them into each
  method.
- When a change alters what a test asserts, update its Javadoc in the same change. Do the same when a change to
  the wrapper removes a workaround or alters a behavior that a test's `Why` or `May change when` describes, even
  if the test code stays as it is.
