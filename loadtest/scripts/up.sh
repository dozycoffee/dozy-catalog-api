#!/usr/bin/env bash
# DB, Prometheus, Grafana, postgres_exporter를 띄운다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

db_mem="${LOADTEST_DB_MEMORY:-8g}"
docker_mem_gib=$(docker info --format '{{.MemTotal}}' | awk '{printf "%.0f", $1/1024/1024/1024}')
echo "Docker 메모리 ${docker_mem_gib}GiB, DB 메모리 제한 ${db_mem}"
if [ "${db_mem%g}" -ge "$docker_mem_gib" ] 2>/dev/null; then
  echo "경고: DB 메모리 제한이 Docker 메모리 이상입니다. 컨테이너가 OOM으로 죽을 수 있습니다." >&2
  echo "      Docker Desktop 메모리를 올리거나 loadtest/.env의 LOADTEST_DB_MEMORY를 줄이세요(개발·스모크용)." >&2
fi

"${COMPOSE[@]}" up -d
echo "Grafana     http://localhost:${LOADTEST_GRAFANA_PORT:-3001}  (대시보드: Catalog 부하테스트)"
echo "Prometheus  http://localhost:${LOADTEST_PROMETHEUS_PORT:-9091}"
