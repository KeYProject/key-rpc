---
title: Getting Started
menuOrder: 2
--- 
# RPC Server and Python Client Guide

This document explains how to start the KeY RPC server and connect to it using the Python client.

## Overview

The KeY RPC system uses JSON-RPC protocol for communication between clients and the KeY verification system. The server can run in multiple modes (TCP server, stdin/stdout, file-based, or WebSocket), and the Python client connects via TCP sockets.

---

## Starting the RPC Server

The server is started using the `StartServer` class from the `org.keyproject.key.api` package. It uses [picocli](https://picocli.info/) for command-line argument parsing.

### Building the Server

First, build the project using Gradle:

```bash
./gradlew :keyext.api:build
```

### Running the Server

Run the server using Java with the compiled classes:

```bash
java -cp <classpath> org.keyproject.key.api.StartServer [options]
```

Or use Gradle to run directly:

```bash
./gradlew :keyext.api:run --args="<options>"
```

---

## Server Communication Modes

The server supports multiple communication modes. Choose the appropriate mode based on your use case.

### 1. TCP Server Mode (Recommended for Python Client)

This mode starts a TCP server that accepts client connections. This is the recommended mode for use with the Python client.

```bash
java -cp <classpath> org.keyproject.key.api.StartServer --server 5151
```

**Options:**
- `--server PORT`: Starts TCP server on the specified port (e.g., 5151)

**Behavior:**
- The server listens on the specified port
- Accepts one client connection at a time
- Allows client reconnection without server restart
- Loaded environments and proofs persist across client disconnections

### 2. Standard Streams Mode

Use stdin/stdout for communication (useful for local scripting):

```bash
java -cp <classpath> org.keyproject.key.api.StartServer --std
```

**Options:**
- `--std`: Use System.in and System.out for communication

### 3. File-Based Mode

Read from and write to files or named pipes:

```bash
java -cp <classpath> org.keyproject.key.api.StartServer --infile input.json --outfile output.json
```

**Options:**
- `--infile FILE`: Read input from a file or named pipe
- `--outfile FILE`: Write output to a file or named pipe

### 4. TCP Client Mode

Connect to an existing server (reverse connection):

```bash
java -cp <classpath> org.keyproject.key.api.StartServer --connectTo 5151
```

**Options:**
- `--connectTo PORT`: Connect to a server running on localhost at the specified port

### 5. WebSocket Mode

Enable WebSocket communication:

```bash
java -cp <classpath> org.keyproject.key.api.StartServer --websocket --server 5151
```

**Options:**
- `--websocket`: Enable WebSocket protocol
- Combine with `--server` for TCP+WebSocket mode

### Additional Options

| Option | Description |
|--------|-------------|
| `--trace` | Enable message tracing (logs to stderr) |
| `-h`, `--help` | Display help message |

---

## Connecting with the Python Client

The Python client connects to the RPC server via TCP sockets.

### Installation

Ensure the Python client dependencies are installed:

```bash
cd keyext.client.python
pip install -r requirements.txt  # if requirements.txt exists
```

### Basic Connection Example

```python
from keyapi import LspEndpoint, LoadParams, StreategyOptions
from keyapi.server import NetKeY, KeYEnv, KeYProof
from keyapi.rpc import JsonRpcEndpoint

def configure_callbacks(key):
    """Register notification handlers for task events."""
    key.register_notification("client/taskStarted", lambda param: print("[KeY Notification] Task started"))
    key.register_notification("client/taskFinished", lambda param: print("[KeY Notification] Task finished"))
    key.register_notification("client/taskProgress", lambda param: print("[KeY Notification] Task progress: ", param))

if __name__ == "__main__":
    # Define the server target (host, port)
    target = ("localhost", 5151)

    # Connect to the server using context manager
    with NetKeY(target) as key:
        # Get server version information
        print(key.meta_version())
        
        # Configure callbacks for notifications
        configure_callbacks(key)

        # Load a Java file for verification
        params = LoadParams(
            "/path/to/your/file.java",  # Path to Java file
            None,  # Optional: contract filter
            None,  # Optional: proof script
            None   # Optional: additional options
        )

        # Create an environment with the loaded file
        with KeYEnv(key, params) as env:
            # Get all contracts found in the file
            contracts = env.contracts()

            print("Found the following contracts: ")
            print("\n".join([("- " + str(c.contractId)) for c in contracts]))

            # Work with the first contract
            i = 0
            with KeYProof(key, contracts[i]) as proof:
                print("Proof for contract: ", contracts[i].contractId)

                # Get the proof tree root node
                root = proof.root()
                print("Root Node: ", root.name)

                # Run automatic proof strategy
                status = proof.auto(StreategyOptions())
                print("Open goals: ", status.openGoals)

    print("Terminating")
```

### Connection Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `target` | tuple | Server address as `(host, port)` |
| `host` | str | Server hostname (default: "localhost") |
| `port` | int | Server port number (default: 5151) |

### Available API Methods

#### Meta API
- `meta_version()` - Get server version information

#### Environment Operations
- `KeYEnv(key, params)` - Create a verification environment
- `env.contracts()` - List all contracts in the loaded file

#### Proof Operations
- `KeYProof(key, contract)` - Create a proof for a specific contract
- `proof.root()` - Get the root node of the proof tree
- `proof.auto(options)` - Run automatic proof strategy

---

## Complete Workflow Example

### Step 1: Start the RPC Server

Open a terminal and start the TCP server:

```bash
cd /home/weigl/work/key-rpc
./gradlew :keyext.api:run --args="--server 5151"
```

Wait for the server to start. You should see:
```
Waiting on port 5151
```

### Step 2: Run the Python Client

In another terminal, run the Python client:

```bash
cd /home/weigl/work/key-rpc/keyext.client.python
python main.py
```

### Step 3: Observe Output

The client will:
1. Connect to the server
2. Print the server version
3. Load the specified Java file
4. List discovered contracts
5. Attempt automatic proof
6. Display open goals (if any)

---

## Troubleshooting

### Connection Refused

If you see "Connection refused" error:
1. Ensure the server is running: `ps aux | grep StartServer`
2. Verify the port is correct (default: 5151)
3. Check for firewall rules blocking the port

### Server Not Starting

If the server fails to start:
1. Check if the port is already in use: `netstat -tlnp | grep 5151`
2. Ensure Java is installed: `java -version`
3. Verify the classpath includes all dependencies

### Python Import Errors

If Python imports fail:
1. Ensure you're in the `keyext.client.python` directory
2. Install required dependencies
3. Check PYTHONPATH includes the client directory

### Using Different Ports

To use a different port, modify both server and client:

**Server:**
```bash
java -cp <classpath> org.keyproject.key.api.StartServer --server 8080
```

**Client:**
```python
target = ("localhost", 8080)
```

---

## Architecture Overview

```
┌─────────────────┐      JSON-RPC       ┌─────────────────┐
│  Python Client  │ ◄─────────────────► │   KeY Server    │
│  (NetKeY)       │     TCP Socket      │  (StartServer)  │
│                 │                     │                 │
│  - rpc.py       │                     │  - KeyApiImpl   │
│  - server.py    │                     │  - Launcher     │
│  - keydata.py   │                     │  - Gson         │
└─────────────────┘                     └─────────────────┘
```

### Message Flow

1. Client establishes TCP connection to server
2. Server accepts connection and creates JSON-RPC launcher
3. Client sends RPC requests (load file, get contracts, prove, etc.)
4. Server processes requests and returns responses
5. Server may send notifications (task started/finished/progress)

---

## Security Considerations

- The server binds to localhost by default (not accessible from external networks)
- No authentication is required for local connections
- For production use, consider implementing authentication
- Do not expose the RPC port to untrusted networks

---

## Related Files

| File | Purpose |
|------|---------|
| `keyext.api/src/main/java/org/keyproject/key/api/StartServer.java` | Server entry point |
| `keyext.api/src/main/java/org/keyproject/key/api/KeyApiImpl.java` | Server implementation |
| `keyext.api/src/main/java/org/keyproject/key/api/remoteclient/ClientApi.java` | Client interface |
| `keyext.client.python/main.py` | Python client example |
| `keyext.client.python/keyapi/server.py` | Python server wrapper |
| `keyext.client.python/keyapi/rpc.py` | Python RPC layer |