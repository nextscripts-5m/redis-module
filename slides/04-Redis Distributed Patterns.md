# Chapter 4 - Redis Distributed Patterns

- [Chapter 4 - Redis Distributed Patterns](#chapter-4---redis-distributed-patterns)
  - [Why Redis for coordination](#why-redis-for-coordination)
  - [Distributed locks](#distributed-locks)
    - [Minimal pattern: SET NX with expiry](#minimal-pattern-set-nx-with-expiry)
    - [Correctness caveats](#correctness-caveats)
    - [Fencing tokens and the system of record](#fencing-tokens-and-the-system-of-record)
  - [Rate limiting](#rate-limiting)
    - [Fixed window (counter + TTL)](#fixed-window-counter--ttl)
    - [Sliding window log](#sliding-window-log)
    - [Token bucket on Redis](#token-bucket-on-redis)
  - [Session sharing](#session-sharing)
  - [Idempotency keys](#idempotency-keys)
  - [Reference](#reference)

---

## Why Redis for coordination

In a distributed deployment, several replicas of the same service run concurrently. Without a shared notion of **who may act now**, **how often**, or **what was already done**, you get duplicate work, race conditions, and inconsistent user-visible state.

Redis is often used as a **low-latency coordination store**: not the system of record for business entities, but a place to hold **leases**, **counters**, **sessions**, **rankings**, and **deduplication keys** with well-understood primitives (strings, hashes, sorted sets). **Redis makes coordination easy; making it correct under failure and clock skew is the hard part.**

---

## Distributed locks

A **distributed lock** grants exclusive access to a resource (a row, a file, a shard, an external API) across processes and machines. The naive goal is mutual exclusion; the engineering goal is **bounded exclusivity** that fails safe when clients or Redis misbehave.

### Minimal pattern: SET NX with expiry

The usual Redis pattern is a single key whose value identifies the **owner** (e.g. UUID), created only if absent, with a **TTL** that acts as a lease:

```text
SET lock:orders:123 <owner-uuid> NX PX 30000
```

- **NX** — set only if the key does not exist (atomic “test and set”).
- **PX** — expiry in milliseconds so a crashed holder cannot block the lock forever.

Release must be **conditional**: delete the key only if the value still matches the owner. Otherwise you can delete another process’s lock after your lease expired and you were slow.

```mermaid
sequenceDiagram
  participant A as Client A
  participant R as Redis
  participant B as Client B

  A->>R: SET lock:k ownerA NX PX ttl
  R-->>A: OK
  B->>R: SET lock:k ownerB NX PX ttl
  R-->>B: (nil) blocked
  Note over A,R: A completes work
  A->>R: if GET==ownerA then DEL
  R-->>A: released
  B->>R: SET lock:k ownerB NX PX ttl
  R-->>B: OK
```



### Correctness caveats


| Risk              | What goes wrong                                                                          | Mitigation idea                                                                               |
| ----------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| **TTL too short** | Work still running; lock expires; another client enters → **double execution**.          | **extend lease** in-process if safe; or **short lock + idempotent** downstream.               |
| **TTL too long**  | Crash before release → others wait until expiry.                                         | Trade availability vs safety; monitor lock wait time.                                         |
| **No fencing**    | Stale holder finishes after lease expiry and **writes anyway** → last writer wins chaos. | **Fencing token** (monotonic) accepted only if greater than last seen by storage (see below). |


Locks **serialize** work; they do not magically make **non-atomic multi-step workflows** safe across different stores.

### Fencing tokens and the system of record

**Fencing** means the protected resource (database, object store) rejects late writes from an **expired** lock holder.

1. Before taking the lock, read a **current max token** from the system of record (or a dedicated Redis counter with `INCR`).
2. Acquire lock; attach **token** to each mutation.
3. Storage applies writes only if `incoming_token > last_committed_token`.

Redis can hold the lock; the **authority** that enforces ordering should be the component that must stay consistent (often the database with a conditional update or version column).

---

## Rate limiting

**Rate limiting** bounds how many requests per key (user, IP, tenant, API key) are allowed in a time window. Redis fits because operations are **fast** and **atomic** at key granularity.

### Fixed window (counter + TTL)

The **fixed-window counter** splits time into **fixed intervals** (seconds, minutes, hours). For each interval you count requests for a **scope** (user id, IP, tenant, API key). If the count **exceeds** a predefined limit, requests are **blocked** until the **next** interval—like a speed limit: exceed it → red light until the window opens again.

![](./images/redis-fixed-window.png)

**Four steps**

1. **Define the window** — e.g. 1 second or 60 seconds (`windowMs`).
2. **Track requests** — a single **counter** per `(scope, window)` in Redis.
3. **Reset** — when the window ends, the counter is discarded or recreated (typically **TTL on the key**).
4. **Enforce** — if `count >= limit`, deny; otherwise allow and increment.

**Key design** — one Redis key per scope (e.g. `ratelimit:<clientId>`, optionally plus route or tenant), so counters for different callers do not overwrite each other.

**Decision flow (each incoming request)**

1. **Read** the current count for that scope (if the key does not exist, treat the count as **0**).
2. If the count is already **≥ limit** → **deny** (policy choice: skip increment or still record—most APIs simply reject).
3. Otherwise **allow** this request: **increment** the counter and attach a **TTL** that matches the window length so the key disappears when the window ends (next window starts fresh).

**Why the TTL must not “slide”** — the expiry defines when the window **closes**. If you **reset TTL on every request**, the key keeps living longer and the window **never** aligns with clock time anymore. The usual fix is to set expiry **only on the first write of the window** (in Redis: `PEXPIRE … NX`).

`**PEXPIRE key milliseconds`** — sets the key’s **remaining lifetime** in milliseconds; when it expires, Redis deletes the key so the counter resets for the next clock window.

`**PEXPIRE key ms NX`** — applies that TTL **only if the key does not already have one**. If a TTL is already counting down, the command **does not** extend or replace it. That pins the window end on the **first** write of the interval instead of **sliding** the deadline on every request.

**Why atomicity matters** — many app replicas may call Redis at once. Separating “read count” and “write count” in two unrelated round-trips opens a **race** (two instances both see `4`, both increment, you overshoot the limit).

```mermaid
sequenceDiagram
  participant C as Client
  participant S as App (limiter)
  participant R as Redis

  C->>S: request
  S->>R: GET ratelimit:alice
  R-->>S: nil (treat as 0)
  Note over S: count < limit
  Note over S,R: atomic batch (INCR + PEXPIRE NX)
  S->>R: INCR ratelimit:alice
  S->>R: PEXPIRE ratelimit:alice windowMs NX
  R-->>S: OK
  S-->>C: allowed
```



```mermaid
sequenceDiagram
  participant C as Client
  participant S as App
  participant R as Redis

  C->>S: request
  S->>R: GET ratelimit:alice
  R-->>S: "5"
  Note over S: count >= limit (e.g. 5)
  S-->>C: denied
```



**Edge burst** — the limit is “per **clock** window”, not “per **any** sliding span of length T”. At the **boundary** between two windows, a client can send **almost 2× the nominal rate** in real time (e.g. 100 at the end of second A + 100 at the start of second B).

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant R as Redis
  Note over C,R: Limit 100 req / 1s fixed windows on wall clock
  C->>R: burst 100 reqs (end of window T0)
  R-->>C: OK
  C->>R: burst 100 reqs (start of window T1)
  R-->>C: OK
  Note over C,R: Observer spanning T0→T1 can see ~200 accepts in ~1s of real time
```



### Sliding window log

Unlike a **fixed window**, the limit applies to a **rolling** interval: “how many requests in the **last** *T* seconds?”, so enforcement reacts **immediately** as old events age out—no waiting for the next calendar bucket.

![](./images/redis-sliding-window.png)

**Four steps**

1. **Choose *T*** — the rolling horizon (e.g. 1 minute).
2. **Log each allowed request** — record something unique per event (timestamp and/or id).
3. **Drop what is outside the window** — remove entries older than `now − T`.
4. **Count and enforce** — if in-window count **≥ limit**, block; otherwise allow and append to the log.

**Two Redis shapes (same idea, different primitives)**

- **Classic — sorted set (`ZSET`)** — score = **epoch ms** (or comparable ordering), member = unique id (`ZADD`). Trim with `ZREMRANGEBYSCORE` (or range delete) for `(-∞, now−T]`, then compare `**ZCARD`** (or bounded `ZRANGE`) to the limit. **Memory grows** with request rate (one logical entry per logged request).

`now_ms` is **not** fixed in advance: use Redis `**TIME`** if you want the server clock. `T_ms` is the window length in ms; `cutoff = now_ms - T_ms` (inclusive trim below).

Assume the next request is handled at `**now_ms = 1700000003000`** with `**T_ms = 60000**` (so `**cutoff = now_ms − T_ms = 1699940003000**`).

```text
ZADD ratelimit:user:42 1700000001000 req-001
ZADD ratelimit:user:42 1700000002000 req-002
ZREMRANGEBYSCORE ratelimit:user:42 -inf 1699940003000
ZCARD ratelimit:user:42
ZADD ratelimit:user:42 1700000003000 req-003
```

If `**ZCARD**` is already **≥ limit** before the last `ZADD`, **skip** that line and reject. In production, wrap **trim + count + conditional `ZADD`** in `**MULTI`/`EXEC**` in order to guarantee atomicity.

### Token bucket on Redis

The **token bucket** shapes traffic smoothly: a bucket holds up to `**C`** tokens; tokens **refill** continuously at rate `**r`** (same time unit everywhere, e.g. per second). Each **allowed** request spends **one** token. If the bucket is **empty**, block (or queue) until refill catches up. Unlike a hard fixed clock, you can **burst** briefly as long as you still have banked tokens under `**C`**.

![](./images/redis-token-bucket.png)

![](./images/redis-token-bucket2.png)

**Four steps**

1. **Pick `C` and `r`** — max tokens in the bucket and refill speed.
2. **Persist state** — current **token count** and **last refill time** (`now` when you last applied refill math).
3. **On each request** — compute `elapsed = now − last_refill`, add `elapsed × r` to the count, **clamp** to `**C`**, then set `last_refill = now`.
4. **Decide** — if `tokens ≥ 1`, **decrement** by one and **allow**; otherwise **deny**.

**Redis layout** — one **Hash** per scope is typical, e.g. `tb:<clientId>` with fields like `tokens` and `last_ms` (epoch ms). `**HGETALL`** / `**HMGET`** reads both fields in one round-trip; `**HSET**` writes them back after the computation.


| Pattern                       | Pros                                          | Cons                                                                                             |
| ----------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Fixed window (counter + TTL)  | Minimal code, cheap                           | Edge bursts at clock boundaries; tighten with `**WATCH**` + `**MULTI`/`EXEC**` (retry) if needed |
| Sliding window log (`ZSET`)   | Precise per request, portable Redis           | Memory grows with traffic                                                                        |
| Token bucket (Hash + `WATCH`) | Smooth limit, **controlled bursts** up to *C* | `WATCH` retries under contention; strict units; single clock source                              |


---

## Session sharing

A common pattern is to use Redis as a **session store** so **stateless** web servers can share the same session data.
When a user **logs in**, the application stores the **session data** in Redis and issues a **unique session id**. That id is sent back to the client, usually as a **cookie**.

![](./images/redis-session-sharing.png)

Redis is an **in-memory** database: if the Redis server **restarts**, session data that lived only in memory can be **lost**.

---

## Idempotency keys

Clients (especially mobile / flaky networks) **retry** POSTs. Without deduplication you risk **double payment**, double shipment, duplicate side effects.

**Pattern**: client sends `Idempotency-Key: <uuid>`; server maps `(tenant, route, key)` → **outcome** or **in-flight marker** in Redis with TTL.

```text
SET idempotency:pay:user42:<key> IN_PROGRESS NX EX 86400
```

- If `NX` fails → return cached response or await in-flight (policy choice).
- On success/failure, **replace** value with serialized result.

Same `Idempotency-Key`: duplicate POSTs replay the answer.

```mermaid
sequenceDiagram
  participant C as Client
  participant S as App
  participant R as Redis
  participant P as Provider

  Note over C,P: First attempt (key abc-123)
  C->>S: POST /pay + Idempotency-Key abc-123
  S->>R: SET idem:… IN_PROGRESS NX
  R-->>S: OK
  S->>P: charge once
  P-->>S: txn_999
  S->>R: SET idem:… outcome JSON + TTL
  S-->>C: 200 + body
  Note over C: response lost — client retries

  Note over C,P: Retry (same key)
  C->>S: POST /pay + same key
  S->>R: SET idem:… IN_PROGRESS NX
  R-->>S: nil (key exists)
  S->>R: GET idem:…
  R-->>S: outcome JSON
  S->>C: same 200 + body, no second charge
```



---

## Reference

- [Distributed locks with Redis](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Token Bucket Rate Limiter](https://www.youtube.com/watch?v=cfF6nXIpDwE&t=38s)

