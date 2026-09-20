# Catalog 아키텍처

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

어떤 기술로, 코드를 어떻게 구성하는지 다룬다. 주제가 서로 독립적이라 주제별 문서로 나눈다. 새 공통 구현 규칙(영속성, 이벤트 발행, 테스트 전략 등)이 생기면 이 폴더에 문서를 추가하고 아래 목록에 올린다.

| 문서 | 다루는 것 |
|---|---|
| [기술 스택](tech-stack.md) | 언어·프레임워크·영속성·직렬화·테스트 기술의 선택과 이유 |
| [패키지 구조](package-structure.md) | 레이어·패키지 트리, 외부 연동 포트, 애그리거트 간 협력 방식 |
| [예외 구조](exception.md) | `DomainException`·`ErrorCode`·`ErrorType`과 HTTP 상태 매핑 |
| [테스트](testing.md) | 계층별 테스트 전략, 작성 관례, 공용 픽스처 원칙 |
| [영속성](persistence.md) | Flyway·Exposed 역할 분담, 마이그레이션 작성 규칙, 매핑, 트랜잭션, 시간 |

## 공통 원칙: 의존 방향

`presentation → application → domain ← infrastructure`

- domain은 아무것도 모른다(프레임워크 무관). HTTP·DB·메시징 개념이 domain에 들어오지 않는다.
- 최상위 패키지는 도메인 모듈(`product`, `store`, `schedule`, `exposure`)이고, 위 네 계층은 각 모듈 안에 있다([ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md)). 모듈 밖에는 `core`(도메인 공통 타입)와 `common`(기술 공통)만 둔다.
- 모듈의 domain은 자기 자신, `core`, 다른 애그리거트·모듈의 ID만 참조한다. 여러 애그리거트를 함께 보는 판단은 그 모듈의 application 정책에 둔다([ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)).
- application은 domain과 자기가 정의한 포트(외부 시스템 포트, 조회 포트)만 안다. 다른 모듈의 도메인 모델은 읽기만 한다.
- infrastructure가 Repository와 포트를 구현하고, Spring이 DI로 연결한다. 배치는 [패키지 구조](package-structure.md)를 따른다.

## 미정 사항

팀이 정해야 할 것을 한곳에 모은다. 결정되면 해당 문서에 반영하고 여기서 지운다.

| 항목 | 내용 | 관련 문서 |
|---|---|---|
| 메시징 기술 | 재고관리 서비스 이벤트 구독과 외부 이벤트 발행에 쓸 브로커(Kafka, RabbitMQ 등). `InventoryEventConsumer`, `DomainEventPublisher` 구현 전에 정해야 한다 | [기술 스택](tech-stack.md), [도메인 모델](../domain-model.md#외부에서-받는-이벤트) |
| Store BC 호출 방식 | REST(WebClient)를 전제로 설계했으나 실제 프로토콜(REST, gRPC 등)은 미확정. 지금은 매장 존재 검증(`ValidateStoreExistsPort`)과 노출 현황 조회의 전체 매장 목록(`StoreDirectoryPort`)이 임시 구현으로 대신하고 있다 | [패키지 구조](package-structure.md#외부-호출-지점) |
| Store 정보 로컬 투영 도입 시점 | Catalog 규칙에 필요한 매장 사실(영업 상태, 지역·유형 등)만 Store BC 이벤트로 받아 Catalog 도메인의 로컬 투영(예: `CatalogStore`)으로 둔다. 번역은 `infrastructure/acl`, 갱신은 application이 맡는다. 매장 진열 설정·판매 가능 여부는 이 투영에 넣지 않고 storeId로 참조하는 별도 애그리거트로 유지한다. Store BC 연동 방식이 정해지거나, 매장 속성을 쓰는 규칙(지역 기반 판매 범위, 폐점 매장 처리 등)이 요구사항에 들어올 때 도입한다. 도입하면 `ValidateStoreExistsPort` 동기 호출을 로컬 조회로 대체할 수 있다(결과적 일관성 감수) | [도메인 모델](../domain-model.md#bc-경계), [패키지 구조](package-structure.md#외부-호출-지점-포트로-인터페이스화-대상) |
| 인증·인가 방식 | JWT, 세션 등 구체 방식과 본사관리자·가맹점주 역할 표현 | [기술 스택](tech-stack.md) |
| 예약 배치 스케줄러 | 실행 주기(대상은 `effective_at <= now`로 고르므로 주기적으로 실행해도 됨), `@Scheduled` 단일 인스턴스 전제인지, 다중 인스턴스에서 스케줄러 자체의 중복 실행을 어떻게 막을지 (예약 row 단위 중복 처리는 `FOR UPDATE SKIP LOCKED`로 대응) | [ERD](../erd.md#동시성-처리) |
| 재고 재동기화 | 재고관리 서비스의 다매장 재고 일괄 조회 API가 확정되면 재활성화·판매 범위 재포함 시점에 재고 투영을 다시 맞춘다 | [ADR-0005](../adr/0005-split-display-setting-and-availability.md) |
| ERD 미결정 항목 | Store BC PK 타입, `created_by`/`updated_by` | [ERD](../erd.md#확인-필요-미결정) |
| 마이그레이션 실행 시점 | 지금은 앱 시작 시 Flyway를 실행한다. 배포 환경이 정해지면 배포 단계에서 따로 실행할지 검토한다 | [영속성](persistence.md), [ADR-0009](../adr/0009-flyway-with-exposed-schema-check.md) |
| 처리되지 않은 예외의 응답 형식 | `DomainException`이 아닌 예외는 지금 Spring 기본 오류 형식(`timestamp`, `path`, `status` 등)의 500으로 나가서, `ErrorResponse(code, message)`와 형식이 다르다. presentation 계층을 만들 때 나머지 예외를 모두 잡는 핸들러로 `ErrorResponse("INTERNAL_ERROR", …)`와 error 로그로 통일할지 정한다 | [예외 구조](exception.md#domainexception과-requirecheck의-구분) |
| 이벤트 처리 방식 전환 조건 | 도메인 이벤트는 지금 `common.event.DomainEventDispatcher`로 같은 트랜잭션에서 동기로 처리한다. 정리 대상이 많아져 응답이 느려지거나, 실패해도 원래 요청을 되돌리면 안 되는 처리(외부 전파)가 들어오거나, 다른 BC로 나가는 이벤트가 생기면 비동기로 바꾼다. 전환 시 아웃박스로 유실을 막고 중간 상태의 노출 판단을 함께 정한다 | [패키지 구조](package-structure.md), [도메인 모델](../domain-model.md#도메인-이벤트) |
| 이벤트 핸들러·메시지 소비의 예외 격리 | HTTP가 아닌 경로에서는 예외가 500으로 바뀌지 않는다. 예약 적용 배치의 건별 격리와 실패 기록은 정해졌다([패키지 구조](package-structure.md#유스케이스-작성-관례)). 남은 것은 메시지 소비 쪽으로, 같은 메시지를 끝없이 재시도하지 않도록 재시도 한도와 처리 불가 메시지 격리 방식을 메시징 기술 결정과 함께 정한다 | [예외 구조](exception.md#domainexception과-requirecheck의-구분), [도메인 모델](../domain-model.md#상태-전이) |
| 의존 규칙 강제 | 모듈 간·애그리거트 간 참조 규칙([ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md), [ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md))을 지금은 코드 리뷰로 지킨다. Konsist·ArchUnit 같은 테스트로 강제할지, 도입한다면 언제 할지 정한다 | [패키지 구조](package-structure.md) |
