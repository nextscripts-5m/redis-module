# Lab 01 — Testing guide (Spring traffic-loader)

```bash
docker compose up --build -d
curl -s http://localhost:18400/api/lab/info | jq .
```

## Scenario 1 — Replication lag / stale reads

```bash
curl -s -X POST http://localhost:18400/api/traffic/start | jq .
# wait 30s
curl -s http://localhost:18400/api/traffic/stats | jq .
```

**Expected:** `staleReads` increases under load; Grafana *App: stale reads* panel rises.

Read one replica explicitly:

```bash
curl -s http://localhost:18400/api/traffic/read/redis-replica-1:6379 | jq .
```

## Scenario 2 — Read scaling

With traffic running, open Grafana **Read ops/s per node** — reads on `redis-replica-`*, writes only on current master.

## Scenario 3 — Failover

1. Keep traffic running (`/api/traffic/start`).
2. `docker compose stop redis-master`
3. Watch Grafana: **Write ops/s** shifts to a replica.

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

