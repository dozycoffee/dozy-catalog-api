#!/usr/bin/env bash
# 대기 예약의 적용 시각을 지금으로 당긴다. 예약 적용 배치(1분 간격)가 이 예약들을 처리하는 동안 API 부하를 겹쳐 본다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
psql_exec -c "UPDATE scheduled_changes SET effective_at = now() - interval '1 minute', effective_date = current_date WHERE status = 'PENDING'"
echo "배치가 처리하는 상황은 다음으로 본다:"
echo "  scripts/psql.sh -c \"SELECT status, count(*), min(updated_at), max(updated_at) FROM scheduled_changes GROUP BY status\""
