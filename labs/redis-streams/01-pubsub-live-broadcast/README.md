# Lab: Chapter 3 — Redis Pub/Sub Live Broadcast

This lab shows Redis Pub/Sub as a **live broadcast** mechanism: connected subscribers receive messages immediately, but Redis does not persist, replay, acknowledge, or retry them.

## Services


| Compose service | Host port | Role                                                          |
| --------------- | --------- | ------------------------------------------------------------- |
| `publisher-api` | 18080     | HTTP API that publishes order notifications to Redis Pub/Sub. |
| `subscriber-a`  | 18081     | Subscriber listening to `order-notifications`.                |
| `subscriber-b`  | 18082     | Second subscriber listening to the same channel.              |
| `redis`         | 6380      | Redis broker used for Pub/Sub.                                |
| `prometheus`    | 9091      | Scrapes publisher and subscriber metrics.                     |
| `grafana`       | 3001      | Pre-provisioned dashboard for Pub/Sub metrics.                |


## 1. Run with Docker

From `labs/redis-streams/01-pubsub-live-broadcast/`:

```bash
docker compose up --build -d
docker compose ps
curl -s http://localhost:18080/api/lab/info | jq .
curl -s http://localhost:18081/api/lab/info | jq .
curl -s http://localhost:18082/api/lab/info | jq .
```

Observability:

- Prometheus: [http://localhost:9091](http://localhost:9091)
- Grafana: [http://localhost:3001](http://localhost:3001) → dashboard **Redis Pub/Sub lab**

## 2. Publish a Message

```bash
curl -s -X POST http://localhost:18080/api/pubsub/publish \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-created","orderId":"42","payload":{"amount":49.90}}' | jq .
```

The response includes `receiversAtPublishTime`, which is the number of subscribers connected to Redis at the exact publish moment.

Check what each subscriber received:

```bash
curl -s http://localhost:18081/api/subscriber/messages | jq .
curl -s http://localhost:18082/api/subscriber/messages | jq .
```

Expected result: both subscribers received the same message.

## 3. Show That Pub/Sub Has No Replay

Stop one subscriber:

```bash
docker compose stop subscriber-b
```

Publish two more messages:

```bash
curl -s -X POST http://localhost:18080/api/pubsub/publish \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-paid","orderId":"42","payload":{"paymentId":"pay_987"}}' | jq .

curl -s -X POST http://localhost:18080/api/pubsub/publish \
  -H 'Content-Type: application/json' \
  -d '{"type":"order-ready-to-ship","orderId":"42","payload":{"warehouse":"EU-1"}}' | jq .
```

Restart the subscriber:

```bash
docker compose start subscriber-b
```

Now inspect both subscribers:

```bash
curl -s http://localhost:18081/api/subscriber/messages | jq .
curl -s http://localhost:18082/api/subscriber/messages | jq .
```

`subscriber-a` received the messages while it was connected. `subscriber-b` does not recover the messages published while it was offline. This is the key Pub/Sub trade-off.

## 4. Introduce Simulated Failures

Subscribers support a configurable failure probability:

```bash
SUBSCRIBER_B_FAILURE_PROBABILITY=0.5 docker compose up --build -d
```

With `0.5`, `subscriber-b` fails roughly half of the messages it receives.

Generate traffic:

```bash
for i in $(seq 1 20); do
  curl -s -X POST http://localhost:18080/api/pubsub/publish \
    -H 'Content-Type: application/json' \
    -d "{\"type\":\"order-updated\",\"orderId\":\"$i\",\"payload\":{\"source\":\"loop\"}}" > /dev/null
done
```

Then open Grafana and compare:

- published/sec;
- received/sec;
- processed/sec;
- failed/sec;
- total simulated failures.

Failed messages are not retried because Pub/Sub has no acknowledgment mechanism. From Redis' point of view, the message was delivered to the subscriber connection.

