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
 * The smoke test of the harness: a JButton reaches ATK as a push button named
 * by its text. It passes only when the library is bound to the JVM and the
 * upcalls of jaw_object_get_role and jaw_object_get_name reach Java. The Java
 * layer must stay disabled: an enabled one would have loaded the AT-SPI bridge.
 */
static void test_smoke_button(void) {
    jobject context =
        jaw_test_fixture("button", STRING_FIXTURE, jaw_test_string("OK"));
    AtkObject *object = jaw_test_atk_object(context);

    g_assert_nonnull(object);
    g_assert_false(jaw_test_accessibility_enabled());
    g_assert_cmpint(atk_object_get_role(object), ==, ATK_ROLE_PUSH_BUTTON);
    g_assert_cmpstr(atk_object_get_name(object), ==, "OK");
}

/*
 * The same AccessibleContext gives the same ATK object, and another context
 * another object, even for an equal component. jaw_impl_find_instance maps a
 * context through the handle that AtkWrapperDisposer keeps; the stable node
 * contexts of a JTree rely on it (IJPL-242394).
 */
static void test_same_object_for_same_context(void) {
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

/*
 * A labeled combo box is named by its label, not by its selected item
 * (IJPL-262106). A JComboBox always has one accessible child, its popup, and
 * jaw_object_get_name used to return the name of that child's selection.
 */
static void test_combo_box_name_is_its_label(void) {
    jobject context = jaw_test_fixture(
        "labeledComboBox",
        "(Ljava/lang/String;Ljava/lang/String;)Ljavax/accessibility/"
        "AccessibleContext;",
        jaw_test_string("Position"), jaw_test_string("Right"));
    AtkObject *object = jaw_test_atk_object(context);

    g_assert_nonnull(object);
    g_assert_cmpint(atk_object_get_role(object), ==, ATK_ROLE_COMBO_BOX);
    g_assert_cmpstr(atk_object_get_name(object), ==, "Position");
}

/*
 * A collapsed tree node reports ATK_STATE_EXPANDABLE but not
 * ATK_STATE_COLLAPSED (IJPL-237903). Swing reports the Java COLLAPSED state,
 * and jaw_object_ref_state_set drops it: the wrapper reports a collapse as the
 * removal of ATK_STATE_EXPANDED, and no event would remove a COLLAPSED state
 * that a screen reader cached. Before ATK 2.38 the Java state mapped to
 * ATK_STATE_INVALID, so that state must not appear either.
 */
static void test_collapsed_state_is_not_exposed(void) {
    jobject context = jaw_test_fixture(
        "collapsedTreeNode", "()Ljavax/accessibility/AccessibleContext;");
    AtkObject *object = jaw_test_atk_object(context);
    g_assert_nonnull(object);

    AtkStateSet *states = atk_object_ref_state_set(object);
    g_assert_nonnull(states);

    g_assert_true(atk_state_set_contains_state(states, ATK_STATE_EXPANDABLE));
    g_assert_false(atk_state_set_contains_state(states, ATK_STATE_EXPANDED));
#if ATK_CHECK_VERSION(2, 38, 0)
    g_assert_false(atk_state_set_contains_state(states, ATK_STATE_COLLAPSED));
#endif
    g_assert_false(atk_state_set_contains_state(states, ATK_STATE_INVALID));

    g_object_unref(states);
}

/*
 * The parent of a menu item is the ATK object of its JMenu, not of the
 * JPopupMenu that holds it (IJPL-242397). The Java unit tests check the upcall
 * get_accessible_parent; this test checks that the native layer finds the ATK
 * object of the menu that the upcall returns.
 */
static void test_menu_item_parent_is_menu(void) {
    jobject contexts = jaw_test_fixture(
        "menuWithItem", "()[Ljavax/accessibility/AccessibleContext;");
    jobject menu_context = jaw_test_array_element(contexts, 0);
    jobject item_context = jaw_test_array_element(contexts, 1);

    AtkObject *item = jaw_test_atk_object(item_context);
    g_assert_nonnull(item);
    AtkObject *parent = atk_object_get_parent(item);
    AtkObject *menu = jaw_test_atk_object(menu_context);

    g_assert_nonnull(parent);
    g_assert_true(parent == menu);
    g_assert_cmpint(atk_object_get_role(parent), ==, ATK_ROLE_MENU);
}

/* "Open " and U+1F4C2, a character outside the Basic Multilingual Plane. */
#define NON_BMP_TEXT "Open \xF0\x9F\x93\x82"

/*
 * A name with a character outside the BMP reaches ATK as valid UTF-8, the same
 * text as in Java. GetStringUTFChars returns modified UTF-8, where such a
 * character is two encoded surrogates; D-Bus rejects that as invalid UTF-8.
 */
static void test_name_with_non_bmp_character(void) {
    jobject context = jaw_test_fixture("label", STRING_FIXTURE,
                                       jaw_test_string(NON_BMP_TEXT));
    AtkObject *object = jaw_test_atk_object(context);
    g_assert_nonnull(object);

    const gchar *name = atk_object_get_name(object);
    g_assert_nonnull(name);
    g_assert_true(g_utf8_validate(name, -1, NULL));
    g_assert_cmpstr(name, ==, NON_BMP_TEXT);
}

/*
 * The same as test_name_with_non_bmp_character, for the description.
 */
static void test_description_with_non_bmp_character(void) {
    jobject context = jaw_test_fixture("buttonWithDescription", STRING_FIXTURE,
                                       jaw_test_string(NON_BMP_TEXT));
    AtkObject *object = jaw_test_atk_object(context);
    g_assert_nonnull(object);

    const gchar *description = atk_object_get_description(object);
    g_assert_nonnull(description);
    g_assert_true(g_utf8_validate(description, -1, NULL));
    g_assert_cmpstr(description, ==, NON_BMP_TEXT);
}

int main(int argc, char **argv) {
    jaw_test_init(&argc, &argv);

    jaw_test_add("/jaw/smoke/button", test_smoke_button);
    jaw_test_add("/jaw/impl/same-object-for-same-context",
                 test_same_object_for_same_context);
    jaw_test_add("/jaw/object/combo-box-name-is-its-label",
                 test_combo_box_name_is_its_label);
    jaw_test_add("/jaw/object/collapsed-state-is-not-exposed",
                 test_collapsed_state_is_not_exposed);
    jaw_test_add("/jaw/object/menu-item-parent-is-menu",
                 test_menu_item_parent_is_menu);
    jaw_test_add("/jaw/object/name-with-non-bmp-character",
                 test_name_with_non_bmp_character);
    jaw_test_add("/jaw/object/description-with-non-bmp-character",
                 test_description_with_non_bmp_character);

    return jaw_test_run();
}
