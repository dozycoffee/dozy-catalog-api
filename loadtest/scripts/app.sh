#!/usr/bin/env bash
# Catalog 서버 컨테이너를 띄우고 내린다. 인스턴스 i는 catalog-i 컨테이너이고 호스트 포트 8080+i(8081, 8082)에 열린다.
#   scripts/app.sh start [개수=2]   scripts/app.sh stop [번호|all=all]   scripts/app.sh status   scripts/app.sh logs <번호>
# 이미지는 scripts/image.sh로 먼저 만든다. 코드를 바꾸면 이미지를 다시 만들고 start를 다시 한다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

wait_ready() {
  local var="CATALOG_PORT_$1"
  local port="${!var}"
  for _ in $(seq 1 120); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health" || true)" = "200" ]; then
      echo "인스턴스 $1 준비됨 (포트 $port)"; return
    fi
    sleep 1
  done
  echo "인스턴스 $1이 120초 안에 준비되지 않았습니다. scripts/app.sh logs $1 로 확인하세요" >&2; exit 1
}

case "${1:-}" in
  start)
    docker image inspect dozy-catalog-api:loadtest > /dev/null 2>&1 || { echo "이미지가 없습니다. 먼저 scripts/image.sh" >&2; exit 1; }
    n="${2:-2}"
    services=(); for i in $(seq 1 "$n"); do services+=("catalog-$i"); done
    "${COMPOSE[@]}" --profile app up -d --force-recreate "${services[@]}"
    for i in $(seq 1 "$n"); do wait_ready "$i"; done ;;
  stop)
    target="${2:-all}"
    if [ "$target" = all ]; then "${COMPOSE[@]}" --profile app stop catalog-1 catalog-2
    else "${COMPOSE[@]}" --profile app stop "catalog-$target"; fi ;;
  status) "${COMPOSE[@]}" --profile app ps catalog-1 catalog-2 ;;
  logs) "${COMPOSE[@]}" --profile app logs --tail "${3:-100}" "catalog-${2:?번호를 주세요}" ;;
  *) echo "사용법: $0 start [개수] | stop [번호|all] | status | logs <번호>" >&2; exit 1 ;;
esac
