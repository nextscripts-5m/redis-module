# Lab: Chapter 3 — Ordering vs Sharded Streams

This lab compares two models on Redis Streams consumer groups:

- **Part A (unordered)**: one stream `order-events`, group `billing`, two competing workers. Load balancing can break business ordering when event types have different processing times.
- **Part B (sharded)**: four lanes `orders:0` … `orders:3`, one dedicated worker per lane. All events for the same `orderId` route to the same stream and preserve order per order.

## Services


| Compose service          | Host port | Role                                                 |
| ------------------------ | --------- | ---------------------------------------------------- |
| `unordered-producer-api` | 18200     | Part A producer on `order-events`.                   |
| `unordered-worker-1`     | 18201     | Part A `billing-worker-1`.                           |
| `unordered-worker-2`     | 18202     | Part A `billing-worker-2`.                           |
| `sharded-producer-api`   | 18210     | Part B producer with shard routing.                  |
| `shard-worker-0` … `3`   | 18211–14  | Part B one consumer per lane `orders:N`.             |
| `redis`                  | 6383      | Redis.                                               |
| `prometheus`             | 9094      | Metrics.                                             |
| `grafana`                | 3004      | Dashboard **Redis Streams ordering vs sharded lab**. |


Processing delays (defaults):


| Event type      | Delay   | Why                                     |
| --------------- | ------- | --------------------------------------- |
| `order-created` | 3000 ms | Slow step (e.g. validation, inventory). |
| `order-paid`    | 100 ms  | Fast step (e.g. payment confirmation).  |


## 1. Run with Docker

From `labs/redis-streams/04-ordering-sharded-streams/`:

```bash
docker compose up --build -d
```

```bash
curl -s http://localhost:18200/api/lab/info | jq .
curl -s http://localhost:18210/api/lab/info | jq .
```

Observability:

- Prometheus: [http://localhost:9094](http://localhost:9094)
- Grafana: [http://localhost:3004](http://localhost:3004)

## 2. Part A — Ordering Violation (Unordered)

Two workers share `order-events` in group `billing`. Redis distributes entries across workers. Because `order-created` is slow and `order-paid` is fast, `**order-paid` can finish before `order-created**` for the same `orderId`.

Publish a lifecycle pair (created then paid, back-to-back):

```bash
curl -s -X POST http://localhost:18200/api/orders/lifecycle \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"42","payload":{"demo":"unordered"}}' | jq .
```

Repeat a few times:

```bash
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:18200/api/orders/lifecycle \
    -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"$i\",\"payload\":{\"demo\":\"unordered-batch\"}}" > /dev/null
done
```

Wait about 5 seconds, then inspect violations:

```bash
curl -s http://localhost:18201/api/worker/ordering-violations | jq .
curl -s http://localhost:18202/api/worker/ordering-violations | jq .
```

Inspect completions (note timestamps and types):

```bash
curl -s http://localhost:18201/api/worker/messages | jq .
```

Grafana: panel **Ordering violations (unordered)** and **Ordering violation rate**.

## 3. Part B — Per-Order Ordering (Sharded)

Routing rule:

```text
stream = "orders:" + (hash(orderId) % 4)
```

Check routing for an order:

```bash
curl -s "http://localhost:18210/api/orders/route?orderId=42" | jq .
```

Publish the same lifecycle pattern on the sharded side:

```bash
curl -s -X POST http://localhost:18210/api/orders/lifecycle \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"42","payload":{"demo":"sharded"}}' | jq .
```

```bash
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:18210/api/orders/lifecycle \
    -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"$i\",\"payload\":{\"demo\":\"sharded-batch\"}}" > /dev/null
done
```

Find which shard worker owns order `42` (example shard 2 → port 18213):

```bash
SHARD=$(curl -s "http://localhost:18210/api/orders/route?orderId=42" | jq -r .shard)
PORT=$((18211 + SHARD))
curl -s "http://localhost:${PORT}/api/worker/messages" | jq .
```

On sharded workers, ordering violations stay at zero:

```bash
curl -s http://localhost:18211/api/worker/ordering-violations | jq .
```

## 4. Compare Part A and Part B


| Aspect                              | Part A (`order-events`)       | Part B (`orders:N`)           |
| ----------------------------------- | ----------------------------- | ----------------------------- |
| Workers per logical pipeline        | 2 competing in `billing`      | 1 dedicated per shard         |
| Same `orderId` events               | May land on different workers | Same lane (`orders:hash % 4`) |
| `order-paid` before `order-created` | Possible                      | Avoided for same `orderId`    |
| Throughput scaling                  | Horizontal in one group       | Horizontal across shards      |


## 5. Inspect Streams in Redis

Part A:

```bash
docker compose exec redis redis-cli XRANGE order-events - + COUNT 10
```

Part B (example lane 0):

```bash
docker compose exec redis redis-cli XRANGE orders:0 - + COUNT 10
```

## 6. Reset

Part A:

```bash
curl -s -X DELETE http://localhost:18200/api/events | jq .
```

Part B:

```bash
curl -s -X DELETE http://localhost:18210/api/events | jq .
```

Restart workers after a full reset so consumer groups are recreated on empty streams.