---
name: native-unit-tests
description: >-
  Write, review and run the GLib tests in jni/tests/ for the native layer (jni/src/): the ATK objects built for
  Swing components, JNI contracts, resource lifetime, -Xcheck:jni and the sanitizer build. Use together with the
  testing skill when changing jni/src/, and for native test failures and requests to add or fix native tests.
---

# Native unit tests

The `testing` skill says when a test is required, which layer gets it, what a test asserts and how to prove that
it protects the fix. Follow it first; this skill describes the native layer.

## How the tests work

A test program embeds a JVM, see the header comment of `jni/tests/jaw-test-jvm.h`. It loads `libatk-wrapper.so`
into the JVM with `System.load`, calls `initNativeLibrary` and never loads the AT-SPI bridge. A test builds Swing
components through `NativeTestFixtures` (`jni/tests/java/`), gets their ATK objects and checks them through the
public ATK API. Every call into Java is a real JNI upcall; nothing is mocked.

- `jaw_test_add(path, function)`: registers a test. Around it, it resets the static state of the Java layer, gives
  the test a JNI local frame and reports the messages of `-Xcheck:jni`.
- `jaw_test_fixture(method, signature, ...)`: calls a static method of `NativeTestFixtures`, which runs on the EDT.
- `jaw_test_atk_object(context)`: the ATK object of a context, registered and looked up the way production does.
- `jaw_test_string(utf8)`, `jaw_test_array_element(array, index)`, `jaw_test_env()`,
  `jaw_test_accessibility_enabled()`.

Tests are in `jni/tests/test-jaw-object.c`; a new program goes into `check_PROGRAMS` of `jni/tests/Makefile.am`.
`jni/tests/test-jaw-cleanup.c` is a program of its own for the state after `jaw_impl_keep_instances`, which no
other test can share: add a test there only if it needs that state.

## What can be tested

- What the AT-SPI bridge reads from an ATK object: `atk_object_get_name`, `atk_object_get_role`,
  `atk_object_ref_state_set`, `atk_object_get_parent`, the ATK interfaces.
- The identity of ATK objects: the same `AccessibleContext` gives the same object, another context another one.
- JNI contracts and their error handling, and the lifetime of native resources.
- How the native layer uses JNI, with `-Xcheck:jni`, and memory errors and undefined behavior, with the sanitizers.

Not here: the AT-SPI bridge, D-Bus and the GLib main loop thread. See `testing`.

## Writing a test

- Assert on what the AT-SPI bridge reads from ATK, not on internal C functions. Call a function of `jni/src/`
  directly only when it is the entry point, like `jaw_impl_find_instance`.
- One JVM per process: all test cases of a program share it. Register a test with `jaw_test_add`, not with
  `g_test_add_func`. Build new components for every test, and do not rely on the order of the test cases.
- Build components in a fixture method. Call ATK from the test thread, the way the GLib thread calls it in
  production; the wrapper moves to the EDT itself.
- Keep the returned contexts as JNI local references until the end of the test: the disposer releases the ATK
  object of a context that the garbage collector finds.
- Create Java strings with `jaw_test_string`, not `NewStringUTF`, which takes modified UTF-8.
- A Java exception in a fixture fails the test and returns `NULL`; check the returned objects with
  `g_assert_nonnull` before you use them.
- Compare ATK objects by pointer where identity matters; an equal name is not the same object.
- A known bug that is not fixed in the same change is a test that calls `g_test_incomplete` while the bug is
  there, with the reason. It shows as `TODO` in TAP and does not fail `make check`; remove the branch with the fix.
- Do not make functions of `jni/src/` public only for a test.
- Document every test as `test-docs` describes for C.

## JNI checks and sanitizers

- The test JVM always runs with `-Xcheck:jni`, and `jaw_test_add` reports its messages for the test that caused
  them. Treat such a message as a bug in `jni/src/`, or in the test if the test made the JNI call.
- `configure --enable-sanitizers` builds the library and the test programs with AddressSanitizer and UBSan.
  `jni/tests/Makefile.am` sets `ASAN_OPTIONS`, `UBSAN_OPTIONS` and `G_SLICE` for the run; leak detection is off,
  because the JVM never frees much of its memory.
- Read the whole report: the stack, the test and the command. Tell a defect of the code apart from a failure of the
  loader, the JDK or the sanitizer runtime.
- Do not hide a finding: no discarded exit codes, no filtered output, no suppression without a reason.

## Run the tests

The tests need Linux, ATK and `libjvm.so`, which `configure` looks for in `JAVA_HOME/lib/server` (`JVM_LIBDIR`
points elsewhere); on macOS `configure` turns them off.

```bash
./autogen.sh && make && make check
make check JAW_TEST_JNI_WARNINGS=fail                 # a message of -Xcheck:jni fails its test
make check JAW_TEST_JVM_OPTIONS=-Xlog:jni+resolve=debug
```

For the sanitizers, build a separate copy of the sources with `./autogen.sh --enable-sanitizers` and run
`make check` there. Never install or package that build.

The results are in `jni/tests/<program>.log` and in `test-reports/TEST-native-*.xml`. A program that
crashes gets a failed test case in its report.
