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
 * Each test builds its own components through NativeTestFixtures and calls
 * jaw_test_reset() first.
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
 * -Xcheck:jni.
 */
void jaw_test_init(int *argc, char ***argv);

/* Runs the registered tests and exits the process with their status. */
int jaw_test_run(void);

/* The JNI environment of the test thread. */
JNIEnv *jaw_test_env(void);

/* Forgets the static state that the Java layer keeps between events. */
void jaw_test_reset(void);

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
