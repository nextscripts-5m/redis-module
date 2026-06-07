#!/usr/bin/env bash
# Side-by-side latency comparison: Spring Cache + Redis (:8080) vs no cache (:8081).
# Usage: ./scripts/compare-cache.sh [COUNT] [ARTICLE_ID]
# Example: ./scripts/compare-cache.sh 30 1

set -euo pipefail

COUNT="${1:-30}"
ARTICLE_ID="${2:-1}"
WITH_URL="${CACHE_WITH_URL:-http://localhost:8080}"
WITHOUT_URL="${CACHE_WITHOUT_URL:-http://localhost:8081}"

stats() {
  awk '
    {
      s += $1
      if (NR == 1) { min = $1; max = $1 }
      if ($1 < min) min = $1
      if ($1 > max) max = $1
      if (NR >= 2) { s2 += $1; n2++ }
    }
    END {
      printf "avg (all):     %.4fs\n", s / NR
      printf "avg (warm):    %.4fs  (requests 2..N, cache hits on :8080)\n", (n2 > 0 ? s2 / n2 : 0)
      printf "min:           %.4fs\n", min
      printf "max:           %.4fs\n", max
    }
  '
}

run_series() {
  local label="$1"
  local base_url="$2"
  local url="${base_url%/}/api/articles/${ARTICLE_ID}"
  local tmp
  tmp=$(mktemp)

  echo "=== ${label} ==="
  echo "URL: ${url}"
  echo "Requests: ${COUNT}"
  echo "time_total(s) per request"
  echo "-------------------------"

  local i=1
  while [[ "${i}" -le "${COUNT}" ]]; do
    local t hint=""
    t=$(curl -sS -o /dev/null -w "%{time_total}" "${url}")
    if [[ "${label}" == *"WITH cache"* && "${i}" -eq 1 ]]; then
      hint="  <- cache miss (loads simulated DB)"
    elif [[ "${label}" == *"WITH cache"* && "${i}" -eq 2 ]]; then
      hint="  <- cache hit"
    fi
    printf 'req %2d: %ss%s\n' "${i}" "${t}" "${hint}"
    printf '%s\n' "${t}" >> "${tmp}"
    i=$((i + 1))
  done

  echo "-------------------------"
  stats < "${tmp}"
  rm -f "${tmp}"
  echo
}

echo "Redis cache lab — latency comparison"
echo "Article id: ${ARTICLE_ID}"
echo

run_series "WITH cache (:8080)" "${WITH_URL}"
run_series "WITHOUT cache (:8081)" "${WITHOUT_URL}"