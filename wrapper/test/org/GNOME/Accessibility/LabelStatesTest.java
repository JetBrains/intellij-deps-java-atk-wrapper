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

package org.GNOME.Accessibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;

/**
 * The states that the {@code get_array_accessible_state} upcall reports for labels.
 */
class LabelStatesTest extends AtkTestBase {

    private static List<AccessibleState> states(AccessibleContext context) throws ReflectiveOperationException {
        return Arrays.asList((AccessibleState[]) invokeStatic(AtkObject.class, "get_array_accessible_state",
                new Class<?>[] {AccessibleContext.class}, context));
    }

    /**
     * Checks: Swing reports {@code FOCUSABLE} for a {@code JLabel} that nothing made focusable. The test runs no
     * wrapper code.
     * <p>
     * Why (Swing): {@code AccessibleAWTComponent.getAccessibleStateSet} adds the state when
     * {@code Component.isFocusable()} is true, and that is the default of every component. The wrapper removes the
     * state only for this reason.
     */
    @Test
    void precondition_swingReportsPlainLabelAsFocusable() throws Exception {
        AccessibleStateSet stateSet = onEdt(() -> new JLabel("Do you want to continue?")
                .getAccessibleContext().getAccessibleStateSet());

        assertTrue(stateSet.contains(AccessibleState.FOCUSABLE));
    }

    /**
     * Checks: the upcall does not report {@code FOCUSABLE} for a {@code JLabel} without the focus, and keeps its
     * other states.
     * <p>
     * Why (Swing, Orca): Swing reports the state for every label. Orca 50 reads the labels of a dialog as its
     * static text only when they are not focusable ({@code Utilities.unrelated_labels}), so it did not read the
     * message of a Swing dialog.
     * <p>
     * May change when: Orca stops using the focusable state to select the static text of a dialog. The removal
     * then has no effect on what Orca says.
     */
    @Test
    void plainLabelIsNotFocusable() throws Exception {
        AccessibleContext context = onEdt(() -> new JLabel("Do you want to continue?").getAccessibleContext());

        List<AccessibleState> states = states(context);

        assertFalse(states.contains(AccessibleState.FOCUSABLE));
        assertTrue(states.contains(AccessibleState.ENABLED));
        assertTrue(states.contains(AccessibleState.VISIBLE));
    }

    /**
     * Checks: a label that has the focus keeps {@code FOCUSABLE}.
     * <p>
     * Why (wrapper): an application can give the focus to a label, for example to let a screen reader user
     * read it. An object that is focused but not focusable is not a consistent state for an assistive technology.
     */
    @Test
    void focusedLabelStaysFocusable() throws Exception {
        AccessibleContext context = onEdt(() -> new JLabel("Do you want to continue?").getAccessibleContext());
        AccessibleStateSet stateSet = onEdt(context::getAccessibleStateSet);
        stateSet.add(AccessibleState.FOCUSED);

        List<AccessibleState> states = Arrays.asList(AtkObject.exposedStates(context, stateSet));

        assertTrue(states.contains(AccessibleState.FOCUSABLE));
        assertTrue(states.contains(AccessibleState.FOCUSED));
    }

    /**
     * Checks: a button keeps {@code FOCUSABLE}.
     * <p>
     * Why (wrapper): the removal is only for the label role. Orca needs the state on the controls.
     */
    @Test
    void buttonStaysFocusable() throws Exception {
        AccessibleContext context = onEdt(() -> new JButton("OK").getAccessibleContext());

        assertTrue(states(context).contains(AccessibleState.FOCUSABLE));
    }

    /**
     * Checks: an item of a {@code JList} has the label role and keeps {@code FOCUSABLE}.
     * <p>
     * Why (Swing, wrapper): the item takes its role and its states from the renderer, a {@code JLabel}. The native
     * layer exposes it as a list item, not as a label, so it is not static text and the removal must not apply.
     */
    @Test
    void listItemStaysFocusable() throws Exception {
        AccessibleContext context = onEdt(() -> new JList<>(new String[] {"first", "second"})
                .getAccessibleContext().getAccessibleChild(0).getAccessibleContext());

        assertSame(AccessibleRole.LABEL, onEdt(context::getAccessibleRole));
        assertTrue(states(context).contains(AccessibleState.FOCUSABLE));
    }
}
