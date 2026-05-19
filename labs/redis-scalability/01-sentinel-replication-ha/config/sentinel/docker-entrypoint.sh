#!/bin/sh
# Sentinel must rewrite its config at runtime; use a writable copy per container.
set -eu

echo "Waiting for redis-master DNS..."
until getent hosts redis-master >/dev/null 2>&1; do
  sleep 1
done
getent hosts redis-master

mkdir -p /data
cp /etc/sentinel/sentinel.conf /data/sentinel.conf
exec redis-sentinel /data/sentinel.conf
