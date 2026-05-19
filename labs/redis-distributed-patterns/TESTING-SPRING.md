# Lab 1 — Spring testing guide (Scenario 3 vs 4)

Uses the same Redis as **TESTING.md**. Scenarios 1–2 are easiest in `redis-cli`; this guide covers **Scenario 3 (unsafe `DEL`)** and **Scenario 4 (safe Lua release)** over HTTP.

## Start

```bash
cd labs/redis-distributed-patterns/01-distributed-lock-lease
docker compose up --build -d
docker compose ps
curl -s http://localhost:18100/api/lab/info | jq .
curl -s http://localhost:18101/api/lab/info | jq .
```


| Service    | Port  | Role                                                                    |
| ---------- | ----- | ----------------------------------------------------------------------- |
| `redis`    | 6390  | Lock store (CLI still works: `docker compose exec -it redis redis-cli`) |
| `worker-a` | 18100 | Slow worker (5 s work, 2 s lease by default)                            |
| `worker-b` | 18101 | Fast worker (acquires after A’s lease expires)                          |


## `releaseMode` on POST

```http
POST /api/orders/{orderId}/process?lockTtlMs=2000&processingDelayMs=5000&releaseMode=safe|unsafe
```


| Query param          | Default    | Meaning                                |
| -------------------- | ---------- | -------------------------------------- |
| `releaseMode`        | `**safe**` | Lua: delete only if owner matches      |
| `releaseMode=unsafe` | —          | Blind `DEL` (Scenario 3 anti-pattern)  |
| `lockTtlMs`          | `2000`     | Lease length (ms)                      |
| `processingDelayMs`  | `5000`     | Simulated work while holding lock (ms) |


---

## Scenario 3 — Unsafe release (`releaseMode=unsafe`)

**Goal:** worker-a’s lease expires; worker-b acquires; worker-a’s late **unsafe** release removes b’s lock.

### Step 1 — worker-a starts (blocks ~5 s)

In one terminal:

```bash
curl -s -X POST 'http://localhost:18100/api/orders/99/process?lockTtlMs=2000&processingDelayMs=5000&releaseMode=unsafe' | jq .
```

### Step 2 — worker-b acquires (~3 s after step 1)

In another terminal:

```bash
curl -s -X POST 'http://localhost:18101/api/orders/99/process?lockTtlMs=30000&processingDelayMs=3000&releaseMode=safe' | jq .
```

**Expected:** worker-b `acquired: true`, lock holder `worker-b:…`.

### Step 3 — worker-a finishes

When the first curl returns (~5 s total):

**Expected JSON (worker-a):**

- `releaseMode`: `"UNSAFE"`
- `deletedAnotherHolder`: **true**
- `message`: mentions unsafe DEL / anti-pattern

---

## Scenario 4 — Safe release (`releaseMode=safe`)

Repeat with a clean key. Use `**releaseMode=safe`** on worker-a (default if omitted).

```bash
curl -s -X POST 'http://localhost:18100/api/orders/88/process?lockTtlMs=2000&processingDelayMs=5000&releaseMode=safe' | jq .

# in a different terminal
curl -s -X POST 'http://localhost:18101/api/orders/88/process?lockTtlMs=30000&processingDelayMs=3000' | jq .
```

When worker-a completes:

**Expected (worker-a):**

- `releaseMode`: `"SAFE"`
- `released`: false (stale owner — Lua returned 0)
- `deletedAnotherHolder`: **false**

---

## Inspect recent events

```bash
curl -s http://localhost:18100/api/workers/events | jq .
curl -s http://localhost:18101/api/workers/events | jq .
```

---

## Cleanup

```bash
docker compose down
```

Redis CLI cleanup (optional): see **TESTING.md** → Cleanup.