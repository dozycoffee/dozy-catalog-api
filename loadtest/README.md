# 부하테스트

[비기능 요구사항](../docs/requirements.md#4-비기능-요구사항)의 목표를 검증하는 도구다. 목표 값은 사용 패턴을 추정한 **가정에 기반한 잠정 값**이고(근거는 #122), 이 도구는 그 목표를 만족하는지 재고 어디서 꺾이는지 찾는다. 무엇을 고칠지는 측정 결과를 보고 따로 정한다. 이 폴더는 측정만 한다.

## 구성

```
k6 (호스트) ──▶ Catalog ×2 (컨테이너, 2 vCPU·2GiB) ──▶ PostgreSQL (컨테이너, 2 vCPU·8GiB)
                    │ /actuator/prometheus                   ▲ postgres_exporter, pg_stat_statements
                    ▼                                         │
              Prometheus ──▶ Grafana ◀────────────────────────┘
토큰: dozy-auth 로컬(호스트, POST /dev/tokens)  ← Catalog 컨테이너가 host.docker.internal로 검증 키를 받는다
```

| 경로 | 내용 |
|---|---|
| `compose.yaml` | PostgreSQL, Catalog ×2(profile `app`), postgres_exporter, Prometheus, Grafana |
| `seed/seed.sql` | 설계 기준 규모의 데이터(`generate_series`). 인자로 여유 규모도 만든다 |
| `k6/` | 시나리오(`main.js`)와 프로필(`lib/profiles.js`), 요청·토큰 도우미 |
| `scripts/` | 실행 스크립트(`up`, `image`, `app`, `seed`, `run`, `top-queries`, `due-schedules` 등) |
| `grafana/` | 데이터소스와 대시보드 "Catalog 부하테스트" |
| `.run/` | 로그와 k6 결과 요약(커밋하지 않음) |

## 준비

- Docker, `k6`(`brew install k6`), JDK 21
- **Docker Desktop 메모리**: 기본 8GB다. 초기 사양의 DB(8GiB)까지 담으려면 16GB 이상으로 올린다. 올리지 않으면 `loadtest/.env`에 `LOADTEST_DB_MEMORY=4g`를 두고 개발·스모크용으로만 쓴다(`.env.example` 참고).
- **dozy-auth 로컬 실행**: 토큰을 `POST /dev/tokens`(local·dev 프로필 전용)로 받는다. Catalog가 컨테이너라서 토큰의 `iss`가 컨테이너에서 닿는 주소여야 한다. dozy-auth 저장소에서 아래처럼 띄운다(기본 8080). 환경 변수가 프로필 파일의 `localhost:8080`보다 우선하므로 dozy-auth 코드는 바꾸지 않는다.

```bash
DOZY_AUTH_ISSUER_BASE_URI=http://host.docker.internal:8080 ./gradlew :auth-server:bootRun --args='--spring.profiles.active=local'
```

  k6는 호스트에서 `http://localhost:8080`으로 토큰을 받는다(`LOADTEST_AUTH_URL`). issuer 주소를 다르게 쓰면 `LOADTEST_AUTH_ISSUER`로 Catalog에 알려 준다.

## 실행

```bash
loadtest/scripts/up.sh              # DB, Prometheus, Grafana
loadtest/scripts/image.sh           # Catalog 이미지(현재 체크아웃). 코드를 바꾸면 다시 만든다
loadtest/scripts/app.sh start 2     # Catalog 컨테이너 두 대(8081, 8082). 첫 기동에서 Flyway가 스키마를 만든다
loadtest/scripts/seed.sh            # 설계 기준 시드(매장 3,000, 상품 200)
loadtest/scripts/run.sh smoke       # 모든 요청이 200인지
loadtest/scripts/run.sh baseline    # 목표의 10%
loadtest/scripts/run.sh target      # 목표 처리량 10분 유지(판정)
```

- 변경 요청(진열, 숨김, 품절)이 데이터를 바꾸므로 **측정 실행마다 `seed.sh`를 다시 한다**(몇 초 걸린다).
- Grafana: http://localhost:3001, Prometheus: http://localhost:9091
- 정리: `scripts/app.sh stop`, `scripts/down.sh`(데이터까지 지우려면 `--volumes`). 앱 로그는 `scripts/app.sh logs <번호>`

### 측정 전 점검

- 같은 장비에서 돌리는 로컬 측정은 **다른 프로세스에 크게 흔들린다.** 같은 부하의 `baseline`을 같은 코드로 여러 번 돌렸을 때 목록 p95가 270ms에서 4초까지 나왔다(Chrome이 CPU 100%를 쓰고 있었다). `scripts/run.sh`는 시작할 때 로드 평균을 보여 주고 높으면 경고한다.
- 측정 전에 브라우저, IDE, 그라파나 탭을 닫는다. 대시보드는 5초마다 갱신되므로 측정 중에 열어 두면 그 자체가 부하다.
- 같은 프로필을 **여러 번** 돌려 편차를 보고, 편차가 큰 값은 판단에 쓰지 않는다. 판정은 별도 장비에서 한다.

### 프로필

| 프로필 | 내용 | 판정 |
|---|---|---|
| `smoke` | 요청마다 10번. 모두 200인지 | 체크 100% |
| `baseline` | 워밍업 1분 + 목표의 10%를 2분 | 지연·오류율 임계값(참고) |
| `target` | 워밍업 3분 + **목표 처리량 10분** | 지연·오류율 임계값 |
| `ramp` | 목표의 0.5 → 1 → 1.5 → 2 → 3배를 단계별로 | 없음(꺾이는 지점을 찾는다) |
| `spike` | 워밍업 후 목표의 1.5배(= 정상 피크의 3배)를 5분 | 오류율만 |
| `n1` | 한 대를 내린 상태에서 정상 피크(목표의 0.5배)를 10분. `BASE_URLS`를 남은 한 대로 준다 | 지연·오류율 임계값 |
| `soak` | 워밍업 3분 + 목표 처리량 30분 | 지연·오류율 임계값 |
| `contention` | 매장 1, 2에 진열 순서 변경을 20 VU로 2분 | 없음(지연 확인) |

- 요청별 목표 처리량과 지연 임계값은 `k6/lib/profiles.js`의 `TARGET_RPS`, `LATENCY_MS`이고 [요구사항 4.3](../docs/requirements.md#43-목표)과 같은 값이다. 거기가 바뀌면 여기도 바꾼다.
- 임계값에는 지연과 오류율 외에 **시작하지 못한 요청(`dropped_iterations`)이 의도한 요청의 0.1% 이하**여야 한다는 조건도 있다(`baseline`, `target`, `n1`, `soak`, `spike`). 서버가 못 따라가서 k6가 시작하지 못한 요청은 오류율에 잡히지 않으므로, 이 조건이 없으면 부하의 대부분을 못 보내고도 통과할 수 있다.
- 워밍업 구간(`warm_*` 시나리오)은 임계값에 넣지 않는다. 임계값은 `measure_*` 시나리오와 요청 이름으로만 건다. 같은 VU가 부르는 토큰 발급이 지연에 섞이지 않게 하기 위해서다.

### 합격 판정은 서버가 잰 값으로

요구사항 4.4에 따라 판정은 서버가 잰 요청 처리 시간(인스턴스 합산)이다. k6가 본 지연은 참고용으로 같이 기록한다. 서버 지표는 Prometheus에서 본다.

```promql
# URI별 p95·p99 (히스토그램)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[2m])))
# 커넥션 풀에서 대기 중인 요청
max(r2dbc_pool_pending_connections)
# 5xx
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
```

쿼리별 DB 시간은 `scripts/top-queries.sh [개수]`다(`run.sh`가 시작할 때 통계를 비운다).

## 요구사항별 확인 방법

| 요구사항 | 방법 |
|---|---|
| 처리량·지연 목표 | `target`, 길게 보려면 `soak` |
| 스파이크 | `spike` |
| 한 대 장애(N-1) | `scripts/app.sh stop 2` 후 `BASE_URLS=http://localhost:8081 scripts/run.sh n1` |
| 진열 순서 경합 | `contention` |
| 예약 배치와 API 겹침 | `scripts/due-schedules.sh`로 대기 예약 적용 시각을 당긴 뒤(배치는 1분 간격) 바로 `target`을 실행. 배치 진행은 `scripts/psql.sh -c "SELECT status, count(*) FROM scheduled_changes GROUP BY status"` |
| 여유 규모 | `scripts/seed.sh x2` 후 `PRODUCT_SCALE=2 scripts/run.sh ramp` (판정 아님, 한계 확인) |
| 사양 조정 | `LOADTEST_APP_CPUS`, `LOADTEST_APP_MEMORY`, `LOADTEST_POOL_SIZE`(앱), `LOADTEST_DB_CPUS`, `LOADTEST_DB_MEMORY`(DB). 바꾼 뒤 `scripts/app.sh start`로 다시 띄운다. 한 번에 하나만 바꿔 다시 잰다 |
| 재고 이벤트 반영·일괄 재입고 폭주 | **아직 없음.** 이벤트 소비자(메시징)가 정해지지 않아 HTTP로 부를 수 없다. 메시징이 정해지면 추가한다 |

## 이 도구의 한계

- **합격 판정은 별도 장비에서 한다.** Catalog와 DB는 CPU·메모리 제한이 걸린 컨테이너지만 k6, dozy-auth, Docker VM이 같은 장비를 쓴다. 요구사항 4.4의 "부하 도구와 같은 장비에서 돌리지 않는다"를 만족하지 못하므로 **로컬 결과는 병목 탐색용 참고값**이다. 별도 장비에서는 dozy-auth를 Catalog 컨테이너가 닿는 주소에 두고 `LOADTEST_AUTH_ISSUER`로 알려 주면 같은 compose를 쓸 수 있다.
- Docker Desktop(macOS)에서는 컨테이너가 Linux VM 안에서 돌고 호스트에서 컨테이너로 가는 포트는 포워딩을 거친다(k6 → Catalog). Catalog → DB는 같은 compose 네트워크라 포워딩이 없다.
- 상품 id 규칙(`id % 20 < 3`이면 한정 판매 등)은 `seed.sql`과 `k6/lib/config.js`가 같이 기댄다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
- 시드는 요구사항 4.4의 규모와 같다: 개별 설정 약 16만 행, 재고 추적 17.5만, 수동 품절 약 1.8만, 옵션 그룹 30, 연결 400, 대기 예약 300(모두 `seed.sh` 출력으로 확인).

## 결과 기록 양식

측정 결과는 이슈 #123에 코멘트로 남긴다. 어느 커밋에서 잰 수치인지 알 수 있게 revision을 같이 적는다(`run.sh`가 출력하고 결과 요약 파일 이름에도 들어간다).

```markdown
### <프로필> 측정 (<날짜>)
- revision: <커밋 SHA 앞 7자리>, 환경: <로컬 참고용 | 별도 장비 + 사양>, 시드: <base | x2>
- 인스턴스: Catalog <N>대 (<CPU> vCPU, 힙 <값>, 풀 <값>), DB <CPU> vCPU, <메모리>

| 요청 | 목표 처리량 | 실제 | p95 (서버) | 목표 | p99 (서버) | 목표 | 판정 |
|---|---|---|---|---|---|---|---|

- 오류율(5xx): / 풀 대기 최대: / CPU 최대: / 힙: 
- 눈에 띄는 쿼리(`top-queries.sh`):
- 관찰과 다음 할 일(고치지 않고 이슈로 분리):
```
