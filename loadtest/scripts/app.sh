#!/usr/bin/env bash
# Catalog 서버를 jar로 직접 띄우고 내린다. 인스턴스 i는 포트 8080+i(8081, 8082)를 쓴다.
#   scripts/app.sh start [개수=2]   scripts/app.sh stop [번호|all=all]   scripts/app.sh status
#
# 컨테이너가 아니라 호스트 프로세스로 띄우는 이유: 로컬 dozy-auth가 발급하는 토큰의 iss가 http://localhost:8080이라
# 컨테이너 안(localhost = 컨테이너 자신)에서는 검증 키를 받을 수 없다. CPU·힙은 JVM 옵션으로 근사한다.
# 합격 판정은 컨테이너 자원 제한이 걸린 별도 장비에서 한다(README).
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

JAR="$REPO_DIR/build/libs/dozy-catalog-api.jar"
CPUS="${LOADTEST_APP_CPUS:-2}"
HEAP="${LOADTEST_APP_HEAP:-1536m}"
POOL="${LOADTEST_POOL_SIZE:-10}"

start_one() {
  local i="$1" port=$((8080 + $1))
  local pidfile="$RUN_DIR/app-$i.pid" logfile="$RUN_DIR/app-$i.log"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "인스턴스 $i는 이미 떠 있습니다 (pid $(cat "$pidfile"))"; return
  fi
  SPRING_PROFILES_ACTIVE=prod \
  DB_HOST=localhost DB_PORT="$DB_PORT" DB_NAME=catalog DB_USERNAME=catalog DB_PASSWORD=catalog \
  DOZY_AUTH_ISSUER_BASE_URI="$AUTH_URL" CATALOG_CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  SERVER_PORT="$port" SPRING_R2DBC_POOL_MAX_SIZE="$POOL" SPRING_R2DBC_POOL_INITIAL_SIZE="$POOL" \
  CATALOG_REVISION="$(git -C "$REPO_DIR" rev-parse HEAD)" CATALOG_RELEASE_VERSION=loadtest \
  JAVA_TOOL_OPTIONS="-Xms$HEAP -Xmx$HEAP -XX:ActiveProcessorCount=$CPUS" \
    nohup java -jar "$JAR" > "$logfile" 2>&1 &
  echo $! > "$pidfile"
  echo "인스턴스 $i 시작 (포트 $port, pid $!, 로그 $logfile)"
}

wait_ready() {
  local port=$((8080 + $1))
  for _ in $(seq 1 90); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health" || true)" = "200" ]; then
      echo "인스턴스 $1 준비됨"; return
    fi
    sleep 1
  done
  echo "인스턴스 $1이 90초 안에 준비되지 않았습니다. 로그: $RUN_DIR/app-$1.log" >&2; exit 1
}

stop_one() {
  local pidfile="$RUN_DIR/app-$1.pid"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    kill "$(cat "$pidfile")"; echo "인스턴스 $1 종료"
  fi
  rm -f "$pidfile"
}

case "${1:-}" in
  start)
    [ -f "$JAR" ] || { echo "$JAR 가 없습니다. 먼저 ./gradlew bootJar" >&2; exit 1; }
    n="${2:-2}"
    for i in $(seq 1 "$n"); do start_one "$i"; done
    for i in $(seq 1 "$n"); do wait_ready "$i"; done ;;
  stop)
    target="${2:-all}"
    if [ "$target" = all ]; then
      for f in "$RUN_DIR"/app-*.pid; do [ -e "$f" ] && stop_one "$(basename "$f" .pid | sed 's/app-//')"; done
    else stop_one "$target"; fi ;;
  status)
    for f in "$RUN_DIR"/app-*.pid; do
      [ -e "$f" ] || continue
      i="$(basename "$f" .pid | sed 's/app-//')"
      if kill -0 "$(cat "$f")" 2>/dev/null; then echo "인스턴스 $i 실행 중 (포트 $((8080 + i)), pid $(cat "$f"))"
      else echo "인스턴스 $i 종료됨"; fi
    done ;;
  *) echo "사용법: $0 start [개수] | stop [번호|all] | status" >&2; exit 1 ;;
esac
