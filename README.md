# Blockchain Payment Network

A distributed cryptocurrency system built from scratch, implementing a blockchain-inspired payment network with cryptographic security, atomic broadcast ordering, and fault-tolerant replication.

---

## Overview

Blockchain Payment Network is a multi-node distributed ledger where clients submit signed wallet operations (create, delete, transfer) that are totally ordered by a sequencer, batched into cryptographically signed blocks, and replicated across all nodes. Each node independently verifies block integrity and applies transactions deterministically, ensuring consistent state across the network.

The system handles node failures transparently, supports dynamic node joins via state replay, and guarantees exactly-once execution through request deduplication.

---

## Architecture

```
┌─────────────┐         gRPC          ┌──────────────────┐
│   Client    │ ──── Broadcast ──────▶│    Sequencer     │
│             │                       │                  │
│  Signs each │                       │  Orders txns     │
│  request    │                       │  Batches blocks  │
│  with RSA   │                       │  Signs blocks    │
└─────────────┘                       └────────┬─────────┘
       │                                       │
       │  gRPC (wallet ops)           DeliverBlock (polling)
       │                                       │
       ▼                              ┌────────▼─────────┐
┌─────────────┐                       │   Node (replica) │
│  NodeService│◀──────────────────────│                  │
│  (gRPC)     │                       │  Verifies block  │
│             │                       │  signature       │
│  Verifies   │                       │  Applies txns    │
│  request    │                       │  in order        │
│  signature  │                       └──────────────────┘
│  Executes   │
│  or rejects │
└─────────────┘
```

**Modules:**


| Module      | Responsibility                                                   |
| ----------- | ---------------------------------------------------------------- |
| `contract`  | Protocol Buffer definitions and shared crypto utilities          |
| `sequencer` | Total ordering service — batches transactions into signed blocks |
| `node`      | Replica — verifies, applies, and serves blockchain state         |
| `client`    | Interactive CLI — signs and submits wallet operations            |
| `crypto`    | RSA key generation utilities                                     |


---

## Key Technical Features

### Atomic Broadcast with Block Batching

The sequencer implements a two-policy block closing strategy:

- **Size-based:** closes a block after N transactions (default: 4)
- **Time-based:** closes a block after T seconds from the first transaction (default: 5s)

Nodes poll the sequencer with blocking calls (`waitForBlock`) and apply blocks sequentially, ensuring all replicas process the same ordered transaction log.

### Cryptographic Security (RSA-2048 / SHA-256)

- **Block signing:** The sequencer signs every closed block with its RSA private key. Nodes reject any block with an invalid or missing signature.
- **Request authentication:** Clients sign every wallet operation with their RSA private key. Nodes verify the signature against the user's registered public key before forwarding to the sequencer.
- Keys are stored in DER format (PKCS#8 private, X.509 public) and loaded at runtime from module resources.

### Exactly-Once Execution via Idempotency

Every client command carries a `requestId = clientUUID + commandNumber`. Nodes cache execution outcomes by `requestId`, so retried requests (after failover or timeout) return the cached result without re-executing the transaction.

### Fault Tolerance & Failover

- **Client failover:** Automatically retries across all known nodes in round-robin on any gRPC error.
- **Node bootstrap:** New nodes replay all existing blocks from the sequencer before accepting requests, reaching consistent state within 30 seconds.
- **Sequencer unavailability:** Nodes retry with exponential backoff (100ms → 2s) and reduce polling frequency after repeated failures.

### Concurrency Model

- `NodeState` is a synchronized state machine — all wallet operations are thread-safe.
- `ApplicationPipeline` runs as a dedicated daemon thread, pulling and applying blocks sequentially.
- Write operations register a `CompletableFuture` that is completed by the pipeline once the transaction is applied, with a 60-second timeout.

### Non-Blocking Operations

The client supports both blocking (wait for confirmation) and async (fire-and-forget) variants of all write operations, implemented with gRPC `StreamObserver`s.

---

## Tech Stack


| Layer        | Technology                                                           |
| ------------ | -------------------------------------------------------------------- |
| Language     | Java 17                                                              |
| Build        | Apache Maven 3.8 (multi-module)                                      |
| RPC          | gRPC 1.60 + Protocol Buffers 3.25                                    |
| Networking   | Netty (shaded)                                                       |
| Cryptography | Java Security (RSA-2048, SHA256withRSA)                              |
| Concurrency  | `CompletableFuture`, `ConcurrentHashMap`, `ScheduledExecutorService` |


---

## Getting Started

### Prerequisites

```bash
javac -version   # Java 17+
mvn -version     # Maven 3.8+
openssl version  # For key generation
```

### Build

```bash
mvn clean install
```

### Generate Cryptographic Keys

```bash
./genkeys.sh Seq    # Sequencer key pair
./genkeys.sh Alice  # User key pairs
./genkeys.sh Bob
```

### Run

**Sequencer** (port 3001, blocks of 4 txns or 5s timeout):

```bash
cd sequencer
mvn exec:java -Dexec.args="3001 4 5"
```

**Node** (port 2001, organization OrgA, connecting to sequencer):

```bash
cd node
mvn exec:java -Dexec.args="2001 OrgA localhost:3001"
```

**Client** (connecting to node):

```bash
cd client
mvn exec:java -Dexec.args="localhost:2001:OrgA"
```

### Client Commands


| Command                                          | Operation                 | Mode     |
| ------------------------------------------------ | ------------------------- | -------- |
| `C <userId> <walletId> <node> <delay>`           | Create wallet             | Blocking |
| `c <userId> <walletId> <node> <delay>`           | Create wallet             | Async    |
| `T <userId> <from> <to> <amount> <node> <delay>` | Transfer                  | Blocking |
| `t <userId> <from> <to> <amount> <node> <delay>` | Transfer                  | Async    |
| `S <walletId> <node>`                            | Read balance              | —        |
| `B`                                              | Get full blockchain state | —        |


---

## Testing

The `tests/` directory contains a shell-based test harness with input command sequences and expected output files.

```bash
cd tests
./run_tests.sh
```

Test cases cover wallet creation/deletion, fund transfers, insufficient balance errors, and concurrent operations.

---

## Project Structure

```
A46-BlockchainIST-2026/
├── contract/src/main/
│   ├── proto/              # gRPC service & message definitions
│   └── java/.../crypto/    # CryptoUtils (sign, verify, key loading)
├── sequencer/src/main/java/
│   ├── SequencerMain.java
│   └── domain/
│       ├── SequencerState.java     # Block batching & ordering
│       └── BlockSigner.java        # RSA block signing
├── node/src/main/java/
│   ├── NodeMain.java
│   └── domain/
│       ├── NodeState.java                  # Wallet state machine
│       ├── ApplicationPipeline.java        # Block processing thread
│       ├── BlockSignatureVerifier.java     # Block integrity check
│       └── RequestSignatureVerifier.java   # Client auth
├── client/src/main/java/
│   ├── ClientMain.java
│   ├── CommandProcessor.java       # Interactive shell + signing
│   └── ClientNodeService.java      # gRPC stubs + failover
├── crypto/                         # Key generation utilities
├── tests/                          # Automated test harness
├── genkeys.sh                      # OpenSSL key generation script
└── pom.xml                         # Multi-module Maven build
```

---

## Authors


| Name               | GitHub                                       |
| ------------------ | -------------------------------------------- |
| Guilherme Monteiro | [@Monteir016](https://github.com/Monteir016) |
| Manuel Semedo      | [@semedooo](https://github.com/semedooo)     |


