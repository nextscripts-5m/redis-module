# Chapter 1 - Redis Fundamentals

## Why Redis in Microservice Architectures

Redis is commonly introduced as a "fast key-value store", but in distributed systems it is better understood as an architectural component that trades memory cost for latency and operational simplicity.

Typical microservice use cases include:

- low-latency caching for read-heavy endpoints,
- counters and lightweight coordination,
- session storage and short-lived state,
- event buffering and stream-like workflows.

The key design question is not only "Can Redis store this data?" but "Which consistency and durability guarantees does this service actually need?"

## Execution Model and Core Architecture

Redis is an in-memory data store using an event-driven model. Commands are processed with single-threaded execution semantics for the command path, which provides a very useful property: each command is atomic at the server level.

### Why this matters

- No per-command locking complexity for clients.
- Predictable behavior for read-modify-write patterns that fit in one command.
- Potential bottlenecks when one key becomes hot or when expensive commands block the event loop.

This model is ideal for short, bounded operations and high request rates. It is less ideal for long-running workloads that require complex cross-key transactional guarantees.

## Latency Hierarchy: Why RAM Changes the Game

A practical way to motivate Redis is the latency gap between:

- CPU cache,
- DRAM,
- SSD/HDD I/O,
- network round-trips.

Even without exact numbers, the order of magnitude differences explain why moving hot-path reads from disk-backed stores to RAM can significantly reduce API latency and variance.

This perspective aligns with the course baseline notes and helps students reason about where Redis belongs in an architecture:

- close to the request path for latency-sensitive reads,
- not as a blind replacement for durable system-of-record databases.

Reference: [learn-microservices Redis slide](https://github.com/nbicocchi/learn-microservices/blob/main/modules/resiliency/slides/3%20-%20Redis.md)

## Data Model and Native Data Structures

Redis stores key-value pairs, where the value can be a rich native type. Choosing the right type is a design decision, not just a syntax preference.

### String

- Best for simple values, JSON blobs, counters, and flags.
- Typical commands: `SET`, `GET`, `INCR`, `DECR`.

### Hash

- Best for compact objects with multiple fields (e.g., user profile metadata).
- Typical commands: `HSET`, `HGET`, `HGETALL`.

### List

- Best for ordered sequences and simple queue patterns.
- Typical commands: `LPUSH`, `RPUSH`, `LPOP`, `LRANGE`.

### Set

- Best for uniqueness and membership checks.
- Typical commands: `SADD`, `SMEMBERS`, `SISMEMBER`, `SCARD`.

### Sorted Set

- Best for ranking, scoreboards, and priority-like retrieval.
- Typical commands: `ZADD`, `ZRANGE`, `ZREVRANGE`.

### Stream

- Best for append-only event flows with consumer groups and acknowledgments.
- Typical commands: `XADD`, `XREADGROUP`, `XACK`.

### Bitmap and HyperLogLog

- Bitmap: efficient bit-level tracking (feature flags, daily active markers).
- HyperLogLog: approximate cardinality at very low memory cost.

## Command Walkthrough (Foundational)

These examples are intentionally minimal and can be reused in the chapter lab.

```bash
# Key-value
SET name "pippo"
GET name
EXISTS name
DEL name

# List
LPUSH bikes:repairs bike:1 bike:2
LRANGE bikes:repairs 0 -1
LPOP bikes:repairs

# Set
SADD social:media Facebook Twitter WhatsApp
SMEMBERS social:media
SCARD social:media

# Hash
HSET user:123 name "Charlie"
HSET user:123 country "USA"
HGETALL user:123
HDEL user:123 country
```

## Atomicity, Transactions, and Concurrency Control

### Single-command atomicity

Every Redis command executes atomically. This is often enough for counters and many update patterns.

### MULTI / EXEC transactions

`MULTI` and `EXEC` let clients queue commands and execute them atomically as a block.

```bash
MULTI
SET order:1:status "CREATED"
HSET order:1 total "39.90"
EXEC
```

### WATCH for optimistic concurrency

`WATCH` can protect read-modify-write flows from concurrent updates. If watched keys change before `EXEC`, the transaction is aborted.

Important clarification for students: Redis transactions are not equivalent to full ACID relational transactions with rollback semantics across arbitrary failure modes.

## TTL, Expiration, and Eviction

### TTL and expiration

TTL defines the validity window of a key. This is critical for:

- cache freshness policies,
- temporary tokens/sessions,
- bounded lifecycle data.

Typical commands: `EXPIRE`, `TTL`, `SETEX`.

### Eviction policies

When memory is constrained, Redis applies the configured eviction policy. Common policies include:

- `noeviction`,
- `allkeys-lru`,
- `volatile-lru`,
- `allkeys-random`.

Architectural implication: eviction strategy must match business semantics. For example, evicting session keys and evicting cache keys have very different consequences.

## Persistence Modes and Trade-offs

Redis supports different durability profiles:

- **No persistence**: best performance, data lost on restart.
- **RDB snapshots**: periodic point-in-time dumps, fast restart, possible data loss between snapshots.
- **AOF**: append write operations for stronger durability, higher I/O overhead.
- **Hybrid (RDB + AOF)**: combines faster recovery with improved durability in many production setups.

Selection depends on Recovery Point Objective (RPO), Recovery Time Objective (RTO), and workload characteristics.

## Deployment Models (Preview)

### Single node

- simplest setup,
- limited by one node's memory/CPU,
- single point of failure.

### Replication

- improves read scalability,
- does not horizontally scale write throughput by itself.

### Sentinel

- adds monitoring and automatic failover.

### Cluster

- shards data across nodes for horizontal scalability and higher aggregate throughput.

Detailed failover, partitioning, and observability topics are expanded in Chapter 5.

## Key Takeaways

- Redis performance is primarily a latency-architecture story, not only a feature story.
- Data structure choice should encode business semantics (order, uniqueness, ranking, replay).
- Atomic commands are powerful, but transactional expectations must stay realistic.
- TTL, eviction, and persistence are first-class design decisions.
- Deployment topology changes operational guarantees and failure behavior.
