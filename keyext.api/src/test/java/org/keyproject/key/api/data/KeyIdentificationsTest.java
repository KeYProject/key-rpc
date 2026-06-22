/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import de.uka.ilkd.key.control.KeYEnvironment;
import de.uka.ilkd.key.proof.Proof;

import org.key_project.logic.Name;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keyproject.key.api.data.KeyIdentifications.EnvironmentId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrency test for {@link KeyIdentifications}. The JSON-RPC launcher
 * dispatches requests on several threads, so register/query must be thread-safe.
 * With a plain {@link java.util.HashMap} this test throws
 * {@link java.util.ConcurrentModificationException} or loses updates; with
 * concurrent maps it is stable.
 */
class KeyIdentificationsTest {
    @Test
    void concurrentRegisterAndAllProofIdsIsThreadSafe() throws Exception {
        var data = new KeyIdentifications();
        var envId = new EnvironmentId("env");
        data.register(envId, mock(KeYEnvironment.class));

        final int writers = 8;
        final int perWriter = 150;

        // Pre-build the proof mocks: mock creation is not what we are testing,
        // and keeping it out of the concurrent section isolates the map races.
        List<List<Proof>> batches = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            List<Proof> batch = new ArrayList<>(perWriter);
            for (int j = 0; j < perWriter; j++) {
                var proof = mock(Proof.class);
                when(proof.name()).thenReturn(new Name("p_" + w + "_" + j));
                batch.add(proof);
            }
            batches.add(batch);
        }

        var pool = Executors.newFixedThreadPool(writers + 2);
        var start = new CountDownLatch(1);
        var errors = new CopyOnWriteArrayList<Throwable>();
        var tasks = new ArrayList<Callable<Void>>();

        // Writers register proofs into the same environment concurrently.
        for (List<Proof> batch : batches) {
            tasks.add(() -> {
                start.await();
                for (Proof proof : batch) {
                    data.register(envId, proof);
                }
                return null;
            });
        }
        // Readers iterate the proof map concurrently with the writers.
        for (int r = 0; r < 2; r++) {
            tasks.add(() -> {
                start.await();
                for (int k = 0; k < 1000; k++) {
                    data.allProofIds();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        for (Future<Void> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        pool.shutdownNow();

        Assertions.assertTrue(errors.isEmpty(), () -> "concurrent access failed: " + errors);
        Assertions.assertEquals(writers * perWriter, data.allProofIds().size(),
            "every distinct proof id should be registered exactly once");
    }
}
