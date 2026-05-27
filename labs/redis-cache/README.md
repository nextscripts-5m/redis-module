# Lab: Chapter 2 — Redis caching with Spring (`redis-cache`)

Default Docker stack: **two app instances in parallel** (same artifact, different profiles), **Redis**, **Prometheus**, **Grafana**, and **Jaeger**.


| Compose service     | Host port | Role                                                                                       |
| ------------------- | --------- | ------------------------------------------------------------------------------------------ |
| `cache-lab-with`    | **8080**  | Spring Cache + Redis (`cache-aside`, eviction). Use this for README §2 (GET/PUT/DELETE).   |
| `cache-lab-nocache` | **8081**  | `nocache` profile: no cache, no Redis. Reads / benchmarks only, for latency comparison.    |
| `redis`             | 6379      | Cache backend for the `:8080` instance only.                                               |
| `prometheus`        | 9090      | Scrapes `/actuator/prometheus` on both apps (jobs `cache-with-redis` and `cache-nocache`). |
| `grafana`           | 3000      | Pre-provisioned dashboard (anonymous Admin access for the demo).                           |
| `jaeger`            | 16686     | Trace UI. Both app instances export OTLP traces to Jaeger.                                 |


---

## 1. Run with Docker

From `labs/redis-cache/`:

```bash
docker compose up --build -d
docker compose ps
```

**Simulated DB delay** (milliseconds, default **80** in `application.yaml`), the same for both instances:

```bash
APP_SIMULATED_DB_DELAY_MS=150 docker compose up --build -d
```

**Cold cache** (Redis-backed instance on `:8080` only):

```bash
docker compose exec redis redis-cli FLUSHDB
```

**Observability**

- Prometheus: [http://localhost:9090](http://localhost:9090) → *Status → Targets* (check `cache-with-redis` and `cache-nocache`).
- Grafana: [http://localhost:3000](http://localhost:3000) → *Dashboards* menu → **Redis cache lab — HTTP latency** (p95 and mean latency for `/api/articles/`* requests).
- Jaeger: [http://localhost:16686](http://localhost:16686) → select service `redis-cache-lab`, then search traces after sending some API traffic.

---

## 2. Exercise the API (cached instance only)

Seeded articles use ids **1**, **2**, and **3** (see `src/main/resources/data.sql`)

```bash
# read
curl -s http://localhost:8080/api/articles/1 | jq .

# update (evicts cache for id 1)
curl -s -X PUT http://localhost:8080/api/articles/1 \
  -H 'Content-Type: application/json' \
  -d '{"title":"Alpha (updated)","content":"new body"}' | jq .

# delete (evict then remove row)
curl -s -X DELETE http://localhost:8080/api/articles/3 -w "\nHTTP %{http_code}\n"
```

After `PUT`/`DELETE`, the **next** `GET` for that id is slow again (miss), then fast on repeated GETs while caching is enabled.

---

## 3. Parallel benchmark (Docker)

Generate traffic on **both** ports, then inspect the charts in Grafana.

```bash
./scripts/bench-reads.sh http://localhost:8080 30
./scripts/bench-reads.sh http://localhost:8081 30
```

On **8080** expect the first request slow and the following ones much faster (until TTL, eviction, or a write). On **8081** each line stays near `APP_SIMULATED_DB_DELAY_MS` plus overhead.

---

