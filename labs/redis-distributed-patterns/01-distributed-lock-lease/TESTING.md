# Lab 1 — Testing guide

From `01-distributed-lock-lease/`:

```bash
docker compose up -d
docker compose exec -it redis redis-cli
```

You get an interactive prompt (`127.0.0.1:6379>`). Type the commands below **without** `redis-cli` in front. Exit with `quit` or Ctrl+D.

---

## Scenario 1 — Exclusive acquire (`SET NX PX`)

**Goal:** only one holder; second `SET NX` fails while the lease is valid.

```text
# setup — clear key (ok if missing)
DEL lock:orders:42

# worker-a — acquire (30 s lease)
SET lock:orders:42 worker-a:111 NX PX 30000

# worker-b — try acquire (must fail while A holds)
SET lock:orders:42 worker-b:222 NX PX 30000

# inspect
GET lock:orders:42
PTTL lock:orders:42
```

**Expected**


| Who      | Command | Output (typical)                     |
| -------- | ------- | ------------------------------------ |
| worker-a | `SET`   | `OK`                                 |
| worker-b | `SET`   | `(nil)`                              |
| inspect  | `GET`   | `"worker-a:111"`                     |
| inspect  | `PTTL`  | positive ms (e.g. `(integer) 29000`) |


---

## Scenario 2 — Safe release (Lua)

**Goal:** delete the lock only if you are still the owner.

### Part A — Worker A acquires and releases

```text
# setup
DEL lock:orders:42

# worker-a — acquire
SET lock:orders:42 worker-a:111 NX PX 30000

# worker-a — safe release (Lua, own owner id)
EVAL "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock:orders:42 worker-a:111

# inspect
GET lock:orders:42
```

**Expected:** `EVAL` → `(integer) 1`, then `GET` → `(nil)`.

### Part B — Worker B tries to release A’s lock

```text
# worker-a — acquire again
SET lock:orders:42 worker-a:111 NX PX 30000

# worker-b — release attempt with B’s id (must not delete)
EVAL "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock:orders:42 worker-b:222

# inspect
GET lock:orders:42
```

**Expected:** `EVAL` → `(integer) 0`, `GET` still `"worker-a:111"`.

---

## Scenario 3 — Unsafe `DEL` after lease expiry (anti-pattern)

**Goal:** A’s lease expires; B acquires; A’s late blind `DEL` removes B’s lock.

```text
# setup
DEL lock:orders:99

# worker-a — acquire (2 s lease; shorter than A’s “work”)
SET lock:orders:99 worker-a:slow NX PX 2000

# wait ~3 seconds (worker-a lease expired; key gone)
```

```text
# worker-b — acquire after expiry
SET lock:orders:99 worker-b:fast NX PX 30000

# inspect
GET lock:orders:99
```

**Expected:** `SET` → `OK`, `GET` → `"worker-b:fast"`.

```text
# wait ~2 seconds (worker-a still “working” off Redis)
```

```text
# worker-a — unsafe release (blind DEL, anti-pattern)
DEL lock:orders:99

# inspect
GET lock:orders:99
```

**Expected:** `DEL` → `(integer) 1`, `GET` → `(nil)` — worker-a deleted worker-b’s lock.

---

## Scenario 4 — Safe release after expiry (correct behaviour)

**Goal:** same timeline as Scenario 3; worker-a uses Lua at the end — worker-b keeps the lock.

```text
# setup
DEL lock:orders:88

# worker-a — acquire (2 s lease)
SET lock:orders:88 worker-a:slow NX PX 2000

# wait ~3 seconds (worker-a lease expired)
```

```text
# worker-b — acquire
SET lock:orders:88 worker-b:fast NX PX 30000

# wait ~3 seconds (worker-a still “working”)
```

```text
# worker-a — safe release (stale owner; must not delete B’s lock)
EVAL "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock:orders:88 worker-a:slow

# inspect
GET lock:orders:88
```

**Expected:** `EVAL` → `(integer) 0`, `GET` → `"worker-b:fast"`.

---

## Cleanup

```text
# setup
DEL lock:orders:42 lock:orders:99 lock:orders:88
quit
```

