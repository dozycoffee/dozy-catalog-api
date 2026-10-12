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

psql_exec() {
  "${COMPOSE[@]}" exec -T postgres psql -U catalog -d catalog -v ON_ERROR_STOP=1 "$@"
}
