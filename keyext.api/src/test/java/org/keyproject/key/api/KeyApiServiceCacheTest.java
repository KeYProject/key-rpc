/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the cached service-loader lookups (macros / script commands). The
 * caching is by construction; these guard against regressions in the behaviour.
 */
class KeyApiServiceCacheTest {
    @Test
    void availableMacrosAndCommandsAreNonEmptyAndStable() throws Exception {
        var api = new KeyApiImpl();
        var macros1 = api.getAvailableMacros().get();
        var macros2 = api.getAvailableMacros().get();
        Assertions.assertFalse(macros1.isEmpty(), "expected built-in macros");
        Assertions.assertEquals(macros1, macros2, "repeated calls must be consistent");
        Assertions.assertFalse(api.getAvailableScriptCommands().get().isEmpty(),
            "expected built-in script commands");
    }

    @Test
    void macroRejectsUnknownName() throws Exception {
        var api = new KeyApiImpl();
        var proofId = api.loadTerm("true").get();
        var ex = Assertions.assertThrows(ExecutionException.class,
            () -> api.macro(proofId, "definitely-not-a-real-macro", null).get());
        Assertions.assertInstanceOf(NoSuchElementException.class, ex.getCause());
    }
}
