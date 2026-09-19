# Catalog 아키텍처

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

어떤 기술로, 코드를 어떻게 구성하는지 다룬다. 주제가 서로 독립적이라 주제별 문서로 나눈다. 새 공통 구현 규칙(영속성, 이벤트 발행, 테스트 전략 등)이 생기면 이 폴더에 문서를 추가하고 아래 목록에 올린다.

| 문서 | 다루는 것 |
|---|---|
| [기술 스택](tech-stack.md) | 언어·프레임워크·영속성·직렬화·테스트 기술의 선택과 이유 |
| [패키지 구조](package-structure.md) | 레이어·패키지 트리, 외부 연동 포트, 애그리거트 간 협력 방식 |
| [예외 구조](exception.md) | `DomainException`·`ErrorCode`·`ErrorType`과 HTTP 상태 매핑 |
| [테스트](testing.md) | 계층별 테스트 전략, 작성 관례, 공용 픽스처 원칙 |

## 공통 원칙: 의존 방향

`presentation → application → domain ← infrastructure`

- domain은 아무것도 모른다(프레임워크 무관). HTTP·DB·메시징 개념이 domain에 들어오지 않는다.
- application은 domain과 외부 연동용 포트만 안다.
- infrastructure가 포트와 Repository를 구현하고, Spring이 DI로 연결한다.

## 미정 사항

팀이 정해야 할 것을 한곳에 모은다. 결정되면 해당 문서에 반영하고 여기서 지운다.

| 항목 | 내용 | 관련 문서 |
|---|---|---|
| 메시징 기술 | 재고관리 서비스 이벤트 구독과 외부 이벤트 발행에 쓸 브로커(Kafka, RabbitMQ 등). `InventoryEventConsumer`, `DomainEventPublisher` 구현 전에 정해야 한다 | [기술 스택](tech-stack.md), [도메인 모델](../domain-model.md#외부에서-받는-이벤트) |
| Store BC 호출 방식 | REST(WebClient)를 전제로 설계했으나 실제 프로토콜(REST, gRPC 등)은 미확정 | [패키지 구조](package-structure.md#외부-호출-지점-포트로-인터페이스화-대상) |
| Store 정보 로컬 투영 도입 시점 | Catalog 규칙에 필요한 매장 사실(영업 상태, 지역·유형 등)만 Store BC 이벤트로 받아 Catalog 도메인의 로컬 투영(예: `CatalogStore`)으로 둔다. 번역은 `infrastructure/acl`, 갱신은 application이 맡는다. 매장 진열 설정·판매 가능 여부는 이 투영에 넣지 않고 storeId로 참조하는 별도 애그리거트로 유지한다. Store BC 연동 방식이 정해지거나, 매장 속성을 쓰는 규칙(지역 기반 판매 범위, 폐점 매장 처리 등)이 요구사항에 들어올 때 도입한다. 도입하면 `ValidateStoreExistsPort` 동기 호출을 로컬 조회로 대체할 수 있다(결과적 일관성 감수) | [도메인 모델](../domain-model.md#bc-경계), [패키지 구조](package-structure.md#외부-호출-지점-포트로-인터페이스화-대상) |
| 인증·인가 방식 | JWT, 세션 등 구체 방식과 본사관리자·가맹점주 역할 표현 | [기술 스택](tech-stack.md) |
| 예약 배치 스케줄러 | `@Scheduled` 단일 인스턴스 전제인지, 다중 인스턴스에서 스케줄러 자체의 중복 실행을 어떻게 막을지 (예약 row 단위 중복 처리는 `FOR UPDATE SKIP LOCKED`로 대응) | [ERD](../erd.md#동시성-처리) |
| 재고 재동기화 | 재고관리 서비스의 다매장 재고 일괄 조회 API가 확정되면 재활성화·판매 범위 재포함 시점에 재고 투영을 다시 맞춘다 | [ADR-0005](../adr/0005-split-display-setting-and-availability.md) |
| ERD 미결정 항목 | Store BC PK 타입, `created_by`/`updated_by` | [ERD](../erd.md#확인-필요-미결정) |
| 처리되지 않은 예외의 응답 형식 | `DomainException`이 아닌 예외는 지금 Spring 기본 오류 형식(`timestamp`, `path`, `status` 등)의 500으로 나가서, `ErrorResponse(code, message)`와 형식이 다르다. presentation 계층을 만들 때 나머지 예외를 모두 잡는 핸들러로 `ErrorResponse("INTERNAL_ERROR", …)`와 error 로그로 통일할지 정한다 | [예외 구조](exception.md#domainexception과-requirecheck의-구분) |
| 이벤트 핸들러·배치의 예외 격리 | HTTP가 아닌 경로에서는 예외가 500으로 바뀌지 않는다. 한 건의 실패가 배치 전체를 멈추거나 같은 메시지를 끝없이 재시도하지 않도록 건별 격리, 실패 기록(예약은 `FAILED`), 재시도 한도, 처리 불가 메시지 격리 방식을 정한다. 메시징 기술 결정과 함께 정한다 | [예외 구조](exception.md#domainexception과-requirecheck의-구분), [도메인 모델](../domain-model.md#상태-전이) |
| 테스트 전략 | application 테스트의 저장소(가짜 또는 Testcontainers), 통합 테스트 데이터 정리 방식, 단위·통합 테스트 분리와 CI 시간 | [테스트](testing.md#미정-사항) |
