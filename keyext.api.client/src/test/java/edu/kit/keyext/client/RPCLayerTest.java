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
