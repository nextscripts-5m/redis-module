#!/usr/bin/env bash
# Repeated GETs to compare latency with Redis cache vs no cache (classroom demo).
# Usage: ./scripts/bench-reads.sh [BASE_URL] [COUNT]
# Example: ./scripts/bench-reads.sh http://localhost:8080 25

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
COUNT="${2:-20}"
URL="${BASE_URL%/}/api/articles/1"

echo "URL: ${URL}"
echo "Requests: ${COUNT}"
echo "time_total(s) per request"
echo "-------------------------"

tmp=$(mktemp)
trap 'rm -f "${tmp}"' EXIT

i=1
while [[ "${i}" -le "${COUNT}" ]]; do
  t=$(curl -sS -o /dev/null -w "%{time_total}" "${URL}")
  printf '%s\n' "${t}" | tee -a "${tmp}"
  i=$((i + 1))
done

echo "-------------------------"
awk '{ s+=$1; if(NR==1){min=$1;max=$1} if($1<min)min=$1; if($1>max)max=$1 } END { printf "avg: %.4fs  min: %.4fs  max: %.4fs\n", s/NR, min, max }' "${tmp}"
