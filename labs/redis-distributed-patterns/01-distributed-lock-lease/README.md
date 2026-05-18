# Lab 1 — Distributed lock with lease

**Slides:** `slides/04-Redis Distributed Patterns.md` → *Distributed locks*.

## Two ways to run

| Path | Guide | What you use |
|------|--------|----------------|
| **Terminal** | [TESTING.md](./TESTING.md) | `docker compose up -d` → `docker compose exec -it redis redis-cli` |
| **Spring (optional)** | [TESTING-SPRING.md](./TESTING-SPRING.md) | Same Redis + two HTTP workers — Scenario 3 vs 4 via `releaseMode` query param |

## Terminal quick start

```bash
docker compose up -d redis
docker compose exec -it redis redis-cli
```

Follow **TESTING.md** at the `127.0.0.1:6379>` prompt.

## Spring quick start (Scenario 3 & 4)

```bash
docker compose up --build -d
curl -s http://localhost:18100/api/lab/info | jq .
```

See **TESTING-SPRING.md** for the curl timeline (`releaseMode=unsafe` vs `safe`).

## Layout

```text
docker-compose.yml    # redis + worker-a + worker-b
TESTING.md            # redis-cli lab (scenarios 1–4)
TESTING-SPRING.md     # HTTP lab (scenarios 3–4)
pom.xml, src/         # Spring Boot (optional)
```
