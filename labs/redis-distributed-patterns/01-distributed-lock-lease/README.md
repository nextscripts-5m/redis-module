# Lab 1 — Distributed lock with lease

**Slides:** `slides/04-Redis Distributed Patterns.md` → *Distributed locks*.

Two (or more) clients compete for `lock:orders:{id}` using `SET … NX PX`. Release must be **owner-aware** (Lua); a blind `DEL` after lease expiry can delete another client’s lock.

## What you need

- Docker

## Start and connect

```bash
cd labs/redis-distributed-patterns/01-distributed-lock-lease
docker compose up -d
docker compose exec -it redis redis-cli
```

You work at the `127.0.0.1:6379>` prompt until you type `quit`.

## Exercises

Step-by-step commands and expected output: **[TESTING.md](./TESTING.md)**.

## Layout

```text
docker-compose.yml   # Redis only (host port 6390 if using redis-cli -p from outside)
TESTING.md           # interactive lab guide
```
