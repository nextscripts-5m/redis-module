# Redis Distributed Patterns Labs

Hands-on labs for `slides/04-Redis Distributed Patterns.md`.

Each lab is **terminal-first**: Redis in Docker, exercises in an interactive `docker compose exec -it redis redis-cli` session. Theory stays in the slides; steps and expected output live in each lab’s **TESTING.md**.

```text
01-distributed-lock-lease/     # SET NX PX, safe vs unsafe release
02-lock-fencing-stale-holder/  # (planned)
03-rate-limit-fixed-window/    # (planned)
05-idempotency-payment/        # (planned)
```

