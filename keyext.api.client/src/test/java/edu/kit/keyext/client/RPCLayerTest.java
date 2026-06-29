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
import java.util.function.BooleanSupplier;

import com.google.gson.JsonObject;
import org.key_project.key.api.client.JsonRPC;
import org.key_project.key.api.client.RPCLayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    /**
     * Regression test: a response for an id nobody is waiting on must be ignored,
     * not NPE the message-handler thread. With the old code the handler died on
     * the unknown id and the following legitimate response was never delivered,
     * so {@code callSync} would block forever (caught here by the timeout).
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void handlerSurvivesUnknownResponseId() throws Exception {
        var in = new FeedableReader();
        var layer = new RPCLayer(in, new StringWriter());
        layer.start();

        var feeder = new Thread(() -> {
            sleepQuietly(200); // give callSync time to register its pending id "0"
            in.feed(JsonRPC.addHeader(JsonRPC.createResponse("999", 1)));
            in.feed(JsonRPC.addHeader(JsonRPC.createResponse("0", 42)));
        }, "test-feeder");
        feeder.setDaemon(true);
        feeder.start();

        var result = layer.callSync("calc", 1); // allocated id is "0"
        Assertions.assertEquals(42, result.get("result").getAsInt());
        layer.dispose();
    }

    /**
     * Regression test: {@code dispose()} must stop the message-handler thread,
     * not just the reader thread. With the old code the handler kept polling
     * forever. Measured as a delta so other tests' threads don't interfere.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void disposeStopsHandlerThread() throws Exception {
        int before = countHandlerThreads();
        var layer = new RPCLayer(new FeedableReader(), new StringWriter());
        layer.start();
        Assertions.assertTrue(awaitUntil(() -> countHandlerThreads() == before + 1),
            "handler thread should be running after start()");

        layer.dispose();
        Assertions.assertTrue(awaitUntil(() -> countHandlerThreads() == before),
            "handler thread should stop after dispose()");
    }

    private static int countHandlerThreads() {
        return (int) Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "JSON Message Handler".equals(t.getName()) && t.isAlive())
                .count();
    }

    private static boolean awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * An in-memory {@link Reader} that blocks on read until {@link #feed} supplies
     * more characters. Unlike a {@code PipedReader} it has no writer-thread
     * lifecycle, so a feeder thread may exit without breaking the pipe.
     */
    private static final class FeedableReader extends Reader {
        private final StringBuilder buffer = new StringBuilder();
        private boolean closed = false;

        synchronized void feed(String s) {
            buffer.append(s);
            notifyAll();
        }

        @Override
        public synchronized int read(char[] cbuf, int off, int len) throws IOException {
            while (buffer.length() == 0 && !closed) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            if (buffer.length() == 0 && closed) {
                return -1;
            }
            int n = Math.min(len, buffer.length());
            buffer.getChars(0, n, cbuf, off);
            buffer.delete(0, n);
            return n;
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }
}
