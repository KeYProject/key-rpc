/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keyproject.key.api.data.FunctionDesc;
import org.keyproject.key.api.data.PredicateDesc;
import org.keyproject.key.api.data.PrintOptions;
import org.keyproject.key.api.data.ProblemDefinition;
import org.keyproject.key.api.data.SortDesc;

/**
 * Tests for {@link KeyApiImpl#loadProblem} and its {@code .key} rendering.
 */
class KeyApiImplTest {
    private static SortDesc sort(String name) {
        return new SortDesc(name, "", List.of(), false, name);
    }

    /** Pure rendering test: no KeY loading, just the generated input document. */
    @Test
    void buildKeyInputRendersDeclarationsAndSequent() {
        var s = sort("s");
        var problem = new ProblemDefinition(
            List.of(s),
            List.of(new FunctionDesc("c", "s", s, List.of(), true, false, false)),
            List.of(new PredicateDesc("p", List.of(s))),
            List.of("p(c)"),
            List.of("p(c)"));

        var key = KeyApiImpl.buildKeyInput(problem);
        Assertions.assertTrue(key.contains("\\sorts {"), key);
        Assertions.assertTrue(key.contains("s;"), key);
        Assertions.assertTrue(key.contains("\\functions {"), key);
        Assertions.assertTrue(key.contains("s c;"), key);
        Assertions.assertTrue(key.contains("\\predicates {"), key);
        Assertions.assertTrue(key.contains("p(s);"), key);
        Assertions.assertTrue(key.contains("\\problem {"), key);
        Assertions.assertTrue(key.contains("(p(c)) -> (p(c))"), key);
    }

    @Test
    void buildKeyInputEncodesEmptyAntecedentOrSuccedent() {
        // succedent only -> the disjunction of goals, no implication
        var succOnly = new ProblemDefinition(null, null, null, null, List.of("a", "b"));
        Assertions.assertTrue(KeyApiImpl.buildKeyInput(succOnly).contains("(a) | (b)"));
        // antecedent only -> assumptions imply false
        var anteOnly = new ProblemDefinition(null, null, null, List.of("a"), null);
        Assertions.assertTrue(KeyApiImpl.buildKeyInput(anteOnly).contains("(a) -> false"));
        // neither -> nothing to prove
        var empty = new ProblemDefinition(null, null, null, null, null);
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> KeyApiImpl.buildKeyInput(empty));
    }

    /**
     * End-to-end: a programmatic problem with a custom sort, constant, and two
     * predicates is loaded, and the resulting root sequent is the expected
     * {@code p(c) ==> q(c)}.
     */
    @Test
    void loadProblemLoadsTheDefinedSequent() throws Exception {
        var s = sort("s");
        var problem = new ProblemDefinition(
            List.of(s),
            List.of(new FunctionDesc("c", "s", s, List.of(), true, false, false)),
            List.of(new PredicateDesc("p", List.of(s)), new PredicateDesc("q", List.of(s))),
            List.of("p(c)"),
            List.of("q(c)"));

        var api = new KeyApiImpl();
        var proofId = api.loadProblem(problem).get();
        Assertions.assertNotNull(proofId, "expected a proof to be created");

        var root = api.root(proofId).get();
        var printed = api.print(root.nodeid(), new PrintOptions(false, 80, 2, false, false)).get();
        var sequent = printed.sequent();
        Assertions.assertTrue(sequent.contains("p(c)"), sequent);
        Assertions.assertTrue(sequent.contains("q(c)"), sequent);
        Assertions.assertTrue(sequent.contains("==>"), sequent);
    }

    /** Abstract sorts and {@code \extends} are rendered and parsed correctly. */
    @Test
    void loadProblemSupportsAbstractSortsAndSubsorts() throws Exception {
        var a = new SortDesc("a", "", List.of(), true, "a"); // abstract
        var b = new SortDesc("b", "", List.of(a), false, "b"); // b extends a
        var problem = new ProblemDefinition(
            List.of(a, b),
            List.of(new FunctionDesc("c", "b", b, List.of(), true, false, false)),
            List.of(new PredicateDesc("p", List.of(a))),
            List.of("p(c)"),
            List.of("p(c)"));

        var api = new KeyApiImpl();
        Assertions.assertNotNull(api.loadProblem(problem).get());
    }
}
