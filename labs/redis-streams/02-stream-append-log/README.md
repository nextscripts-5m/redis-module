# Lab: Chapter 3 — Redis Streams Append Log

This lab shows Redis Streams as a **persistent append-only log**. Producers append entries with `XADD`; readers can inspect history with `XRANGE` or consume new entries with `XREAD` from a client-managed cursor.

## Services


| Compose service      | Host port | Role                                                                  |
| -------------------- | --------- | --------------------------------------------------------------------- |
| `order-producer-api` | 18090     | HTTP API that appends order events to the `order-events` stream.      |
| `stream-reader`      | 18091     | Background `XREAD` reader that stores its last processed ID in Redis. |
| `replay-reader`      | 18092     | HTTP API used to replay stream history with `XRANGE`-style reads.     |
| `redis`              | 6381      | Redis instance storing the stream and the reader cursor.              |
| `prometheus`         | 9092      | Scrapes producer and reader metrics.                                  |
| `grafana`            | 3002      | Pre-provisioned dashboard for stream metrics.                         |


## 1. Run with Docker

From `labs/redis-streams/02-stream-append-log/`:

```bash
docker compose up --build -d
```

```bash
docker compose ps
```

Observability:

- Prometheus: [http://localhost:9092](http://localhost:9092)
- Grafana: [http://localhost:3002](http://localhost:3002) → dashboard **Redis Streams append log lab**

## 2. Append Events with `XADD`

Create an order event:

```bash
curl -s -X POST http://localhost:18090/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-created","orderId":"42","payload":{"amount":49.90}}' | jq .
```

Add more events for the same order:

```bash
curl -s -X POST http://localhost:18090/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-paid","orderId":"42","payload":{"paymentId":"pay_987"}}' | jq .
```

```bash
curl -s -X POST http://localhost:18090/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-ready-to-ship","orderId":"42","payload":{"warehouse":"EU-1"}}' | jq .
```

Each response contains a Redis Stream ID such as:

```text
1715600000000-0
```

## 3. Read the Append-Only Log

Read recent entries through the API:

```bash
curl -s "http://localhost:18090/api/events?count=20" | jq .
```

## 4. Replay from a Cursor

Replay history from the beginning:

```bash
curl -s "http://localhost:18092/api/replay?from=0-0&count=20" | jq .
```

Replay from a specific ID returned by a previous `POST /api/orders`:

```bash
curl -s "http://localhost:18092/api/replay?from=1715600000000-0&count=20" | jq .
```

Inspect it:

```bash
curl -s http://localhost:18091/api/reader/cursor | jq .
```

## 5. Stop the Reader and Catch Up Later

Stop the background reader:

```bash
docker compose stop stream-reader
```

Produce events while the reader is offline:

```bash
for i in $(seq 1 5); do
  curl -s -X POST http://localhost:18090/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"source\":\"offline-window\"}}" > /dev/null
done
```

At this point, you can already prove that Redis retained those events:

```bash
curl -s "http://localhost:18090/api/events?count=20" | jq .
```

Restart the reader:

```bash
docker compose start stream-reader
```

Inspect what it processed:

```bash
curl -s http://localhost:18091/api/reader/messages | jq .
```

This call proves a second, different point:

```text
The restarted reader has actually consumed and processed the retained events.
```

So this shows that the reader resumed from its cursor and processed those events. |

The reader catches up because the stream retained the events and the reader resumed from its last stored ID.

## 6. Simulate Reader Failures

Restart the lab with a failure probability:

```bash
STREAM_READER_FAILURE_PROBABILITY=0.5 docker compose up --build -d
```

Generate traffic:

```bash
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:18090/api/orders \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"source\":\"failure-demo\"}}" > /dev/null
done
```

When the reader fails a message, it does **not** advance its cursor. The same entry can be read again later. This is not a Redis consumer group retry; it is simple client-side cursor management.

Inspect failed reader attempts:

```bash
curl -s http://localhost:18091/api/reader/messages | jq .
```

This endpoint shows failed attempts too, with:

- `"processed": false`
- `"outcome": "simulated failure; cursor not advanced"`

If a retry succeeds later, the same `eventId` can appear twice: first as failed, then with `"processed": true` and `"outcome": "processed; cursor advanced"`.

Open Grafana and compare:

- stream length;
- produced events;
- read events;
- replay requests;
- reader failures;
- reader processing latency.



## 7. Reset the Lab

Clear the stream and reader cursor:

```bash
curl -s -X DELETE http://localhost:18090/api/events | jq .
```

Or use Redis CLI:

```bash
docker compose exec redis redis-cli DEL order-events order-events:stream-reader:last-id
```

