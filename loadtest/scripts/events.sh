#!/usr/bin/env bash
# 상품 이벤트 발행 시나리오(요구사항 4.3). 정상 피크의 배경 부하(k6 프로필 events: 기준 정보 조회, 본사 검색, 상품 변경)를 걸고
# 그 한가운데에서 상황을 만든 뒤, 아웃박스와 예약의 진행을 5초마다 기록한다.
#   scripts/events.sh schedule       대기 예약의 적용 시각을 당긴다. 예약이 5분 안에 적용되고 이벤트가 1분 안에 나가는지 본다
#   scripts/events.sh kafka-outage   Kafka를 2분 내렸다 올린다. 그동안 변경 API가 영향을 받지 않고 복구 뒤 쌓인 이벤트가 나가는지 본다
# 앱 두 대와 DB, Kafka, dozy-auth가 떠 있고 시드가 있어야 한다(README). 측정 전에 seed.sh를 다시 실행한다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

scenario="${1:?사용법: $0 <schedule|kafka-outage>}"
case "$scenario" in schedule|kafka-outage) ;; *) echo "사용법: $0 <schedule|kafka-outage>" >&2; exit 1 ;; esac

# 아웃박스 대기 건수, 가장 오래된 대기 이벤트의 나이(초), 격리 건수, 대기 예약 건수
sample() {
  psql_exec -tAF ' ' -c "SELECT
      (SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND quarantined_at IS NULL),
      (SELECT coalesce(round(extract(epoch FROM now() - min(created_at)))::int, 0) FROM outbox_events WHERE published_at IS NULL AND quarantined_at IS NULL),
      (SELECT count(*) FROM outbox_events WHERE quarantined_at IS NOT NULL),
      (SELECT count(*) FROM scheduled_changes WHERE status = 'PENDING')" | tr -s ' \n' ' '
}

log="$RUN_DIR/events-$scenario.log"
: > "$log"
start="$(date +%s)"
elapsed() { echo $(( $(date +%s) - start )); }

echo "배경 부하 시작(프로필 events, 약 11분)..."
"$LOADTEST_DIR/scripts/run.sh" events > "$RUN_DIR/events-k6.out" 2>&1 &
k6_pid=$!

# 측정 구간은 시작 3분 뒤부터다. 그 30초 뒤에 상황을 만든다.
trigger_at=210
triggered=0
recovered=0
trigger_time=""
recover_time=""
peak_pending=0
peak_age=0
schedules_done=""
outbox_done=""

while kill -0 "$k6_pid" 2>/dev/null; do
  now="$(elapsed)"
  if [ "$triggered" = 0 ] && [ "$now" -ge "$trigger_at" ]; then
    triggered=1; trigger_time="$now"
    if [ "$scenario" = schedule ]; then
      echo "[$now s] 대기 예약의 적용 시각을 당깁니다"
      "$LOADTEST_DIR/scripts/due-schedules.sh" > /dev/null
    else
      echo "[$now s] Kafka를 내립니다"
      "${COMPOSE[@]}" stop kafka > /dev/null
    fi
  fi
  if [ "$scenario" = kafka-outage ] && [ "$triggered" = 1 ] && [ "$recovered" = 0 ] && [ "$now" -ge $((trigger_at + 120)) ]; then
    recovered=1; recover_time="$now"
    echo "[$now s] Kafka를 올립니다"
    "${COMPOSE[@]}" start kafka > /dev/null
  fi

  read -r pending age quarantined schedules <<< "$(sample)"
  echo "$now $pending $age $quarantined $schedules" >> "$log"
  [ "$pending" -gt "$peak_pending" ] && peak_pending="$pending"
  [ "$age" -gt "$peak_age" ] && peak_age="$age"
  if [ "$triggered" = 1 ]; then
    if [ "$scenario" = schedule ] && [ -z "$schedules_done" ] && [ "$schedules" = 0 ]; then schedules_done="$now"; fi
    if [ -z "$outbox_done" ] && [ "$pending" = 0 ] && { [ "$scenario" = kafka-outage ] && [ "$recovered" = 1 ] || [ -n "$schedules_done" ]; }; then outbox_done="$now"; fi
  fi
  sleep 5
done
wait "$k6_pid" && k6_status=0 || k6_status=$?

echo
echo "== 결과: $scenario =="
echo "기록: $log (경과 초, 아웃박스 대기, 가장 오래된 대기 나이(초), 격리, 대기 예약)"
echo "아웃박스 대기 최대 ${peak_pending}건, 가장 오래된 대기 이벤트의 나이 최대 ${peak_age}초"
if [ "$scenario" = schedule ]; then
  [ -n "$schedules_done" ] && echo "예약이 모두 적용될 때까지 $((schedules_done - trigger_time))초 (목표 300초 이내)" || echo "예약이 끝나지 않았습니다"
  [ -n "$outbox_done" ] && [ -n "$schedules_done" ] && echo "이벤트가 모두 나갈 때까지 예약 적용 완료 뒤 $((outbox_done - schedules_done))초 (목표 60초 이내)"
else
  [ -n "$outbox_done" ] && echo "Kafka 복구 뒤 쌓인 이벤트가 모두 나갈 때까지 $((outbox_done - recover_time))초" || echo "복구 뒤에도 이벤트가 남았습니다. 위 기록을 확인하세요"
fi
echo "격리된 이벤트: $(psql_exec -tAc 'SELECT count(*) FROM outbox_events WHERE quarantined_at IS NOT NULL' | tr -d '[:space:]')건 (0이어야 한다)"
echo
sed -n '/^장비/,/^측정 대상/p' "$RUN_DIR/events-k6.out"
# 임계값 결과만 추린다. 진행 표시줄(✓/✗)이 수천 줄이라 head로 자르면 SIGPIPE로 스크립트가 멈춘다.
sed -n '/█ THRESHOLDS/,/█ TOTAL RESULTS/p' "$RUN_DIR/events-k6.out" | grep -E "✓|✗|http_req_|dropped_iterations" || true
echo "k6 종료 코드: $k6_status (임계값 위반이면 0이 아니다). 전체 출력: $RUN_DIR/events-k6.out"
