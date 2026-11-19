---
name: native-unit-tests
description: >-
  Write, update, review and run the GLib tests in jni/tests/ when changing the native layer (jni/src/). Use for bug
  fixes, behavior changes, workaround removal, test coverage reviews, and requests to add or fix native unit tests.
---

# Native unit tests

A change to the native layer (`jni/src/`) that changes behavior comes with tests in `jni/tests/`. The rules of the
`java-unit-tests` skill apply here as well: when a test is required, what to assert on, the jobs of regression,
neighbor and precondition tests, the proof that a test protects the fix, and reporting. This skill adds what is
specific to the native tests.

## How the tests work

A test program embeds a JVM, see `jni/tests/jaw-test-jvm.h`. It loads `libatk-wrapper.so` into the JVM with
`System.load`, calls `initNativeLibrary` and never loads the AT-SPI bridge. A test builds Swing components through
`NativeTestFixtures` (`jni/tests/java/`), gets their ATK objects with `jaw_test_atk_object` and checks them through
the public ATK API: `atk_object_get_name`, `atk_object_get_role`, `atk_object_ref_state_set`,
`atk_object_get_parent`, the ATK interfaces. Every call into Java is a real JNI upcall.

- Assert on what the AT-SPI bridge reads from ATK, not on internal C functions. Call a function of `jni/src/`
  directly only when it is the entry point, like `jaw_impl_find_instance`.
- One JVM per process: all test cases of a program share it. Call `jaw_test_reset()` first in every test, build
  new components for every test, and do not rely on the order of the test cases.
- Build components in a fixture method, which runs on the EDT. Call ATK from the test thread, the way the GLib
  thread calls it in production; the wrapper moves to the EDT itself.
- Keep the returned contexts as JNI local references until the end of the test: the disposer releases the ATK
  object of a context that the garbage collector finds.
- Create Java strings with `jaw_test_string`, not `NewStringUTF`, which takes modified UTF-8.
- A Java exception in a fixture fails the test through `g_test_fail`; check the returned objects with
  `g_assert_nonnull` before you use them.
- A known bug that is not fixed in the same change is a test that calls `g_test_incomplete` while the bug is
  there, with the reason. It shows as `TODO` in TAP and does not fail `make check`; remove the branch with the fix.
- A new fixture method gets a Javadoc that says what it builds. Each test function gets a comment in the
  `Checks / Why (source) / May change when` format of the `test-javadoc` skill.
- The AT-SPI bridge, D-Bus and the GLib main loop thread are not available. A behavior that needs them is not
  covered here; tell the user.

## Run the tests

The tests need Linux, ATK and `libjvm.so`; on macOS `configure` turns them off.

```bash
./autogen.sh && make && make check
make check JAW_TEST_JVM_OPTIONS=-Xcheck:jni      # also check the JNI calls
```

The results are in `jni/tests/test-jaw-object.log` and in `test-reports/TEST-native-*.xml`. A program that
crashes gets a failed test case in its report.

To prove that a test protects a fix, build and run the code without the fix in a separate worktree
(`git worktree add <tmp> <commit>`), never in the user's working tree.
