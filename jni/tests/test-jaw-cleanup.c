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
 * The JawImpl instances after the wrapper started its cleanup at the exit of
 * the JVM.
 *
 * jaw_impl_keep_instances cannot be undone, and it changes the lifetime of
 * every instance of its process. So this program has only the test of that
 * state; every other native test belongs to test-jaw-object.c.
 */

#include "jaw-test-jvm.h"
#include "jawimpl.h"

#define STRING_FIXTURE                                                         \
    "(Ljava/lang/String;)Ljavax/accessibility/AccessibleContext;"

/* A GWeakNotify that counts the finalized objects in the gint at @data. */
static void count_finalized(gpointer data, GObject *finalized_object) {
    (void)finalized_object;
    *(gint *)data += 1;
}

/*
 * After jaw_impl_keep_instances, the release of the last reference does not
 * finalize a JawImpl instance and does not call its weak references
 * (IJPL-252515). at-spi2-core 2.55.90 to 2.60.4 can leave a weak reference to
 * its destroyed object cache on an instance. atk_bridge_adaptor_cleanup then
 * releases the last reference to an instance that the bridge leased to a
 * client, and that weak reference ended the JVM at exit. Before
 * jaw_impl_keep_instances the same release finalizes the instance, so the
 * wrapper keeps no instance while the application runs.
 */
static void test_kept_instance_does_not_finalize(void) {
    jobject context = jaw_test_fixture("button", STRING_FIXTURE,
                                       jaw_test_string("Released"));
    jobject kept_context =
        jaw_test_fixture("button", STRING_FIXTURE, jaw_test_string("Kept"));
    g_assert_nonnull(context);
    g_assert_nonnull(kept_context);
    gint finalized = 0;

    /* The test owns the only reference, as the bridge does for a lease. */
    JawImpl *instance = jaw_impl_create_instance(jaw_test_env(), context);
    g_assert_nonnull(instance);
    g_object_weak_ref(G_OBJECT(instance), count_finalized, &finalized);
    g_object_unref(instance);
    g_assert_cmpint(finalized, ==, 1);

    JawImpl *kept = jaw_impl_create_instance(jaw_test_env(), kept_context);
    g_assert_nonnull(kept);
    g_object_weak_ref(G_OBJECT(kept), count_finalized, &finalized);
    jaw_impl_keep_instances();
    g_object_unref(kept);

    g_assert_cmpint(finalized, ==, 1);
    g_assert_cmpstr(atk_object_get_name(ATK_OBJECT(kept)), ==, "Kept");
}

int main(int argc, char **argv) {
    jaw_test_init(&argc, &argv);

    jaw_test_add("/jaw/impl/kept-instance-does-not-finalize",
                 test_kept_instance_does_not_finalize);

    return jaw_test_run();
}
