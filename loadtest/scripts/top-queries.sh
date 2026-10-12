#!/usr/bin/env bash
# 누적 실행 시간이 큰 쿼리 상위 N개(pg_stat_statements). 측정 전에 reset 하려면 --reset.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [ "${1:-}" = "--reset" ]; then psql_exec -c "SELECT pg_stat_statements_reset()" > /dev/null; echo "초기화함"; exit 0; fi
psql_exec -c "SELECT calls, round(total_exec_time::numeric, 0) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms, rows, left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
              FROM pg_stat_statements WHERE query NOT LIKE '%pg_stat_statements%' ORDER BY total_exec_time DESC LIMIT ${1:-15}"
