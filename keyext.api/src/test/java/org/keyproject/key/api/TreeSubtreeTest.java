/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keyproject.key.api.data.KeyIdentifications.TreeNodeId;
import org.keyproject.key.api.data.TreeNodeDesc;

/**
 * Regression test for {@code treeSubtree}, which previously always returned an
 * empty list (a silent stub).
 */
class TreeSubtreeTest {
    @Test
    void treeSubtreeReturnsTheSubtreeRootedAtTheNode() throws Exception {
        var api = new KeyApiImpl();
        var proofId = api.loadTerm("true").get();
        var treeRoot = api.treeRoot(proofId).get();
        var rootSerial = treeRoot.id().nodeId();

        List<TreeNodeDesc> subtree =
            api.treeSubtree(proofId, new TreeNodeId(rootSerial)).get();
        Assertions.assertFalse(subtree.isEmpty(), "subtree of the root must not be empty");
        Assertions.assertTrue(
            subtree.stream().anyMatch(d -> d.id().nodeId().equals(rootSerial)),
            "subtree should contain the root node itself");

        // an unknown node yields an empty list
        Assertions.assertTrue(
            api.treeSubtree(proofId, new TreeNodeId("999999")).get().isEmpty());
    }
}
