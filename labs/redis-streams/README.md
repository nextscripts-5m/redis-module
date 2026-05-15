# Redis Streams Labs

Labs for `slides/03-Redis Messaging Streams.md`.

Each example lives in its own folder and is dockerized independently. Implemented labs:

```text
01-pubsub-live-broadcast/
02-stream-append-log/
03-consumer-groups-worker-pool/
04-ordering-sharded-streams/
```

Planned labs:

```text
05-failure-recovery-dlq-observability/
```

Run a lab from its own directory with:

```bash
docker compose up --build -d
```
