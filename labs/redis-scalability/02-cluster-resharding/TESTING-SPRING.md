# Lab 02 — Testing guide (Spring cluster-loader)

Run everything from `02-cluster-resharding/`.

```bash
docker compose up --build -d
curl -s http://localhost:18410/api/lab/info | jq .
```

---

**Healthy loader** (check after Phase C):

```bash
curl -s http://localhost:18410/api/load/stats | jq .
```

- `running: true`
- `commandsOk` increasing **much faster** than `commandErrors`
- If `commandErrors` grows hundreds per second while `commandsOk` barely moves → **Phase C was skipped** (loader still bootstrapped with 3 nodes only)

---

## Scenario 1 — Slots and hash tags

**Goal:** see that keys map to different slots; `{hot}` forces the same slot.

```bash
redis-cli -p 6401 CLUSTER KEYSLOT user:42
redis-cli -p 6401 CLUSTER KEYSLOT '{hot}:key:1'
redis-cli -p 6401 CLUSTER KEYSLOT '{hot}:key:2'

curl -s http://localhost:18410/api/keys/user:42/slot | jq .
curl -s 'http://localhost:18410/api/keys/%7Bhot%7D:key:1/slot' | jq .
curl -s 'http://localhost:18410/api/keys/%7Bhot%7D:key:2/slot' | jq .
```

**Expected:**

- `{hot}:key:1` and `{hot}:key:2` → **same** `slot` (e.g. 6093).
- `user:42` → **different** slot (e.g. 15880).
- `host` in JSON is a Docker IP (reachable from the app in compose, not from the Mac).

```bash
docker compose exec cluster-1 redis-cli CLUSTER NODES
```

---

## Scenario 2 — MOVED (wrong node)

**Goal:** see the MOVED error when the client **does not** follow the redirect.

**Step 1 — MOVED without following redirect (from host, OK):**

```bash
redis-cli -p 6402 SET user:remote:1 other
```

**Expected:** text response like `MOVED <slot> 172.19.x.x:6379` (no write).

**Step 2 — SET with redirect (inside Docker):**

```bash
docker compose exec cluster-1 redis-cli -c SET user:remote:1 value
```

**Expected:** `OK` (the `-c` client reaches the slot owner).

**Step 3 — Proof: data lives on the slot owner (GET)**

Use the node from the MOVED line (example: owner on **cluster-3**):

```bash
docker compose exec cluster-3 redis-cli GET user:remote:1
docker compose exec cluster-2 redis-cli GET user:remote:1
```

**Expected:** `"value"` on the owner; `MOVED …` on the wrong node.

---

## Scenario 3 — Hot spot (3 masters)

**Goal:** ~80% of keys on one slot → one saturated master in Grafana.

```bash
curl -s -X POST 'http://localhost:18410/api/load/start?profile=hotspot' | jq .
```

Open [Grafana](http://localhost:3009). Wait **60–90 seconds**.

**Expected:**

1. **Redis ops/s** — one of `cluster-1` / `cluster-2` / `cluster-3` well above the others.
2. **Load imbalance** — **> 3**, often **10+**.
3. **Loader errors** — **errors/s** near **0**.

```bash
curl -s http://localhost:18410/api/load/stats | jq .
```

---

## Scenario 4 — Reshard under load (3 → 4 masters)

**Goal:** add a master, move slots off the hot node, realign the loader, see load spread in Grafana.

### Phase A — Baseline (hot spot on 3 nodes)

```bash
curl -s -X POST 'http://localhost:18410/api/load/stop' | jq . 2>/dev/null || true
curl -s -X POST 'http://localhost:18410/api/load/start?profile=hotspot' | jq .
```

Note in Grafana (60s): which `instance` dominates **ops/s** (e.g. `cluster-2`); high **Load imbalance**.

### Phase B — Add 4th node and reshard

**Keep load running** (loader still knows only 3 nodes — errors during migration are expected).

```bash
docker compose --profile scale-out up -d cluster-4 redis-exporter-4

until redis-cli -p 6405 ping 2>/dev/null | grep -q PONG; do sleep 1; done

docker compose exec cluster-1 redis-cli --cluster add-node cluster-4:6379 cluster-1:6379
```

**Expected:** `New node added correctly.` within **5–30 seconds**.

```bash
NEW_ID=$(docker compose exec -T cluster-4 redis-cli cluster myid | tr -d '\r')

# Example: hot spot was cluster-2
FROM_ID=$(docker compose exec -T cluster-2 redis-cli cluster myid | tr -d '\r')

docker compose exec cluster-1 redis-cli --cluster reshard cluster-1:6379 \
  --cluster-from "$FROM_ID" \
  --cluster-to "$NEW_ID" \
  --cluster-slots 4096 \
  --cluster-yes
```

It moves **4096 slots from** `cluster-1` **to** `cluster-4`. If Grafana shows the hot master is `cluster-2` or `cluster-3`, replace `cluster-1` in `FROM_ID` and `--cluster-from` with that service name (e.g. `docker compose exec -T cluster-2 redis-cli cluster myid`).

Resharding can take **1–3 minutes**. Do not stop the load.

**During Phase B (Grafana):**


| Panel              | Expected                                                               |
| ------------------ | ---------------------------------------------------------------------- |
| **Known nodes**    | **4**                                                                  |
| **Redis ops/s**    | **cluster-4** appears; migration activity                              |
| **Loader errors**  | **errors/s** may rise and stay high — loader still has 3-node topology |
| **Load imbalance** | May fluctuate                                                          |


```bash
curl -s http://localhost:18410/api/load/stats | jq .
```

If `commandErrors` >> `commandsOk` and keeps climbing → normal **until Phase C**.

### Phase C — Realign loader (required)

**Do not skip.** The cluster has 4 masters; Spring must be told about all four.

```bash
curl -s -X POST http://localhost:18410/api/load/stop | jq .

SPRING_DATA_REDIS_CLUSTER_NODES=cluster-1:6379,cluster-2:6379,cluster-3:6379,cluster-4:6379 \
  docker compose up -d --force-recreate cluster-loader
```

Wait until `cluster-loader` is **healthy**:

```bash
docker compose ps cluster-loader
```

Restart load:

```bash
curl -s -X POST 'http://localhost:18410/api/load/start?profile=hotspot' | jq .
```

### Phase D — After Phase C (60–120s)

**Grafana:**


| Panel              | vs Phase A                                                            |
| ------------------ | --------------------------------------------------------------------- |
| **Redis ops/s**    | More lines active; former hot master **lower**; **cluster-4** visible |
| **Load imbalance** | **Lower** (toward 1–3)                                                |
| **Loader errors**  | **errors/s** → ~0                                                     |


**API:**

```bash
curl -s http://localhost:18410/api/load/stats | jq .
```

**Expected:** `commandsOk` rising fast; `commandErrors` nearly flat (totals from Phase B remain, but new errors should be rare).

```bash
docker compose exec cluster-1 redis-cli CLUSTER NODES | head -20
```

---

## Scenario 5 — Same slot, different owner

After Phase C (load running or stopped):

```bash
KEY='{hot}:key:001'
SLOT=$(redis-cli -p 6401 CLUSTER KEYSLOT "$KEY")
echo "slot=$SLOT"
docker compose exec cluster-1 redis-cli CLUSTER NODES
```

Find the row whose range **includes** `$SLOT` (e.g. `6093` → `5461-9556` on the new master). `CLUSTER NODES` shows **IP ranges**, not hostnames like `cluster-4`.

Confirm which service owns that range:

```bash
docker compose exec cluster-4 redis-cli CLUSTER NODES | grep myself
```

**Expected:** `CLUSTER KEYSLOT` **unchanged**; the slot range moved to **cluster-4** (or whichever node received the resharded slots).

---

---

## End of lab — stop load

```bash
curl -s -X POST http://localhost:18410/api/load/stop | jq .
```

Full teardown (including volumes):

```bash
docker compose --profile scale-out down -v
```

---

