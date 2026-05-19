#!/usr/bin/env sh
set -eu
echo "Stopping redis-master container to trigger Sentinel failover..."
docker compose stop redis-master
echo "Done. Watch Grafana (write ops/s should move to a replica) and run:"
echo "  redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster"
