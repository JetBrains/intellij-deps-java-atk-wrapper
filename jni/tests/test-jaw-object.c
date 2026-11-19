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

/*
 * Checks: the name of a combo box with a label is the text of the label, not
 * the selected item.
 *
 * Why (Swing, Orca): a JComboBox always has one accessible child, its popup.
 * jaw_object_get_name used to return the name of the selected child for every
 * combo box with one child, so Orca spoke the selected item twice instead of
 * the label (IJPL-262106, fixed in 33038d2). The name now comes from
 * AccessibleContext.getAccessibleName, which for a combo box falls back to the
 * label set by JLabel.setLabelFor; Orca reads the selected item as the value.
 */
static void test_combo_box_name_is_its_label(void) {
    jaw_test_reset();
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
 * Checks: a collapsed tree node that can be expanded reports
 * ATK_STATE_EXPANDABLE, and neither ATK_STATE_EXPANDED nor ATK_STATE_COLLAPSED,
 * nor ATK_STATE_INVALID.
 *
 * Why (Swing, Orca): Swing reports the Java COLLAPSED state for every collapsed
 * node. The wrapper reports a collapse as the removal of ATK_STATE_EXPANDED,
 * and no event removes ATK_STATE_COLLAPSED from the state cache of Orca, so
 * Orca said "collapsed" after an expansion (IJPL-237903, fixed in 844aed0).
 * jaw_object_ref_state_set therefore drops the Java COLLAPSED state; Orca
 * derives "collapsed" from ATK_STATE_EXPANDABLE. Before ATK 2.38 the state
 * mapped to ATK_STATE_INVALID, which made the object invalid.
 *
 * May change when: Orca stops caching ATK_STATE_COLLAPSED, or the wrapper
 * reports a collapse as the addition of ATK_STATE_COLLAPSED. The state could
 * then be exposed again.
 */
static void test_collapsed_state_is_not_exposed(void) {
    jaw_test_reset();
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
 * Checks: atk_object_get_parent of an item of a menu returns the ATK object of
 * the JMenu, not of the JPopupMenu that holds the item.
 *
 * Why (Swing, Orca): Swing reports the JPopupMenu as the parent of a menu item
 * until something asks the JMenu for its children. The Java upcall
 * get_accessible_parent returns the JMenu instead, and Orca then speaks the
 * menu name when the focus enters the menu (IJPL-242397). The Java unit tests
 * check the upcall; this test checks that the native layer finds the ATK
 * object of the returned menu.
 */
static void test_menu_item_parent_is_menu(void) {
    jaw_test_reset();
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



int main(int argc, char **argv) {
    jaw_test_init(&argc, &argv);

    g_test_add_func("/jaw/smoke/button", test_smoke_button);
    g_test_add_func("/jaw/impl/same-object-for-same-context",
                    test_same_object_for_same_context);
    g_test_add_func("/jaw/object/combo-box-name-is-its-label",
                    test_combo_box_name_is_its_label);
    g_test_add_func("/jaw/object/collapsed-state-is-not-exposed",
                    test_collapsed_state_is_not_exposed);
    g_test_add_func("/jaw/object/menu-item-parent-is-menu",
                    test_menu_item_parent_is_menu);

    return jaw_test_run();
}
