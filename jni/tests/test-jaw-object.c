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
 * The ATK objects that the native layer builds for Swing components: what the
 * AT-SPI bridge reads from them.
 */

#include "jaw-test-jvm.h"

#define STRING_FIXTURE                                                         \
    "(Ljava/lang/String;)Ljavax/accessibility/AccessibleContext;"

/*
 * Checks: a JButton reaches ATK as a push button whose name is the text of the
 * button, and the Java layer stays disabled.
 *
 * Why (test setup): this is the smoke test of the harness. It passes only when
 * the JVM runs, the library is loaded once and bound to it, and the upcalls of
 * jaw_object_get_role and jaw_object_get_name reach Java. The disabled Java
 * layer shows that the static initializer of AtkWrapper did not load a second
 * copy of the library or the AT-SPI bridge.
 */
static void test_smoke_button(void) {
    jaw_test_reset();
    jobject context =
        jaw_test_fixture("button", STRING_FIXTURE, jaw_test_string("OK"));
    AtkObject *object = jaw_test_atk_object(context);

    g_assert_nonnull(object);
    g_assert_false(jaw_test_accessibility_enabled());
    g_assert_cmpint(atk_object_get_role(object), ==, ATK_ROLE_PUSH_BUTTON);
    g_assert_cmpstr(atk_object_get_name(object), ==, "OK");
}

/*
 * Checks: the same AccessibleContext always gives the same ATK object, and
 * another context gives another object, even for an equal component.
 *
 * Why (wrapper): jaw_impl_find_instance maps a context to its ATK object
 * through the handle that AtkWrapperDisposer keeps. An assistive technology
 * tells objects apart by identity: a new object for the same component is a
 * new element for Orca, which is why the Java layer keeps the node contexts of
 * a JTree stable (IJPL-242394).
 */
static void test_same_object_for_same_context(void) {
    jaw_test_reset();
    jobject context =
        jaw_test_fixture("button", STRING_FIXTURE, jaw_test_string("OK"));
    jobject other_context =
        jaw_test_fixture("button", STRING_FIXTURE, jaw_test_string("OK"));

    AtkObject *object = jaw_test_atk_object(context);
    AtkObject *same_object = jaw_test_atk_object(context);
    AtkObject *other_object = jaw_test_atk_object(other_context);

    g_assert_nonnull(object);
    g_assert_true(object == same_object);
    g_assert_nonnull(other_object);
    g_assert_true(object != other_object);
}






int main(int argc, char **argv) {
    jaw_test_init(&argc, &argv);

    g_test_add_func("/jaw/smoke/button", test_smoke_button);
    g_test_add_func("/jaw/impl/same-object-for-same-context",
                    test_same_object_for_same_context);

    return jaw_test_run();
}
