# Lab: Chapter 2 — Redis caching with Spring (`redis-cache`)

Two instances of the **same application** run in parallel: one with **Spring Cache + Redis**, one with caching **disabled**. Both use the same simulated database latency so you can compare read performance fairly.


| Compose service     | Host port | Role                                                        |
| ------------------- | --------- | ----------------------------------------------------------- |
| `cache-lab-with`    | **8080**  | Cache-aside on reads; `@CacheEvict` on writes               |
| `cache-lab-nocache` | **8081**  | `nocache` profile — every read goes to the (slow) datastore |
| `redis`             | 6379      | Cache backend for `:8080` only                              |


Seeded article ids: **1**, **2**, **3** (`src/main/resources/data.sql`).

---

## 1. Start the stack

From `labs/redis-cache/`:

```bash
docker compose up --build -d
docker compose ps
```

Simulated datastore delay (milliseconds, default **80**):

```bash
APP_SIMULATED_DB_DELAY_MS=150 docker compose up --build -d
```

Reset Redis cache (optional, before a cold-cache demo on `:8080`):

```bash
docker compose exec redis redis-cli FLUSHDB
```

---

## 2. Compare latency (main exercise)

Run the side-by-side benchmark:

```bash
chmod +x scripts/compare-cache.sh
./scripts/compare-cache.sh 30
```

**What to expect**


| Instance               | Pattern                                                                                |
| ---------------------- | -------------------------------------------------------------------------------------- |
| **:8080** (with cache) | Request 1 slow (80 ms+) — **cache miss**. Requests 2..N fast (few ms) — **cache hit**. |
| **:8081** (no cache)   | Every request slow (~80 ms+) — always hits simulated DB.                               |


The script prints per-request times plus `avg (all)` and `avg (warm)` (from request 2 onward on the cached instance). Compare **warm avg on :8080** with **avg on :8081** to see the speedup.

Optional arguments: `./scripts/compare-cache.sh [COUNT] [ARTICLE_ID]`

---

## 3. Cache hit, miss, and eviction (manual)

Use **:8080** only. Measure time with `curl -w "%{time_total}"`.

### 3.1 Warm cache

```bash
curl -s -o /dev/null -w "GET #1: %{time_total}s\n" http://localhost:8080/api/articles/1
curl -s -o /dev/null -w "GET #2: %{time_total}s\n" http://localhost:8080/api/articles/1
```

First GET slow (miss), second fast (hit).

### 3.2 Eviction after PUT

```bash
curl -s -X PUT http://localhost:8080/api/articles/1 \
  -H 'Content-Type: application/json' \
  -d '{"title":"Alpha (updated)","content":"new body"}' | jq .

curl -s -o /dev/null -w "GET after PUT #1: %{time_total}s\n" http://localhost:8080/api/articles/1
curl -s -o /dev/null -w "GET after PUT #2: %{time_total}s\n" http://localhost:8080/api/articles/1
```

`@CacheEvict` removes `articles::1` from Redis. The first GET after PUT is slow again (miss), then fast on repeat.

---

