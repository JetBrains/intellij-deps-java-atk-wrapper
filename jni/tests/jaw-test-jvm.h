/*
 * Java ATK Wrapper for GNOME
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, see <https://www.gnu.org/licenses/>.
 */

/*
 * The harness of the native unit tests.
 *
 * A test program embeds a JVM, loads libatk-wrapper.so into it and checks the
 * ATK objects that the native layer builds for Swing components, the way the
 * AT-SPI bridge sees them. Every call from C into Java is a real JNI upcall;
 * nothing is mocked. The JVM runs headless, and the AT-SPI bridge is never
 * loaded.
 *
 * A process can create only one JVM, so all test cases of a program share it.
 * Each test builds its own components through NativeTestFixtures; jaw_test_add
 * resets the static state of the Java layer before each test.
 *
 * The JVM always runs with -Xcheck:jni. A message of the JNI checks is reported
 * for the test during which the JVM printed it; with JAW_TEST_JNI_WARNINGS=fail
 * in the environment it fails that test.
 *
 * Writing a test:
 * - Register it with jaw_test_add, not g_test_add_func.
 * - Build components in a NativeTestFixtures method, which runs on the EDT.
 *   Call ATK from the test thread, as the GLib thread does in production; the
 *   wrapper moves to the EDT itself.
 * - Keep the contexts of a fixture as JNI local references until the end of
 *   the test: the disposer releases the ATK object of a context that the
 *   garbage collector finds.
 * - Create Java strings with jaw_test_string, not NewStringUTF, which takes
 *   modified UTF-8.
 * - Check the result of a fixture with g_assert_nonnull before using it: a
 *   Java exception fails the test and returns NULL.
 * - A known bug that the same change does not fix is a test that calls
 *   g_test_incomplete while the bug is there; it shows as TODO in TAP.
 * - The AT-SPI bridge, D-Bus and the GLib main loop thread are not available.
 *   A behavior that needs them belongs to the AT-SPI integration tests.
 */

#ifndef _JAW_TEST_JVM_H_
#define _JAW_TEST_JVM_H_

#include <atk/atk.h>
#include <jni.h>

G_BEGIN_DECLS

/*
 * Initializes GLib testing, creates the JVM and loads the library into it.
 * Reads JAW_TEST_CLASSPATH (the compiled Java layer and fixtures) and
 * JAW_TEST_LIBRARY (the path of libatk-wrapper.so) from the environment, which
 * make check sets. JAW_TEST_JVM_OPTIONS adds JVM options, for example
 * -Xlog:jni+resolve=debug.
 */
void jaw_test_init(int *argc, char ***argv);

typedef void (*JawTestFunc)(void);

/*
 * Registers a test case, like g_test_add_func. Around the test it resets the
 * state that the Java layer keeps between events, gives the test a JNI local
 * frame of its own, and reports the messages of -Xcheck:jni.
 */
void jaw_test_add(const char *path, JawTestFunc function);

/* Runs the registered tests and exits the process with their status. */
int jaw_test_run(void);

/* The JNI environment of the test thread. */
JNIEnv *jaw_test_env(void);

/*
 * Calls the static method of NativeTestFixtures with the given name and JNI
 * signature and returns its result. On a Java exception, prints it, marks the
 * test as failed and returns NULL.
 */
jobject jaw_test_fixture(const char *method, const char *signature, ...);

/*
 * Converts UTF-8 to a Java string. Unlike NewStringUTF, which takes modified
 * UTF-8, it accepts characters outside the Basic Multilingual Plane.
 */
jstring jaw_test_string(const char *utf8);

/* Returns element @index of a Java object array returned by a fixture. */
jobject jaw_test_array_element(jobject array, int index);

/*
 * Returns the ATK object of an AccessibleContext. Registers the context with
 * AtkWrapperDisposer and looks the object up with jaw_impl_find_instance, the
 * same way the Java layer and the native layer do in production.
 */
AtkObject *jaw_test_atk_object(jobject accessible_context);

/* The value of AtkWrapper.accessibilityEnabled. */
gboolean jaw_test_accessibility_enabled(void);

G_END_DECLS

#endif
