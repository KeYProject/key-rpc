import enum
import json
import threading
import typing
from typing import Dict

from keyapi import KEY_DATA_CLASSES, KEY_DATA_CLASSES_REV

LEN_HEADER = "Content-Length: "
TYPE_HEADER = "Content-Type: "


class MyEncoder(json.JSONEncoder):
    """
    Encodes an object in JSON
    """

    def default(self, o):  # pylint: disable=E0202
        d = dict(o.__dict__)
        d['$class'] = KEY_DATA_CLASSES_REV[type(o).__name__]
        return d


class ResponseError(Exception):
    def __init__(self, error_code, message, data=None):
        super().__init__(message)
        self.error_code = error_code
        self.data = data


class ErrorCodes(enum.Enum):
    MethodNotFound = None
    ParseError = 1


class JsonRpcEndpoint(object):
    '''
    Thread safe JSON RPC endpoint implementation. Responsible to recieve and send JSON RPC messages, as described in the
    protocol. More information can be found: https://www.jsonrpc.org/
    '''

    def __init__(self, stdin, stdout):
        self.stdin = stdin
        self.stdout = stdout
        self.read_lock = threading.Lock()
        self.write_lock = threading.Lock()

    @staticmethod
    def __add_header(content_bytes):
        '''
        Prepends the JSON-RPC framing header to an already UTF-8 encoded body.

        The ``Content-Length`` value is the number of *bytes* of the body (per
        the LSP base protocol), not the number of characters. Counting
        characters truncates every message that contains a non-ASCII glyph
        (common in KeY terms), so the header must be computed from the encoded
        bytes.

        :param bytes content_bytes: the UTF-8 encoded JSON body
        :return: the framed message as bytes
        '''
        header = "%s%d\r\n\r\n" % (LEN_HEADER, len(content_bytes))
        return header.encode("ascii") + content_bytes

    def send_request(self, message):
        '''
        Sends the given message.

        :param dict message: The message to send.
        '''
        json_string = json.dumps(message, cls=MyEncoder)
        content_bytes = json_string.encode("utf-8")
        jsonrpc_req = self.__add_header(content_bytes)
        with self.write_lock:
            self.stdout.write(jsonrpc_req)
            self.stdout.flush()

    def _read_exactly(self, count):
        '''
        Reads exactly ``count`` bytes, looping until they have all arrived.

        ``read(n)`` on a socket stream may legally return fewer than ``n`` bytes,
        so a single ``read`` is not enough to consume a framed message.

        :return: the bytes, or ``None`` if EOF is reached first
        '''
        chunks = []
        remaining = count
        while remaining > 0:
            chunk = self.stdin.read(remaining)
            if not chunk:
                return None
            chunks.append(chunk)
            remaining -= len(chunk)
        return b"".join(chunks)

    def recv_response(self) -> object:
        '''
        Receives a message. Expects the input stream to be binary.

        :return: a message, or ``None`` when the stream has reached EOF
        '''
        with self.read_lock:
            message_size = None
            while True:
                # read header
                line = self.stdin.readline()
                if not line:
                    # server quit
                    return None
                if not line.endswith(b"\r\n"):
                    raise ResponseError(ErrorCodes.ParseError, "Bad header: missing newline")
                # remove the "\r\n" and decode the (ASCII) header line
                line = line[:-2].decode("ascii")
                if line == "":
                    # done with the headers
                    break
                elif line.startswith(LEN_HEADER):
                    line = line[len(LEN_HEADER):]
                    if not line.isdigit():
                        raise ResponseError(ErrorCodes.ParseError,
                                            "Bad header: size is not int")
                    message_size = int(line)
                elif line.startswith(TYPE_HEADER):
                    # nothing todo with type for now.
                    pass
                else:
                    raise ResponseError(ErrorCodes.ParseError, "Bad header: unkown header")
            if not message_size:
                raise ResponseError(ErrorCodes.ParseError, "Bad header: missing size")

            # Content-Length counts bytes, so read bytes (not characters) and
            # only then decode as UTF-8.
            body = self._read_exactly(message_size)
            if body is None:
                # EOF in the middle of a message
                return None
            return json.loads(body.decode("utf-8"), object_hook=object_decoder)


def object_decoder(obj):
    if type(obj) is list:
        return [object_decoder(item) for item in obj]
    if type(obj) is dict:
        for k,v in obj.items():
            obj[k] = object_decoder(v)
        if '$class' in obj:
            class_name = obj["$class"]
            del obj["$class"]
            return KEY_DATA_CLASSES[class_name](**obj)
    return obj


class LspEndpoint(threading.Thread):
    def __init__(self, json_rpc_endpoint: JsonRpcEndpoint, method_callbacks=None, notify_callbacks=None, timeout=2000):
        super().__init__()
        self.json_rpc_endpoint: JsonRpcEndpoint = json_rpc_endpoint
        self.notify_callbacks: Dict = notify_callbacks or {}
        self.method_callbacks: Dict = method_callbacks or {}
        self.event_dict = {}
        self.response_dict = {}
        self.next_id = 0
        self._id_lock = threading.Lock()
        self._timeout = timeout
        self.shutdown_flag = False

    def handle_result(self, rpc_id, result, error):
        self.response_dict[rpc_id] = (result, error)
        cond = self.event_dict[rpc_id]
        cond.acquire()
        cond.notify()
        cond.release()

    def stop(self):
        self.shutdown_flag = True

    def run(self):
        rpc_id = None
        while not self.shutdown_flag:
            try:
                jsonrpc_message = self.json_rpc_endpoint.recv_response()
                if jsonrpc_message is None:
                    print("server quit")
                    break
                method = jsonrpc_message.get("method")
                result = jsonrpc_message.get("result")
                error = jsonrpc_message.get("error")
                rpc_id = jsonrpc_message.get("id")
                params = jsonrpc_message.get("params")

                if method:
                    if rpc_id:
                        # a call for method
                        if method not in self.method_callbacks:
                            raise ResponseError(ErrorCodes.MethodNotFound,
                                                "Method not found: {method}".format(method=method))
                        result = self.method_callbacks[method](params)
                        self.send_response(rpc_id, result, None)
                    else:
                        # a call for notify
                        if method not in self.notify_callbacks:
                            # Have nothing to do with this.
                            print("Notify method not found: {method}.".format(method=method))
                        else:
                            self.notify_callbacks[method](params)
                else:
                    self.handle_result(rpc_id, result, error)
            except ResponseError as e:
                self.send_response(rpc_id, None, e)

    def send_response(self, id, result, error):
        message_dict = {"jsonrpc": "2.0", "id": id}
        if result:
            message_dict["result"] = result
        if error:
            message_dict["error"] = error
        self.json_rpc_endpoint.send_request(message_dict)

    def send_message(self, method_name, params, id=None):
        message_dict = {}
        message_dict["jsonrpc"] = "2.0"
        if id is not None:
            message_dict["id"] = id
        message_dict["method"] = method_name
        message_dict["params"] = params
        # message_dict["$class"] = "org.eclipse.lsp4j.jsonrpc.messages.Message"
        self.json_rpc_endpoint.send_request(message_dict)

    def call_method(self, method_name, args):
        # Allocate the request id atomically: concurrent callers must never
        # share an id, otherwise their entries in event_dict/response_dict
        # collide and a response gets delivered to the wrong caller.
        with self._id_lock:
            current_id = self.next_id
            self.next_id += 1
        cond = threading.Condition()
        self.event_dict[current_id] = cond

        cond.acquire()
        self.send_message(method_name, args, current_id)
        if self.shutdown_flag:
            return None

        if not cond.wait(timeout=self._timeout):
            raise TimeoutError()
        cond.release()

        self.event_dict.pop(current_id)
        result, error = self.response_dict.pop(current_id)
        if error:
            raise ResponseError(error.get("code"), error.get("message"), error.get("data"))
        return result

    def send_notification(self, method_name, kwargs):
        self.send_message(method_name, kwargs)


class ServerBase:
    def __init__(self, endpoint: LspEndpoint):
        self.endpoint = endpoint

    def _call_sync(self, method_name: str, param: typing.List[object]) -> object:
        resp = self.endpoint.call_method(method_name, param)
        return resp

    def _call_async(self, method_name: str, param: typing.List[object]):
        self.endpoint.send_notification(method_name, param)
