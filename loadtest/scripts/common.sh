#!/usr/bin/env bash
# 부하테스트 스크립트 공통: 경로와 기본값.
set -euo pipefail

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"
RUN_DIR="$LOADTEST_DIR/.run"
mkdir -p "$RUN_DIR"

COMPOSE=(docker compose -f "$LOADTEST_DIR/compose.yaml")
DB_PORT="${LOADTEST_DB_PORT:-55432}"
AUTH_URL="${LOADTEST_AUTH_URL:-http://localhost:8080}"
# Catalog 인스턴스의 호스트 포트. 다른 서비스가 8081, 8082를 쓰면 LOADTEST_CATALOG_1_PORT, LOADTEST_CATALOG_2_PORT로 바꾼다.
CATALOG_PORT_1="${LOADTEST_CATALOG_1_PORT:-8081}"
CATALOG_PORT_2="${LOADTEST_CATALOG_2_PORT:-8082}"
DEFAULT_BASE_URLS="http://localhost:${CATALOG_PORT_1},http://localhost:${CATALOG_PORT_2}"

psql_exec() {
  "${COMPOSE[@]}" exec -T postgres psql -U catalog -d catalog -v ON_ERROR_STOP=1 "$@"
}
