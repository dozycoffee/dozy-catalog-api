# ADR-0021: 서비스 간 이벤트는 Kafka로 발행하고, 아웃박스와 폴링 릴레이로 보낸다

- **상태**: 채택
- **날짜**: 2026-10-14
- **관련**: [ADR-0020](0020-store-owns-store-product-state.md)(상품 이벤트가 필요해진 이유), [미정 사항](../architecture/README.md#미정-사항)의 "메시징 기술", 이슈 #130, 공통 "MSA 이벤트 형식", Store의 [ADR-0002](https://github.com/dozycoffee/dozy-store-api/blob/main/docs/adr/0002-minimal-product-replica-with-full-state-events.md)·요구사항 7.1, `dozy-platform`의 ADR-0001(Kafka)·0002(아웃박스)·0004(CloudEvents, 모두 제안 상태)와 공통 이벤트 규약 초안

## 맥락

ADR-0020으로 Catalog는 판단에 필요한 상품 상태를 다른 서비스(Store)에 이벤트로 알려야 한다. 이 발행이 메시징 기술이 정해지지 않아 막혀 있다. 지금 외부 전파는 `ProductEventPublisherPort`가 있지만 구현은 로그만 남기고, 서비스 내부의 도메인 이벤트는 `DomainEventDispatcher`가 같은 트랜잭션에서 동기로 처리한다.

Store가 Catalog에 요구하는 성질(Store ADR-0002, 요구사항 7.1)은 다음과 같다.

- **유실 없음**: 상품 변경이 커밋되면 이벤트도 반드시 나간다. 이벤트를 놓치면 복제본이 다음 변경까지 어긋나고, 판매 범위 정리처럼 변경을 알아야 일어나는 처리가 일어나지 않는다.
- **상품 단위 순서**: 같은 상품의 이벤트는 일어난 순서대로. 순서가 뒤바뀌어도 상품별 순번으로 견딘다.
- **중복 허용**: 같은 이벤트가 다시 와도 구독자가 무시한다.
- **다시 읽기와 구독자 추가**: 구독자가 늘어도 발행 서비스는 바뀌지 않는다.

Catalog의 제약은 다음과 같다.

- 스택은 WebFlux와 코루틴이고 영속성은 R2DBC 기반 Exposed다. 애플리케이션 요청 경로에 JDBC가 없다.
- 트랜잭션 경계는 `TransactionRunner`가 연다(ADR-0011). 예약 적용 배치도 같은 유스케이스를 거친다.
- Spring Boot 4.1.0이 `spring-kafka` 4.1.0과 `kafka-clients` 4.2.1을 관리한다.
- `dozy-platform`은 같은 문제를 다루는 ADR 초안(Kafka, 아웃박스)과 공통 이벤트 규약 초안을 이미 갖고 있다. 둘 다 아직 "제안" 상태이고, 채택은 영향받는 서비스 담당자의 승인을 요구한다. 플랫폼 ADR-0001에 따르면 Reactor Kafka는 개발이 중단되었고 Spring for Apache Kafka의 리액티브 템플릿도 deprecated라 리액티브 클라이언트를 그대로 쓸 수 없다.

### 이벤트 형식

서비스 공통 형식은 "MSA 이벤트 형식"(Kafka 헤더 없이 이벤트 메타데이터와 데이터를 JSON 값 하나에 담는 방식)으로 확정되었다(2026-10-14 확인). `dozy-platform`의 공통 이벤트 규약 초안은 CloudEvents 1.0 structured 모드(헤더 `content-type`, `traceparent`)를 제안하고 있어 이 형식과 다르다. 아래는 그 차이다. 플랫폼 초안은 MSA 이벤트 형식에 맞춰 고쳐야 한다.

| 항목 | MSA 이벤트 형식(확정) | 플랫폼 초안(CloudEvents, 고칠 대상) |
|---|---|---|
| 이벤트 ID | `eventId` | `id` |
| 종류 | `eventType` (`PRODUCT_ACTIVATED`) | `type` (`com.dozycoffee.catalog.product.activated`) |
| 스키마 버전 | `eventVersion` (정수) | 값에 없음. 깨지는 변경은 토픽 `v{major}` |
| 시각 | `occurredAt` (오프셋 포함 ISO-8601) | `time` (UTC, 마이크로초) |
| 발행 서비스 | `source` (`catalog`) | `source` (`/dozy/catalog`) |
| 추적 | 값의 `traceparent` 필드 | Kafka 헤더 `traceparent` |
| 대상 | `data` 안의 ID | `subject` (`products/1024`) |
| 순번 | 정해진 필드 없음 | 확장 속성 `subjectversion` |
| Kafka 헤더 | 쓰지 않음 | `content-type`, `traceparent` |
| 토픽 | `dozy.{서비스}.{집계}` | `{서비스}.{대상}.v{major}` |

## 결정

- **서비스 경계를 넘는 비동기 발행은 Kafka로 한다.** 서비스 내부의 도메인 이벤트(`DomainEventDispatcher`)는 그대로 둔다. 동기 조회는 REST로 둔다. Catalog는 이벤트를 **발행하기만 하고 구독하지 않는다**(재고 이벤트의 소비는 ADR-0020으로 Store로 간다).
- **발행은 트랜잭셔널 아웃박스로 한다.** 상품 변경과 같은 DB 트랜잭션에서 `outbox_events` 테이블에 이벤트를 쓰고, 업무 코드는 Kafka를 직접 호출하지 않는다. 같은 트랜잭션에서 도메인 이벤트를 처리하는 기존 방식(`pullDomainEvents()` 후 동기 핸들러)에 맞춰, 핸들러가 아웃박스에 쓴다. 쓰기가 실패하면 원래 변경도 롤백된다. `ProductEventPublisherPort`의 로그 구현은 이 기록으로 대체한다.
- **릴레이는 Catalog 안의 폴링 방식으로 한다.** 예약 적용 배치 스케줄러와 같은 방식(`@Scheduled`, 앞 실행이 끝난 뒤 간격)이다.
  - 아웃박스에서 미전송 행을 `id` 순서로 읽어 Kafka로 보내고, 브로커 확인(`acks=all`) 뒤 전송 완료로 표시한다.
  - **한 번에 하나만 돈다.** 인스턴스가 여러 대여도 PostgreSQL 트랜잭션 단위 권고 잠금(`pg_try_advisory_xact_lock`, 기존 `AdvisoryLockNamespace`에 번호를 추가)을 잡은 인스턴스만 한 묶음을 처리한다. 새 의존성(분산 잠금 라이브러리)을 들이지 않는다. 같은 상품의 순서를 지키기 위해 행 단위 병렬 처리는 하지 않는다.
  - 프로듀서는 멱등(`enable.idempotence=true`)이고 같은 키는 같은 파티션으로 간다.
  - 영구히 발행되지 못하는 이벤트는 격리 상태로 바꾸고(뒤의 이벤트를 막지 않는다) 운영자가 같은 `eventId`로 다시 발행한다. 재시도 한도와 간격은 릴레이 설계에서 정한다.
  - 전송이 끝난 행은 정리 배치로 지운다.
- **클라이언트는 Spring for Apache Kafka(`spring-kafka`)의 `KafkaTemplate`을 쓴다.** 전송 결과의 `CompletableFuture`를 코루틴에서 `await()`한다. 릴레이는 요청 경로가 아니라 배치 경로이므로 블로킹 클라이언트의 부담이 작다. 리액티브 클라이언트(Reactor Kafka, 리액티브 템플릿)는 쓰지 않는다. 키와 값은 문자열로 보내고 값 JSON은 Jackson 3으로 명시적으로 직렬화한다(저장된 형식이 코드 이름에 기대지 않게, 영속성 문서의 JSONB 규칙과 같은 방식).
- **메시지 형식은 공통 MSA 이벤트 형식을 따른다.** Kafka 헤더를 쓰지 않고 값 JSON 하나에 담는다. 필드 이름은 camelCase, 값이 null이면 필드를 보내지 않는다, 필드 추가에는 `eventVersion`을 올리지 않는다.

  ```json
  {
    "eventId": "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
    "eventType": "PRODUCT_STORE_SCOPE_CHANGED",
    "eventVersion": 1,
    "occurredAt": "2026-10-14T10:00:00+09:00",
    "source": "catalog",
    "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
    "data": {
      "productId": 1024,
      "sku": "DZ-00001024",
      "status": "ACTIVE",
      "storeScope": { "kind": "LIMITED", "targetStoreIds": [12, 35, 107] },
      "tracksInventory": false,
      "sequence": 42
    }
  }
  ```

  - **토픽**은 `dozy.catalog.product` 하나다. 상품의 등록, 활성화, 단종, 판매 범위 변경, 삭제는 순서를 함께 지켜야 해서 한 토픽에 둔다. **키**는 `productId`의 문자열이다.
  - **`eventType`**은 `PRODUCT_REGISTERED`, `PRODUCT_ACTIVATED`, `PRODUCT_DISCONTINUED`, `PRODUCT_STORE_SCOPE_CHANGED`, `PRODUCT_DELETED`다. 구독자는 종류와 관계없이 `data`의 상태로 복제본을 교체한다.
  - **`data`는 상품 상태 전체**다. 상품 ID, SKU, 상태, 판매 범위(`kind`와, `LIMITED`일 때만 `targetStoreIds`), 재고 추적 여부, `sequence`. 삭제 이벤트는 `productId`, `sequence`, `deleted: true`만 담는다.
  - **`sequence`는 상품별 순번**이다. 같은 상품의 이벤트끼리 뒤에 일어난 것이 항상 크다. 값은 Product의 낙관적 잠금 버전(저장 후 값)이고, 삭제는 삭제 직전 버전에 1을 더한다. 건너뛰어도 된다. 한 트랜잭션에서 한 상품에 이벤트가 여러 개 생기면 마지막 상태 하나로 합쳐서 같은 순번이 둘 나가지 않게 한다.
  - **`occurredAt`**은 트랜잭션 안에서 아웃박스에 기록한 시각이다. **`traceparent`**는 요청 안에서 만든 이벤트에만 있고, 예약 배치처럼 요청이 없는 경로는 보내지 않는다. 릴레이는 다른 시점에 보내므로 아웃박스에 쓸 때 현재 trace context를 저장해 둔다.
  - 구독자는 `eventId`로 중복을 거르고 `sequence`가 더 클 때만 상태를 교체한다. 처음 채우기와 다시 맞추기에 쓰는 전체 상품 상태 목록 API는 같은 `data` 형태와 `sequence`를 담는다.
  - 이벤트 계약(필드 정의, 예시, 변경 이력)은 Catalog의 `docs/events/`에 둔다.
- **토픽 설정(파티션 수, 보존 기간, 복제), 브로커 인증, 운영 형태는 플랫폼이 정한다.** 키와 파티션의 대응이 바뀌면 순서가 깨질 수 있으므로 파티션 수는 처음 정하고 늘리지 않는 것을 전제로 한다.
- **로컬과 테스트**: 서비스 저장소의 `compose.yaml`에 단일 노드 Kafka(KRaft)를 두고 호스트 포트는 고정하지 않는다. 통합 테스트는 Testcontainers의 Kafka를 쓴다.
- **관측**: 아웃박스의 미전송 행 수, 가장 오래된 행의 나이, 격리된 행 수를 지표로 낸다. 이름은 서비스 지표 규칙(`dozy.catalog.` 접두어, 정해진 값만 태그)을 따른다.

## 검토한 대안

- **다른 브로커(RabbitMQ, Redis Streams, 클라우드 매니지드 큐)**: 운영이 가볍거나 이미 쓰는 인프라일 수 있다. 하지만 소비한 메시지가 사라지거나(다시 읽기 불가), 키 단위 순서를 따로 만들어야 하거나, 배포 환경이 정해지지 않았다. 플랫폼 ADR-0001이 같은 이유로 Kafka를 제안했다.
- **커밋 후 바로 전송(after-commit)**: 아웃박스가 필요 없다. 하지만 커밋과 전송 사이에 서버가 죽으면 이벤트가 사라지고, 복제본은 다음 변경까지 어긋난다.
- **CDC(Debezium)로 아웃박스를 릴레이**: 폴링 지연과 부하가 없다. 하지만 Kafka Connect를 따로 운영해야 하고, 발행 서비스가 하나뿐인 지금은 부담이 이득보다 크다. 폴링의 지연이나 부하가 문제가 되면 다시 검토한다.
- **Kafka 트랜잭션(exactly-once)**: Kafka 안에서만 보장되고 DB 쓰기와는 묶이지 않는다. 구독자의 DB 반영까지는 결국 멱등 처리가 필요하다.
- **클라이언트로 Reactor Kafka나 리액티브 템플릿**: 코루틴과 맞지만 개발 중단·deprecated다.
- **클라이언트로 `kafka-clients` 직접 사용**: 의존성이 가볍다. 하지만 Boot 설정, Micrometer 지표, 직렬화·재시도 보일러플레이트를 직접 만들어야 한다. `spring-kafka`는 같은 `kafka-clients` 위에 이를 제공한다.
- **플랫폼 초안의 CloudEvents structured 모드로 형식을 정함**: 표준이고 도구가 있다. 하지만 서비스 공통 형식은 MSA 이벤트 형식으로 확정되었으므로 그것을 따른다.
- **Kafka 릴레이 잠금으로 ShedLock 같은 분산 잠금 라이브러리**: 플랫폼 ADR-0002가 제안한 방식이다. 하지만 Catalog는 이미 PostgreSQL 권고 잠금을 쓰고 있어 새 의존성 없이 같은 효과를 낼 수 있다.
- **행마다 병렬로 릴레이(`FOR UPDATE SKIP LOCKED`)**: 처리량이 높다. 하지만 같은 상품의 이벤트가 인스턴스에 흩어져 순서가 깨진다. 상품 변경 빈도는 낮아 단일 릴레이로 충분하다.

## 결과

- **얻는 것**
  - ADR-0020의 선결 조건(이벤트 발행)이 풀린다.
  - 상품 변경과 이벤트가 함께 저장되거나 함께 사라진다. 같은 상품의 이벤트는 같은 파티션에서 순서를 지키고, 어긋나는 경우는 순번으로 견딘다.
  - 구독자가 늘어도 Catalog는 바뀌지 않는다.
  - 업무 코드는 Kafka를 모른다. 발행 수단이나 형식을 바꿔도 직렬화와 릴레이 쪽만 바뀐다.
  - 새 의존성은 `spring-kafka` 하나다. 릴레이의 단일 실행은 기존 권고 잠금으로 해결한다.
- **감수하는 것**
  - 아웃박스 테이블, 릴레이, 정리 배치, 격리 처리, 지표를 새로 만들고 운영한다.
  - 폴링 간격만큼 전달이 늦다. 단종·판매 범위 변경이 매장에 반영되기까지 지연은 이 간격과 Store의 처리 시간을 더한 값이다.
  - 릴레이가 DB 트랜잭션을 열고 Kafka 응답을 기다린다. 한 번에 보내는 건수와 전송 타임아웃을 작게 두어야 한다. 단일 릴레이라 처리량에 상한이 있지만, 상품 변경 빈도(예약이 00시에 수백 건 적용되는 정도)에는 충분하다고 본다.
  - 블로킹 Kafka 클라이언트를 쓰므로 릴레이를 이벤트 루프가 아닌 전용 디스패처에서 돌려야 한다.
  - **플랫폼 문서(공통 이벤트 규약 초안, ADR-0004)가 이 형식과 다르다.** 플랫폼 문서를 MSA 이벤트 형식에 맞춰 고쳐야 하고, 그 전까지 문서가 어긋난다. Store의 구독 구현은 MSA 이벤트 형식을 기준으로 해야 한다.
  - `sequence`가 Product의 낙관적 잠금 버전에 기댄다. 버전을 올리는 방식을 바꾸면 이벤트 순번에 영향이 간다.
  - 한정 판매 상품의 판매 범위가 대상 매장 목록 전체를 담는다. 매장 3,000개를 모두 대상으로 하면 ID만 약 20KB이고 Kafka 기본 메시지 크기 한도(약 1MB) 안이지만, 매장 수가 크게 늘면 한도를 다시 봐야 한다.
  - 파티션 수를 늘리기 어렵다.
  - Kafka 클러스터 운영 부담이 생긴다(플랫폼 담당).
- **후속 작업**
  - 플랫폼의 공통 이벤트 규약 초안과 ADR-0004(CloudEvents)를 MSA 이벤트 형식에 맞춰 고치도록 플랫폼에 요청한다. 플랫폼 ADR-0001·0002의 채택 승인에 Catalog 담당이 참여한다. Store에는 구독 구현의 기준이 MSA 이벤트 형식임을 알린다.
  - 이벤트 계약 문서 `docs/events/`를 쓴다(`dozy.catalog.product`의 필드, 예시, 변경 이력). Store 요구사항 7.1과 맞춘다.
  - 아웃박스 테이블 설계(ERD, 마이그레이션)와 릴레이 설계(묶음 크기, 타임아웃, 재시도 한도와 간격, 격리와 재발행 절차, 정리 배치)를 정한다.
  - `spring-kafka` 의존성, `catalog.kafka.*` 설정, `compose.yaml`의 Kafka, Testcontainers 통합 테스트를 추가한다.
  - [미정 사항](../architecture/README.md#미정-사항)의 "메시징 기술"을 지우고, 기술 스택 문서에 메시징을 올렸다(이 PR). 패키지 구조의 외부 연동 표와 도메인 모델의 도메인 이벤트 절은 구현 PR에서 고친다.
  - 토픽 생성(파티션 수, 보존, 복제), 브로커 인증, 운영 형태는 플랫폼과 정한다.
  - ADR-0020 이행 1단계(이벤트 발행과 조회 API 추가)의 첫 작업으로 이 결정을 구현한다.
