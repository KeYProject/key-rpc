/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package edu.kit.keyext.client;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import org.key_project.key.api.client.JsonRPC;
import org.key_project.key.api.client.RPCLayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Alexander Weigl
 * @version 1 (24.11.24)
 */
public class RPCLayerTest {
    @Test
    void testSending() throws IOException {
        var incoming = new StringReader("");
        var outgoing = new StringWriter();
        RPCLayer layer = new RPCLayer(incoming, outgoing);
        layer.callAsync("doSomething", 1, 1);

        var request = JsonRPC.createRequest("0", "doSomething", 1, 1);
        var requestWH = JsonRPC.addHeader(request);
        Assertions.assertEquals(requestWH, outgoing.toString());
    }

    @Test
    void testIncoming() throws IOException {
        var notification = JsonRPC.createNotification("test", 1, 2, 3, 4);
        var response = JsonRPC.createResponse("1", 2);
        final var incoming = new StringReader(JsonRPC.addHeader(notification) +
                JsonRPC.addHeader(response));
        RPCLayer.JsonStreamListener listener =
            new RPCLayer.JsonStreamListener(incoming, new ArrayBlockingQueue<>(1));
        String first = listener.readMessage();
        Assertions.assertEquals(notification, first);
        String second = listener.readMessage();
        Assertions.assertEquals(response, second);
    }

    /**
     * Regression test for partial reads: a {@link Reader} (e.g. a socket) may
     * return fewer characters than requested per {@code read} call. The listener
     * must keep reading until the whole framed message has arrived; otherwise the
     * message is silently truncated.
     */
    @Test
    void testIncomingWithPartialReads() throws IOException {
        var response = JsonRPC.createResponse("1", 2);
        var notification = JsonRPC.createNotification("test", "some longer payload");
        var incoming = new OneCharAtATimeReader(
            JsonRPC.addHeader(response) + JsonRPC.addHeader(notification));
        RPCLayer.JsonStreamListener listener =
            new RPCLayer.JsonStreamListener(incoming, new ArrayBlockingQueue<>(1));
        Assertions.assertEquals(response, listener.readMessage());
        Assertions.assertEquals(notification, listener.readMessage());
        Assertions.assertNull(listener.readMessage()); // clean EOF
    }

    /**
     * Regression test for back-pressure: when the queue is full the listener must
     * block ({@code put}) rather than throw ({@code add}). With the old
     * {@code add()} the second message threw {@link IllegalStateException} and
     * killed the reader thread, so only the first message was ever delivered.
     * <p>
     * The check is made deterministic by not draining the queue until the
     * listener has had to insert the second message into the full (capacity-1)
     * queue: with {@code put} it parks (thread stays alive), with {@code add} it
     * has already died.
     */
    @Test
    void testListenerBlocksWhenQueueFull() throws Exception {
        var m1 = JsonRPC.addHeader(JsonRPC.createResponse("1", 1));
        var m2 = JsonRPC.addHeader(JsonRPC.createResponse("2", 2));
        var m3 = JsonRPC.addHeader(JsonRPC.createResponse("3", 3));
        var queue = new ArrayBlockingQueue<JsonObject>(1);
        var listener = new RPCLayer.JsonStreamListener(new StringReader(m1 + m2 + m3), queue);

        var thread = new Thread(listener, "test-listener");
        thread.setDaemon(true);
        thread.start();

        // Wait until the first message is queued (slot full) and the listener is
        // attempting the second one, i.e. it has either parked (put) or died (add).
        awaitUntil(() -> queue.size() == 1);
        awaitUntil(() -> !thread.isAlive()
                || thread.getState() == Thread.State.WAITING
                || thread.getState() == Thread.State.TIMED_WAITING);
        Assertions.assertTrue(thread.isAlive(),
            "listener died instead of applying back-pressure on a full queue");

        // Draining now lets the blocked put()s proceed; all three arrive in order.
        Assertions.assertEquals(1, take(queue).get("result").getAsInt());
        Assertions.assertEquals(2, take(queue).get("result").getAsInt());
        Assertions.assertEquals(3, take(queue).get("result").getAsInt());

        thread.join(5000);
        Assertions.assertFalse(thread.isAlive(), "listener thread should finish at EOF");
    }

    private static JsonObject take(ArrayBlockingQueue<JsonObject> queue)
            throws InterruptedException {
        var v = queue.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(v, "expected a message but none arrived");
        return v;
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    @Test
    void testLockingAndReleasing() throws IOException, InterruptedException {
        var response = JsonRPC.addHeader(JsonRPC.createResponse("0", 2));
        var layer = new RPCLayer(new StringReader(response), new StringWriter());
        layer.start(); // starts the thread.
        var result = layer.callSync("calc", 1, 1);
        System.out.println(result);
    }

    /** A {@link Reader} that hands out at most one character per read call. */
    private static final class OneCharAtATimeReader extends Reader {
        private final String data;
        private int pos = 0;

        OneCharAtATimeReader(String data) {
            this.data = data;
        }

        @Override
        public int read(char[] cbuf, int off, int len) {
            if (pos >= data.length()) {
                return -1;
            }
            if (len <= 0) {
                return 0;
            }
            cbuf[off] = data.charAt(pos++);
            return 1;
        }

        @Override
        public void close() {}
    }
}
