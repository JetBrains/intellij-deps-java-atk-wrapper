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

#include "jaw-test-jvm.h"

#include "jawimpl.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

/*
 * Defined in AtkWrapper.c. The Java layer calls it before it loads the AT-SPI
 * bridge; the tests call it and never load the bridge.
 */
JNIEXPORT jboolean JNICALL
Java_org_GNOME_Accessibility_AtkWrapper_initNativeLibrary(JNIEnv *env,
                                                          jobject obj);

#define FIXTURES_CLASS "org/GNOME/Accessibility/NativeTestFixtures"
#define DISPOSER_CLASS "org/GNOME/Accessibility/AtkWrapperDisposer"
#define WRAPPER_CLASS "org/GNOME/Accessibility/AtkWrapper"

static JavaVM *test_jvm = NULL;
static JNIEnv *test_env = NULL;

static jclass fixtures_class = NULL;
static jclass disposer_class = NULL;
static jclass wrapper_class = NULL;
static jmethodID disposer_get_instance = NULL;
static jmethodID disposer_add_record = NULL;
static jmethodID wrapper_forget_focus_history = NULL;
static jfieldID wrapper_accessibility_enabled = NULL;

/*
 * The local references of one test. In production the GLib main loop pushes a
 * frame for each iteration; a test makes several ATK calls and keeps the
 * contexts of its components, so its frame is larger.
 */
#define TEST_LOCAL_FRAME_SIZE 512

/* The messages of -Xcheck:jni that the JVM printed so far. */
static gint jni_check_messages = 0;
/* JAW_TEST_JNI_WARNINGS=fail: a message of -Xcheck:jni fails the test. */
static gboolean jni_check_fails_test = FALSE;

static gboolean is_jni_check_message(const char *text) {
    return strstr(text, "WARNING in native method") != NULL ||
           strstr(text, "WARNING: JNI local refs") != NULL ||
           strstr(text, "FATAL ERROR in native method") != NULL;
}

/*
 * The JVM prints through this function, see the "vfprintf" option of the JNI
 * Invocation API. It counts the messages of -Xcheck:jni and passes the text on
 * to stderr: stdout carries the TAP stream of the test program.
 */
static jint JNICALL jvm_vfprintf(FILE *stream, const char *format,
                                 va_list args) {
    (void)stream;
    gchar *text = g_strdup_vprintf(format, args);
    if (text == NULL) {
        return 0;
    }
    if (is_jni_check_message(text)) {
        g_atomic_int_inc(&jni_check_messages);
    }
    int written = fputs(text, stderr) < 0 ? -1 : (int)strlen(text);
    fflush(stderr);
    g_free(text);
    return written;
}

/* Aborts the program: a failure here leaves no test able to run. */
static void fail_setup_on_exception(const char *what) {
    if ((*test_env)->ExceptionCheck(test_env)) {
        (*test_env)->ExceptionDescribe(test_env);
        (*test_env)->ExceptionClear(test_env);
        g_error("%s threw a Java exception", what);
    }
}

/* Marks the current test as failed; returns TRUE if there was an exception. */
static gboolean fail_test_on_exception(const char *what) {
    if (!(*test_env)->ExceptionCheck(test_env)) {
        return FALSE;
    }
    (*test_env)->ExceptionDescribe(test_env);
    (*test_env)->ExceptionClear(test_env);
    g_test_message("%s threw a Java exception, see the stack trace above",
                   what);
    g_test_fail();
    return TRUE;
}

static jclass find_class(const char *name) {
    jclass local = (*test_env)->FindClass(test_env, name);
    fail_setup_on_exception(name);
    jclass global = (*test_env)->NewGlobalRef(test_env, local);
    (*test_env)->DeleteLocalRef(test_env, local);
    return global;
}

static jmethodID find_static_method(jclass klass, const char *name,
                                    const char *signature) {
    jmethodID method =
        (*test_env)->GetStaticMethodID(test_env, klass, name, signature);
    fail_setup_on_exception(name);
    return method;
}

static void create_jvm(const char *class_path) {
    GPtrArray *options = g_ptr_array_new();
    g_ptr_array_add(options,
                    g_strconcat("-Djava.class.path=", class_path, NULL));
    g_ptr_array_add(options, g_strdup("-Djava.awt.headless=true"));
    /* System.load is a restricted method; JDK 24 and later warn without it. */
    g_ptr_array_add(options, g_strdup("--enable-native-access=ALL-UNNAMED"));
    /* Always on: the tests also check how the native layer uses JNI. */
    g_ptr_array_add(options, g_strdup("-Xcheck:jni"));

    const char *extra = g_getenv("JAW_TEST_JVM_OPTIONS");
    if (extra != NULL) {
        gchar **parts = g_strsplit_set(extra, " \t", -1);
        for (gchar **part = parts; *part != NULL; part++) {
            if (**part != '\0') {
                g_ptr_array_add(options, g_strdup(*part));
            }
        }
        g_strfreev(parts);
    }

    /* The option strings stay allocated for the life of the process. */
    JavaVMOption *vm_options = g_new0(JavaVMOption, options->len + 1);
    for (guint i = 0; i < options->len; i++) {
        vm_options[i].optionString = g_ptr_array_index(options, i);
    }
    /* The JNI Invocation API takes the hook as a data pointer. */
    vm_options[options->len].optionString = (char *)"vfprintf";
    vm_options[options->len].extraInfo = (void *)jvm_vfprintf;

    JavaVMInitArgs args = {0};
    args.version = JNI_VERSION_1_8;
    args.nOptions = (jint)options->len + 1;
    args.options = vm_options;
    args.ignoreUnrecognized = JNI_FALSE;

    if (JNI_CreateJavaVM(&test_jvm, (void **)&test_env, &args) != JNI_OK) {
        g_error("JNI_CreateJavaVM failed");
    }
}

void jaw_test_init(int *argc, char ***argv) {
    // The test driver of make check reads TAP, which GLib before 2.62 prints
    // only with --tap. The option goes here and not into AM_LOG_FLAGS of
    // Makefile.am: automake puts those in front of the program, as the command.
    char **tap_argv = g_new0(char *, *argc + 2);
    tap_argv[0] = (*argv)[0];
    tap_argv[1] = "--tap";
    for (int i = 1; i < *argc; i++) {
        tap_argv[i + 1] = (*argv)[i];
    }
    *argc += 1;
    *argv = tap_argv; // lives until the end of the program, as argv does
    g_test_init(argc, argv, NULL);

    const char *class_path = g_getenv("JAW_TEST_CLASSPATH");
    const char *library = g_getenv("JAW_TEST_LIBRARY");
    if (class_path == NULL || library == NULL) {
        g_error("JAW_TEST_CLASSPATH and JAW_TEST_LIBRARY are not set; run the "
                "tests with make check");
    }

    jni_check_fails_test =
        g_strcmp0(g_getenv("JAW_TEST_JNI_WARNINGS"), "fail") == 0;

    create_jvm(class_path);

    fixtures_class = find_class(FIXTURES_CLASS);
    disposer_class = find_class(DISPOSER_CLASS);
    wrapper_class = find_class(WRAPPER_CLASS);

    /*
     * Load the library from Java, not only by linking: System.load calls
     * JNI_OnLoad, which saves the JVM for jaw_util_get_jni_env, and binds the
     * native methods of AtkWrapper. The test program links the same file, so
     * the dynamic loader returns the copy that is already loaded.
     */
    jmethodID load_library = find_static_method(fixtures_class, "loadLibrary",
                                                "(Ljava/lang/String;)V");
    jstring jlibrary = (*test_env)->NewStringUTF(test_env, library);
    (*test_env)->CallStaticVoidMethod(test_env, fixtures_class, load_library,
                                      jlibrary);
    fail_setup_on_exception("System.load");
    (*test_env)->DeleteLocalRef(test_env, jlibrary);

    if (!Java_org_GNOME_Accessibility_AtkWrapper_initNativeLibrary(test_env,
                                                                   NULL)) {
        g_error("initNativeLibrary failed");
    }

    disposer_get_instance = find_static_method(
        disposer_class, "getInstance",
        "()Lorg/GNOME/Accessibility/AtkWrapperDisposer;");
    disposer_add_record = (*test_env)->GetMethodID(
        test_env, disposer_class, "addRecord",
        "(Ljavax/accessibility/AccessibleContext;)V");
    fail_setup_on_exception("addRecord");
    wrapper_forget_focus_history =
        find_static_method(wrapper_class, "forgetFocusHistory", "()V");
    wrapper_accessibility_enabled = (*test_env)->GetStaticFieldID(
        test_env, wrapper_class, "accessibilityEnabled", "Z");
    fail_setup_on_exception("accessibilityEnabled");
}

typedef struct {
    JawTestFunc function;
} TestCase;

static void reset_wrapper_state(void) {
    (*test_env)->CallStaticVoidMethod(test_env, wrapper_class,
                                      wrapper_forget_focus_history);
    fail_test_on_exception("AtkWrapper.forgetFocusHistory");
}

static void run_test_case(gconstpointer data) {
    const TestCase *test_case = data;

    if ((*test_env)->PushLocalFrame(test_env, TEST_LOCAL_FRAME_SIZE) < 0) {
        g_error("PushLocalFrame failed");
    }
    gint messages_before = g_atomic_int_get(&jni_check_messages);

    reset_wrapper_state();
    test_case->function();

    (*test_env)->PopLocalFrame(test_env, NULL);

    gint messages = g_atomic_int_get(&jni_check_messages) - messages_before;
    if (messages > 0) {
        g_test_message("-Xcheck:jni printed %d message(s) during this test, "
                       "see the output of the test program",
                       messages);
        if (jni_check_fails_test) {
            g_test_fail();
        }
    }
}

void jaw_test_add(const char *path, JawTestFunc function) {
    /* Lives as long as the registered test, that is, as the process. */
    TestCase *test_case = g_new0(TestCase, 1);
    test_case->function = function;
    g_test_add_data_func(path, test_case, run_test_case);
}

int jaw_test_run(void) {
    int status = g_test_run();

    g_printerr("-Xcheck:jni printed %d message(s) in this run%s\n",
               g_atomic_int_get(&jni_check_messages),
               jni_check_fails_test ? "" : " (report only)");

    fflush(stdout);
    fflush(stderr);

    /*
     * Leave through System.exit, so that the JVM shuts down its own threads,
     * the EDT among them, before the process exits.
     */
    jclass system = (*test_env)->FindClass(test_env, "java/lang/System");
    jmethodID exit_method =
        (*test_env)->GetStaticMethodID(test_env, system, "exit", "(I)V");
    (*test_env)->CallStaticVoidMethod(test_env, system, exit_method,
                                      (jint)status);
    return status;
}

JNIEnv *jaw_test_env(void) {
    return test_env;
}

jobject jaw_test_fixture(const char *method, const char *signature, ...) {
    jmethodID fixture = (*test_env)->GetStaticMethodID(
        test_env, fixtures_class, method, signature);
    if (fixture == NULL) {
        (*test_env)->ExceptionDescribe(test_env);
        (*test_env)->ExceptionClear(test_env);
        g_error("NativeTestFixtures has no method %s%s", method, signature);
    }

    va_list args;
    va_start(args, signature);
    jobject result = (*test_env)->CallStaticObjectMethodV(
        test_env, fixtures_class, fixture, args);
    va_end(args);

    if (fail_test_on_exception(method)) {
        return NULL;
    }
    return result;
}

jstring jaw_test_string(const char *utf8) {
    glong length = 0;
    gunichar2 *utf16 = g_utf8_to_utf16(utf8, -1, NULL, &length, NULL);
    g_assert_nonnull(utf16);
    jstring result =
        (*test_env)->NewString(test_env, (const jchar *)utf16, (jsize)length);
    g_free(utf16);
    return result;
}

jobject jaw_test_array_element(jobject array, int index) {
    if (array == NULL) {
        return NULL;
    }
    jobject element = (*test_env)->GetObjectArrayElement(
        test_env, (jobjectArray)array, (jsize)index);
    if (fail_test_on_exception("GetObjectArrayElement")) {
        return NULL;
    }
    return element;
}

AtkObject *jaw_test_atk_object(jobject accessible_context) {
    if (accessible_context == NULL) {
        return NULL;
    }

    jobject disposer = (*test_env)->CallStaticObjectMethod(
        test_env, disposer_class, disposer_get_instance);
    if (fail_test_on_exception("AtkWrapperDisposer.getInstance")) {
        return NULL;
    }
    (*test_env)->CallVoidMethod(test_env, disposer, disposer_add_record,
                                accessible_context);
    (*test_env)->DeleteLocalRef(test_env, disposer);
    if (fail_test_on_exception("AtkWrapperDisposer.addRecord")) {
        return NULL;
    }

    JawImpl *impl = jaw_impl_find_instance(test_env, accessible_context);
    return impl == NULL ? NULL : ATK_OBJECT(impl);
}

gboolean jaw_test_accessibility_enabled(void) {
    return (*test_env)->GetStaticBooleanField(test_env, wrapper_class,
                                              wrapper_accessibility_enabled);
}
