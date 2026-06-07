# Lab 01 — Sentinel, replication & read scaling

**Slides:** `slides/05-Redis Scalability.md` → *Replication*, *Sentinel*.

Topology: **1 master**, **3 replicas**, **3 Sentinels** (quorum 2). Writes go to the current master (via Sentinel-aware client). Reads target **replicas only** — the traffic loader polls `INFO replication` and excludes the current master from the read pool.

## Services


| Service                 | Host port   | Role                                         |
| ----------------------- | ----------- | -------------------------------------------- |
| `redis-master`          | 6391        | Primary (writes)                             |
| `redis-replica-1` … `3` | 6392–6394   | Replicas (reads)                             |
| `sentinel-1` … `3`      | 26379–26381 | Monitoring & failover                        |
| `traffic-loader`        | 18400       | Spring loader + app metrics                  |
| `prometheus`            | 9098        | Scrapes redis_exporter + loader              |
| `grafana`               | 3008        | Dashboard **Redis Sentinel replication lab** |


## Quick start

```bash
docker compose up --build -d
```

Grafana: [http://localhost:3008](http://localhost:3008)

## Scenario 1 — Replication lag / stale reads

```bash
curl -s -X POST http://localhost:18400/api/traffic/start | jq .
# wait 30s
curl -s http://localhost:18400/api/traffic/stats | jq .
```

**Expected:** `staleReads` increases under load; `readTargets` lists the three replicas (not the master). Grafana *App: stale reads* panel rises.

Read one replica explicitly:

```bash
curl -s "http://localhost:18400/api/traffic/read/redis-replica-1" | jq .
```

## Scenario 2 — Read scaling

With traffic running, open Grafana:

- **Write ops/s (master only)** — a single line on `redis-master`.
- **Read ops/s (replicas only)** — three lines on `redis-replica-1`, `redis-replica-2`, `redis-replica-3`.

Replica SET traffic from internal replication is filtered out of the write panel.

## Scenario 3 — Failover

1. Keep traffic running (`/api/traffic/start`).
2. `docker compose stop redis-master`
3. Watch Grafana:
   - **Write ops/s** — line moves from `redis-master` to the promoted replica (e.g. `redis-replica-2`).
   - **Read ops/s** — promoted node drops out; only the remaining replicas are read.

```bash
curl -s http://localhost:18400/api/traffic/stats | jq '{currentMaster, readTargets}'
```

```bash
# check which replica is promoted to master
for p in 6392 6393 6394; do
  echo -n "port $p: "
  redis-cli -p $p INFO replication | grep '^role:'
done
```

Stop traffic:

```bash
curl -s -X POST http://localhost:18400/api/traffic/stop | jq .
```
