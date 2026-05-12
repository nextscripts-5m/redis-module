# Lab: Chapter 2 — Redis caching with Spring (`redis-cache`)

## 1. Run everything with Docker

From `labs/redis-cache/`:

```bash
docker compose up --build -d
docker compose ps
curl -s http://localhost:8080/api/lab/info
```

**Simulated DB delay** (milliseconds, default **80** in `application.yaml`) can be overridden from the host without editing files:

```bash
APP_SIMULATED_DB_DELAY_MS=150 docker compose up --build -d
```

**Run with cache disabled** (A/B demo: no Redis cache):

```bash
SPRING_PROFILES_ACTIVE=nocache docker compose up --build -d
```

**Cold cache** (flush Redis while the stack is running):

```bash
docker compose exec redis redis-cli FLUSHDB
```

---

## 2. Exercise the API (from the host)

Articles are seeded with ids **1**, **2**, and **3** (see `src/main/resources/data.sql`).

```bash
# read
curl -s http://localhost:8080/api/articles/1 | jq .

# update (evicts cache for id 1)
curl -s -X PUT http://localhost:8080/api/articles/1 \
  -H 'Content-Type: application/json' \
  -d '{"title":"Alpha (updated)","content":"new body"}' | jq .

# delete (evicts then removes row)
curl -s -X DELETE http://localhost:8080/api/articles/3 -w "\nHTTP %{http_code}\n"
```

After `PUT`/`DELETE`, the **next** `GET` for that id is slow again on a cache miss, then fast on repeated GETs when caching is enabled.

---

## 3. Classroom benchmark (Docker only)

All traffic still goes to **[http://localhost:8080](http://localhost:8080)** on the host; only the **app container** profile changes.

**Round A — no cache**

```bash
docker compose down
SPRING_PROFILES_ACTIVE=nocache docker compose up --build -d
./scripts/bench-reads.sh http://localhost:8080 30
```

Expect **every** line near `APP_SIMULATED_DB_DELAY_MS` / default delay plus overhead.

**Round B — Redis cache**

```bash
docker compose down
docker compose up --build -d
docker compose exec redis redis-cli FLUSHDB
./scripts/bench-reads.sh http://localhost:8080 30
```

Expect the **first** request slow, the **rest** much faster (until TTL, eviction, or a write invalidates the key).

### Optional: heavier load

If you have [hey](https://github.com/rakyll/hey) on the host:

```bash
hey -n 200 -c 10 http://localhost:8080/api/articles/1
```

Compare requests/sec and latency between round A and round B.

---

