/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the temp-file leak: each loadKey/loadTerm/loadProblem
 * wrote a {@code json-rpc-*.key} file into the system temp dir and never removed
 * it.
 */
class LoadKeyTempFileTest {
    @Test
    void loadTermDoesNotLeaveTempFile() throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countTempKeyFiles(tmp);

        var api = new KeyApiImpl();
        var proofId = api.loadTerm("true").get();
        Assertions.assertNotNull(proofId);

        long after = countTempKeyFiles(tmp);
        Assertions.assertEquals(before, after,
            "loadKey should not leave a temporary .key file behind");
    }

    private static long countTempKeyFiles(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(p -> {
                var name = p.getFileName().toString();
                return name.startsWith("json-rpc-") && name.endsWith(".key");
            }).count();
        }
    }
}
