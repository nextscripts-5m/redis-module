# Lab 02 — Cluster resharding & hot spots

Three masters (no replicas in v1), optional **4th master** via compose profile `scale-out`. Loader skews ~80% of keys to hash tag `{hot}` (single slot → hot shard).

## Services


| Service           | Host port | Role                                       |
| ----------------- | --------- | ------------------------------------------ |
| `cluster-1` … `3` | 6401–6403 | Initial cluster masters                    |
| `cluster-4`       | 6405      | 4th master (`--profile scale-out`)         |
| `cluster-loader`  | 18410     | Cluster-aware load + error/latency metrics |
| `prometheus`      | 9099      | Scrapes exporters + loader                 |
| `grafana`         | 3009      | Dashboard **Redis Cluster resharding lab** |


## Quick start

```bash
docker compose up --build -d
curl -s http://localhost:18410/api/lab/info | jq .
curl -s -X POST 'http://localhost:18410/api/load/start?profile=hotspot' | jq .
```

Hands-on scenarios: **[TESTING-SPRING.md](./TESTING-SPRING.md)**.