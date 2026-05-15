# Lab: Chapter 3 — Failure Recovery, DLQ, Observability

This lab demonstrates **PEL** behavior when a consumer fails before `XACK`, **XAUTOCLAIM** for reclaiming idle pending messages, **idempotent** handling of duplicate deliveries, and a **dead-letter stream** (`order-events:dlq`) for poison messages after a retry budget.

Observability is **Prometheus + Grafana** only (no distributed tracing stack in this lab).

## Services


| Compose service     | Host port | Role                                                                                                       |
| ------------------- | --------- | ---------------------------------------------------------------------------------------------------------- |
| `producer-api`      | 18310     | `XADD` payment events to `order-events`.                                                                   |
| `payment-worker`    | 18311     | `XREADGROUP` in `payment-processing` / consumer `payment-worker`; may simulate failure **without** `XACK`. |
| `recovery-worker`   | 18312     | `XAUTOCLAIM` idle pending entries to consumer `recovery-worker`; retries; moves poison to DLQ.             |
| `dlq-inspector-api` | 18313     | HTTP read model for DLQ + **Prometheus gauges** (stream length, PEL total, DLQ length).                    |
| `redis`             | 6385      | Data store.                                                                                                |
| `prometheus`        | 9097      | Scrapes all Spring Actuator endpoints.                                                                     |
| `grafana`           | 3007      | Dashboard **Redis Streams failure recovery DLQ lab**.                                                      |


Optional **redis-exporter** from the course plan is not bundled here to keep the compose file smaller; the inspector exposes stream/PEL/DLQ gauges instead.

## 1. Run with Docker

From `labs/redis-streams/05-failure-recovery-dlq-observability/`:

```bash
docker compose up --build -d
```

```bash
curl -s http://localhost:18310/api/lab/info | jq .
curl -s http://localhost:18313/api/dlq/summary | jq .
```

- Grafana: [http://localhost:3007](http://localhost:3007)
- Prometheus: [http://localhost:9097](http://localhost:9097)

## 2. Produce traffic

```bash
for i in $(seq 1 25); do
  curl -s -X POST http://localhost:18310/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"payment-requested\",\"orderId\":\"$i\",\"payload\":{\"amount\":19.99}}" > /dev/null
done
```

Or install: `brew install watch`, then:

```bash
watch -n 2 'curl -s http://localhost:18313/api/dlq/summary | jq .pendingTotal'
```

## 3. Case: crash before `XACK` (PEL)

When `payment-worker` fails after `XREADGROUP` but **before** `XACK`, the entry stays in the **PEL** for consumer `payment-worker`.

```bash
curl -s http://localhost:18313/api/dlq/summary | jq .
```

Compare with Redis CLI:

```bash
docker compose exec redis redis-cli XPENDING order-events payment-processing
```

## 4. Case: `XAUTOCLAIM` recovery

`recovery-worker` runs `XAUTOCLAIM` with `min-idle-time` (default **6000** ms in compose, override with `RECOVERY_MIN_IDLE_MS`).

After idle time, pending messages are **claimed** by `recovery-worker`, processed again, and `XACK` on success.

Metrics:

- `redis_stream_recovery_claimed_total`
- `redis_stream_recovery_succeeded_total`
- `redis_stream_recovery_failed_total`

## 5. Case: idempotency (duplicate delivery)

`recovery-worker` sets `lab05:processed:{messageId}` after a successful business completion. If the same stream ID is delivered again, the worker **acks without re-running side effects** and increments `redis_stream_idempotent_skips_total`.

```bash
curl -s http://localhost:18312/actuator/prometheus | grep idempotent
```

## 6. Case: poison message → DLQ

If recovery keeps failing, a Redis counter `lab05:recovery_attempts:{messageId}` increments on each claim cycle. When it **exceeds** `APP_MAX_RETRIES_BEFORE_DLQ` (default **3**), the message is copied to `**order-events:dlq`** and acked off the main consumer group.

Fast demo:

```bash
RECOVERY_FAILURE_PROBABILITY=1.0 MAX_RETRIES_BEFORE_DLQ=2 docker compose up --build -d
```

Wait **30–60 seconds** after producing traffic (DLQ fills asynchronously), then:

```bash
curl -s "http://localhost:18313/api/dlq/events?count=20" | jq .
```

## 8. Reset the lab

```bash
curl -s -X DELETE http://localhost:18310/api/events | jq .
```

This deletes `order-events`, `order-events:dlq`, and `lab05:*` keys. Restart workers so the consumer group is recreated cleanly on the next `docker compose up` if needed.