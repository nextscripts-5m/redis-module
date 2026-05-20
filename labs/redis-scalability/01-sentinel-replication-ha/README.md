# Lab 01 — Sentinel, replication & read scaling

**Slides:** `slides/05-Redis Scalability.md` → *Replication*, *Sentinel*.

Topology: **1 master**, **3 replicas**, **3 Sentinels** (quorum 2). Writes go to the current master (via Sentinel-aware client); reads can target replicas and may be **stale**.

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
curl -s http://localhost:18400/api/lab/info | jq .
curl -s -X POST http://localhost:18400/api/traffic/start | jq .
```

- Grafana: [http://localhost:3008](http://localhost:3008)
- Prometheus: [http://localhost:9098](http://localhost:9098)
- Scenarios: **[TESTING-SPRING.md](./TESTING-SPRING.md)**

