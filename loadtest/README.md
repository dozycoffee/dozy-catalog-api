# 부하테스트

[비기능 요구사항](../docs/requirements.md#4-비기능-요구사항)의 목표를 검증하는 도구다. 목표 값은 사용 패턴을 추정한 **가정에 기반한 잠정 값**이고(근거는 #122), 이 도구는 그 목표를 만족하는지 재고 어디서 꺾이는지 찾는다. 무엇을 고칠지는 측정 결과를 보고 따로 정한다. 이 폴더는 측정만 한다.

> 측정 대상은 **Catalog가 받는 요청**(Store 서비스용 조회, 본사 API, 예약 적용과 이벤트 발행)이다. 점주·손님의 매장 요청과 재고 이벤트는 Store로 이전되어(ADR-0020) 여기에 없다. 옛 매장 시나리오는 git 이력(#125~#129)에 있다.

## 구성

```
k6 (호스트) ──▶ Catalog ×2 (컨테이너, 2 vCPU·2GiB) ──▶ PostgreSQL (컨테이너, 2 vCPU·8GiB)
                    │ /actuator/prometheus  └─ 상품 이벤트 ─▶ Kafka   ▲ postgres_exporter, pg_stat_statements
                    ▼                                                  │
              Prometheus ──▶ Grafana ◀────────────────────────┘
토큰: dozy-auth 로컬(호스트, POST /dev/tokens)  ← Catalog 컨테이너가 host.docker.internal로 검증 키를 받는다
```

| 경로 | 내용 |
|---|---|
| `compose.yaml` | PostgreSQL, Kafka(상품 이벤트의 목적지), Catalog ×2(profile `app`), postgres_exporter, Prometheus, Grafana |
| `seed/seed.sql` | 설계 기준 규모의 데이터(`generate_series`). 인자로 여유 규모, 대상 매장이 적은 경우, 한정 판매 없음도 만든다 |
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
loadtest/scripts/seed.sh            # 설계 기준 시드(상품 200, 한정 판매 30개 × 대상 매장 1,000)
loadtest/scripts/run.sh smoke       # 모든 요청이 200인지
loadtest/scripts/run.sh baseline    # 목표의 10%
loadtest/scripts/run.sh target      # 목표 처리량 10분 유지(판정)
```

- 본사 상품 변경(`admin_toggle`)과 예약 적용이 데이터를 바꾸므로 **측정 실행마다 `seed.sh`를 다시 한다**(몇 초 걸린다).
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
| `cold_start` | 정상 피크(기준 정보 조회, 본사 검색)가 계속되는 동안 Store의 유효 옵션 캐시가 한꺼번에 비는 상황(약 40 rps를 10초, 세 번). 약 8분 | 기준 정보·본사 지연 임계값, 몰린 요청의 오류율 |
| `replica_fill` | 정상 피크가 계속되는 동안 상태 목록을 분당 2번 끝까지 훑는다(복제본 채우기). 약 8분 | 기준 정보·본사·상태 목록(페이지) 지연 임계값 |
| `events` | 정상 피크의 배경 부하에 본사 상품 변경(이벤트 발행)을 더한다. 약 11분. `scripts/events.sh`가 이 위에 예약 적용·Kafka 장애를 겹친다 | 지연·오류율 임계값 |
| `probe` | 요청 **하나**를 낮은 고정 부하로 약 2분. 서버 지연과 **요청당 CPU**를 출력한다. `scripts/probe.sh`로 실행 | 없음(요청별 비용 측정) |

- 요청별 목표 처리량과 지연 임계값은 `k6/lib/profiles.js`의 `TARGET_RPS`, `LATENCY_MS`이고 [요구사항 4.3](../docs/requirements.md#43-목표)과 같은 값이다. 거기가 바뀌면 여기도 바꾼다.
- 임계값에는 지연과 오류율 외에 **시작하지 못한 요청(`dropped_iterations`)이 의도한 요청의 0.1% 이하**여야 한다는 조건도 있다(`baseline`, `target`, `n1`, `soak`, `spike`). 서버가 못 따라가서 k6가 시작하지 못한 요청은 오류율에 잡히지 않으므로, 이 조건이 없으면 부하의 대부분을 못 보내고도 통과할 수 있다.
- 워밍업 구간(`warm_*` 시나리오)은 임계값에 넣지 않는다. 임계값은 `measure_*` 시나리오와 요청 이름으로만 건다. 같은 VU가 부르는 토큰 발급이 지연에 섞이지 않게 하기 위해서다.

### 요청 하나씩 재기 (`probe`)

혼합 부하를 포화시키는 프로필(`ramp`, `spike`)은 9~15분이 걸리고, 어느 요청이 비용을 내는지 섞여서 보인다. **요청별 비용을 알아보거나 수정 전후를 비교할 때는 `probe`가 빠르다**(약 2분).

```bash
loadtest/scripts/probe.sh catalog_info            # 기본 부하(초당 10건, ID 100개)로 60초
loadtest/scripts/probe.sh catalog_info 30         # 초당 30건
loadtest/scripts/probe.sh state_list 0.5 120      # 초당 0.5번(한 번에 3페이지) 훑기를 120초
```

요청 이름은 `catalog_info`, `catalog_options`, `catalog_categories`, `state_list`, `admin_search`, `admin_toggle`이다. 출력은 처리량, 서버가 잰 지연(평균, p95, p99), 유휴 CPU를 뺀 **요청당 CPU(ms)**, 이번 실행의 상위 쿼리다.

- 포화시키지 않는 낮은 부하라 줄(큐)이 생기지 않고, 요청당 CPU는 부하 수준과 거의 무관하다. 그래서 장비가 시끄러운 로컬에서도 혼합 부하보다 값이 안정적이다.
- 수정 전후 비교: 같은 요청을 같은 시드에서 `probe`로 재서 요청당 CPU와 p95를 비교한다.
- 요청당 CPU는 앱 CPU 코어(컨테이너 한도로 정규화한 `process_cpu_usage` × 한도) 중 유휴 상태를 뺀 값을 처리량으로 나눈 것이다. JIT, GC, 지표 수집이 섞여 있어 ±수 ms의 오차는 있다.
- 긴 프로필(`ramp`, `target`, `spike`, `soak`)은 최종 검증에 쓴다.

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
| 상품 변경과 이벤트 발행 | `scripts/events.sh schedule`(예약 적용, 이벤트 1분 안 소진), `scripts/events.sh kafka-outage`(Kafka 장애 중 변경 API 영향과 복구 뒤 소진) |
| 캐시 콜드 스타트 | `cold_start` |
| 복제본 처음 채우기 | `replica_fill` |
| 예약 배치와 API 겹침 | `scripts/events.sh schedule`. 배치 진행은 `scripts/psql.sh -c "SELECT status, count(*) FROM scheduled_changes GROUP BY status"` |
| 여유 규모 | `scripts/seed.sh x2` 후 `PRODUCT_SCALE=2 scripts/run.sh ramp` (판정 아님, 한계 확인) |
| 한정 판매 분포 민감도 | `scripts/seed.sh fewstores`(대상 매장 50개), `nolimited`(한정 판매 없음)로 같은 프로필·`probe`를 다시 재서 비교 |
| 사양 조정 | `LOADTEST_APP_CPUS`, `LOADTEST_APP_MEMORY`, `LOADTEST_POOL_SIZE`(앱), `LOADTEST_DB_CPUS`, `LOADTEST_DB_MEMORY`(DB). 바꾼 뒤 `scripts/app.sh start`로 다시 띄운다. 한 번에 하나만 바꿔 다시 잰다 |
| 사양 매트릭스(CPU·메모리·풀) | `scripts/spec.sh <CPU수> <메모리> [프로필...]`(기본 `target spike`). 사양을 바꿔 앱을 다시 띄우고 프로필을 돌린 뒤 JVM 힙·힙 밖·GC(종류, 일시 정지)·프로세스 CPU·풀·5xx와 컨테이너 최대 메모리, `OOMKilled`를 `.run/spec-*.txt`에 요약한다. 예: `scripts/spec.sh 1 1536m`. 풀 크기는 `LOADTEST_POOL_SIZE` |
| 점주·손님 요청, 재고 이벤트 | **이 도구의 범위 밖.** Store로 이전되었다(ADR-0020) |

## 이 도구의 한계

- **로컬에서 포트가 겹치면** `LOADTEST_CATALOG_1_PORT`, `LOADTEST_CATALOG_2_PORT`로 Catalog의 호스트 포트를 바꾸고(기본 8081, 8082), dozy-auth를 다른 포트에서 띄운 경우 `LOADTEST_AUTH_URL`(k6가 부르는 주소)과 `LOADTEST_AUTH_ISSUER`(Catalog 컨테이너가 보는 주소)를 함께 바꾼다.
- **인증 서버를 Gradle `bootRun`으로 띄우면 Gradle 데몬이 멈출 때 함께 죽어** 긴 측정 중에 모든 요청이 실패한다. 긴 프로필(`target`, `events` 등)은 dozy-auth를 jar로 직접 띄워 돌리는 편이 안전하다.
- 같은 장비에서 다른 프로세스가 CPU를 쓰면 요청당 CPU가 2배 넘게 흔들린다(`catalog_info`가 같은 코드에서 13.9ms와 34.4ms로 나왔다). 수정 전후를 비교할 때는 같은 조건에서 연달아 재고, 읽는 행 수(`top-queries.sh`)처럼 장비와 무관한 값을 함께 본다.

- **합격 판정은 별도 장비에서 한다.** Catalog와 DB는 CPU·메모리 제한이 걸린 컨테이너지만 k6, dozy-auth, Docker VM이 같은 장비를 쓴다. 요구사항 4.4의 "부하 도구와 같은 장비에서 돌리지 않는다"를 만족하지 못하므로 **로컬 결과는 병목 탐색용 참고값**이다. 별도 장비에서는 dozy-auth를 Catalog 컨테이너가 닿는 주소에 두고 `LOADTEST_AUTH_ISSUER`로 알려 주면 같은 compose를 쓸 수 있다.
- Docker Desktop(macOS)에서는 컨테이너가 Linux VM 안에서 돌고 호스트에서 컨테이너로 가는 포트는 포워딩을 거친다(k6 → Catalog). Catalog → DB는 같은 compose 네트워크라 포워딩이 없다.
- 상품 id 규칙(`[음료][푸드][MD][단종]` 순서의 연속 번호)은 `seed.sql`과 `k6/lib/config.js`가 같이 기댄다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
- **Store 서비스는 없다.** k6가 Store 서비스의 호출을 흉내 낸다(기준 정보는 ID 100개씩, 캐시 콜드 스타트는 40 rps 몰아치기). Store의 실제 호출 패턴(캐시 시간, 동시 만료)은 Store 문서와 [요구사항 4.2](../docs/requirements.md#42-부하-모델)의 가정을 따르며, 실연동으로 확인되면 시나리오를 고친다.
- Kafka는 구독자 없이 받기만 한다. 릴레이가 보내는 쪽의 동작과 장애를 보고, 구독자의 지연은 이 도구의 범위 밖이다.
- `admin_toggle`은 상품 하나를 단종했다가 다시 활성화하므로 요청마다 상품 이벤트가 쌓인다. 한정 판매 상품의 이벤트는 대상 매장 목록 전체를 담아 크다.
- 시드는 요구사항 4.1의 규모와 같다: 상품 200(전체 500), 한정 판매 30개 × 대상 매장 1,000(3만 행), 옵션 그룹 30, 연결 400, 대기 예약 300(모두 `seed.sh` 출력으로 확인). 한정 판매의 실제 분포는 가정이다(#130).

## 결과 기록 양식

측정 결과는 이슈 #142에 코멘트로 남긴다. 어느 커밋에서 잰 수치인지 알 수 있게 revision을 같이 적는다(`run.sh`가 출력하고 결과 요약 파일 이름에도 들어간다).

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
