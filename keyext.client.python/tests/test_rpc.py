# This file is part of KeY - https://key-project.org
# KeY is licensed under the GNU General Public License Version 2
# SPDX-License-Identifier: GPL-2.0-only
"""Regression tests for the JSON-RPC transport (keyapi.rpc).

Run with::

    python3 -m unittest discover -s tests -t .

from the ``keyext.client.python`` directory.
"""
import io
import queue
import threading
import unittest

from keyapi.rpc import JsonRpcEndpoint, LspEndpoint

# Logic symbols / umlauts as they appear in KeY terms. Each of these is more
# than one byte in UTF-8, so byte length and character length differ.
UNICODE = "∀x; (x = x) — Grüße ∃∈≤"

# Direct handle to the name-mangled static framing helper.
_add_header = JsonRpcEndpoint._JsonRpcEndpoint__add_header


def _frame_server_side(json_text):
    """Frame a JSON body the way the Gson-based server does: raw UTF-8 on the
    wire with a byte-counted Content-Length. This is the input the client must
    be able to read back."""
    body = json_text.encode("utf-8")
    return ("Content-Length: %d\r\n\r\n" % len(body)).encode("ascii") + body


class _DripBytesIO(io.BytesIO):
    """A BytesIO that returns at most ``chunk`` bytes per ``read`` call, to
    mimic the short reads a real socket may hand back."""

    def __init__(self, data, chunk=1):
        super().__init__(data)
        self._chunk = chunk

    def read(self, size=-1):
        if size is None or size < 0:
            return super().read(size)
        return super().read(min(size, self._chunk))


class _EchoEndpoint:
    """In-memory transport for LspEndpoint: every request is echoed back as a
    response whose ``result`` is the request id."""

    def __init__(self):
        self.sent = []
        self._lock = threading.Lock()
        self._responses = queue.Queue()

    def send_request(self, message):
        with self._lock:
            self.sent.append(message)
        if message.get("id") is not None and "method" in message:
            self._responses.put(
                {"jsonrpc": "2.0", "id": message["id"], "result": message["id"]})

    def recv_response(self):
        return self._responses.get()


class WriteFramingTest(unittest.TestCase):
    def test_add_header_uses_byte_length(self):
        # #6: Content-Length must count UTF-8 bytes, not characters. "∀é" is
        # 2 characters but 5 bytes (3 + 2); a char count would advertise 2.
        body = "∀é".encode("utf-8")
        self.assertEqual(len(body), 5)
        framed = _add_header(body)
        header, sep, rest = framed.partition(b"\r\n\r\n")
        self.assertEqual(header, b"Content-Length: 5")
        self.assertEqual(rest, body)

    def test_send_request_advertises_body_byte_length(self):
        out = io.BytesIO()
        JsonRpcEndpoint(io.BytesIO(), out).send_request(
            {"jsonrpc": "2.0", "id": 1, "params": [UNICODE]})
        header, body = out.getvalue().split(b"\r\n\r\n", 1)
        advertised = int(header[len(b"Content-Length: "):])
        self.assertEqual(advertised, len(body))


class ReadFramingTest(unittest.TestCase):
    def test_read_raw_utf8_body(self):
        # #6: a server message carrying raw multi-byte UTF-8 must decode
        # correctly. The old code read message_size *characters* from a text
        # stream and corrupted anything past the first multi-byte glyph.
        text = '{"jsonrpc": "2.0", "id": 2, "result": "' + UNICODE + '"}'
        ep = JsonRpcEndpoint(io.BytesIO(_frame_server_side(text)), io.BytesIO())
        msg = ep.recv_response()
        self.assertEqual(msg["result"], UNICODE)
        self.assertEqual(msg["id"], 2)

    def test_read_back_to_back_raw_utf8(self):
        # #6: framing two multi-byte messages must keep them aligned; reading
        # the wrong number of bytes for the first bleeds into the second.
        t1 = '{"jsonrpc": "2.0", "id": 1, "result": "first ∀∃"}'
        t2 = '{"jsonrpc": "2.0", "id": 2, "result": "second é≤"}'
        buf = _frame_server_side(t1) + _frame_server_side(t2)
        ep = JsonRpcEndpoint(io.BytesIO(buf), io.BytesIO())
        self.assertEqual(ep.recv_response()["result"], "first ∀∃")
        self.assertEqual(ep.recv_response()["result"], "second é≤")
        self.assertIsNone(ep.recv_response())  # clean EOF

    def test_partial_reads_are_assembled(self):
        # #6: read() may return fewer bytes than requested; the body must still
        # be reassembled in full before being decoded.
        text = '{"jsonrpc": "2.0", "id": 7, "result": "' + UNICODE + '"}'
        ep = JsonRpcEndpoint(_DripBytesIO(_frame_server_side(text), chunk=1),
                             io.BytesIO())
        self.assertEqual(ep.recv_response()["result"], UNICODE)


class ConcurrencyTest(unittest.TestCase):
    def test_unique_ids_under_concurrency(self):
        # #7: concurrent call_method invocations must each get a distinct id and
        # all complete without a TimeoutError caused by a collided id.
        n = 32
        endpoint = LspEndpoint(_EchoEndpoint(), timeout=10)
        endpoint.daemon = True
        endpoint.start()

        results = [None] * n
        errors = []
        barrier = threading.Barrier(n)

        def worker(i):
            try:
                barrier.wait()
                results[i] = endpoint.call_method("ping", [i])
            except Exception as exc:  # noqa: BLE001 - recorded for assertion
                errors.append(exc)

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(n)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=15)

        endpoint.stop()
        self.assertEqual(errors, [])
        # Every worker got a response back (the echoed id), none timed out.
        self.assertTrue(all(r is not None for r in results))
        sent_ids = [m["id"] for m in endpoint.json_rpc_endpoint.sent]
        self.assertEqual(len(sent_ids), n)
        self.assertEqual(len(set(sent_ids)), n, "request ids must be unique")


if __name__ == "__main__":
    unittest.main()
