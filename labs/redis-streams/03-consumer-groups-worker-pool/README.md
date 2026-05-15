# Lab: Chapter 3 — Consumer Groups Worker Pool

This lab shows Redis Streams **consumer groups**: `XREADGROUP`, `XACK`, the Pending Entries List (PEL), and work distribution across workers.

## Model

```text
stream: order-events

groups:
  billing   -> billing-worker-1, billing-worker-2
  shipping  -> shipping-worker-1
  analytics -> analytics-worker-1
```

Workers inside the same group compete for entries. Different groups independently observe the same stream with separate cursors.

## Services


| Compose service      | Host port | Role                                                  |
| -------------------- | --------- | ----------------------------------------------------- |
| `order-producer-api` | 18100     | HTTP API that appends order events with `XADD`.       |
| `billing-worker-1`   | 18101     | Consumer in group `billing`.                          |
| `billing-worker-2`   | 18102     | Second consumer in group `billing` (load sharing).    |
| `shipping-worker-1`  | 18103     | Consumer in group `shipping`.                         |
| `analytics-worker-1` | 18104     | Consumer in group `analytics`.                        |
| `redis`              | 6382      | Redis instance storing the stream and group state.    |
| `prometheus`         | 9093      | Scrapes producer and worker metrics.                  |
| `grafana`            | 3003      | Pre-provisioned dashboard for consumer group metrics. |


## 1. Run with Docker

From `labs/redis-streams/03-consumer-groups-worker-pool/`:

```bash
docker compose up --build -d
```

```bash
docker compose ps
```

```bash
curl -s http://localhost:18100/api/lab/info | jq .
curl -s http://localhost:18101/api/lab/info | jq .
```

Observability:

- Prometheus: [http://localhost:9093](http://localhost:9093)
- Grafana: [http://localhost:3003](http://localhost:3003) → dashboard **Redis Streams consumer groups lab**

## 2. Produce Order Events

```bash
curl -s -X POST http://localhost:18100/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-created","orderId":"42","payload":{"amount":49.90}}' | jq .
```

```bash
curl -s -X POST http://localhost:18100/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-paid","orderId":"42","payload":{"paymentId":"pay_987"}}' | jq .
```

```bash
curl -s -X POST http://localhost:18100/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-ready-to-ship","orderId":"42","payload":{"warehouse":"EU-1"}}' | jq .
```

Inspect the stream:

```bash
curl -s "http://localhost:18100/api/events?count=20" | jq .
```

## 3. Work Distribution Inside a Group

Generate traffic and inspect which billing worker processed each entry:

```bash
for i in $(seq 1 12); do
  curl -s -X POST http://localhost:18100/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"lane\":\"billing-demo\"}}" > /dev/null
done
```

```bash
curl -s http://localhost:18101/api/worker/messages | jq .
curl -s http://localhost:18102/api/worker/messages | jq .
```

`billing-worker-1` and `billing-worker-2` divide messages in the `billing` group. Each stream entry is normally delivered to only one billing consumer.

Open Grafana and compare processed/sec by consumer in the **Billing worker pool load sharing** panel.

## 4. Independent Groups on the Same Stream

Each group has its own cursor on `order-events`:

```bash
curl -s http://localhost:18103/api/worker/messages | jq .
curl -s http://localhost:18104/api/worker/messages | jq .
```

`shipping` and `analytics` both process the same events independently from `billing`.

Compare group throughput in Grafana (**Throughput by group**).

## 5. Pending Entries Without `XACK`

When processing fails, the worker does **not** call `XACK`. Redis keeps the entry in the PEL for that consumer.

Restart with failure probability on billing workers:

```bash
BILLING_WORKER_FAILURE_PROBABILITY=0.5 docker compose up --build -d
```

Generate traffic:

```bash
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:18100/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"source\":\"pel-demo\"}}" > /dev/null
done
```

Inspect pending entries:

```bash
curl -s http://localhost:18101/api/worker/pending | jq .
curl -s http://localhost:18101/api/worker/pending/entries | jq .
```

Or use Redis CLI:

```bash
docker compose exec redis redis-cli XPENDING order-events billing
docker compose exec redis redis-cli XPENDING order-events billing - + 10
```

Message for the classroom:

```text
XREADGROUP delivers an entry to a consumer.
Without XACK, the entry stays pending in the PEL.
This is Redis-managed at-least-once delivery, unlike the client cursor in lab 02.
```

## 6. Stop One Billing Worker

```bash
docker compose stop billing-worker-2
```

Produce more events:

```bash
for i in $(seq 1 8); do
  curl -s -X POST http://localhost:18100/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"source\":\"single-billing-worker\"}}" > /dev/null
done
```

Only `billing-worker-1` continues to consume from the `billing` group. Restart the second worker:

```bash
docker compose start billing-worker-2
```

## 7. Reset the Lab

```bash
curl -s -X DELETE http://localhost:18100/api/events | jq .
```

Workers recreate their groups on the next startup when the stream is empty.