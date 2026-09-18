# Catalog 아키텍처

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

어떤 기술로, 코드를 어떻게 구성하는지 다룬다. 주제가 서로 독립적이라 주제별 문서로 나눈다. 새 공통 구현 규칙(영속성, 이벤트 발행, 테스트 전략 등)이 생기면 이 폴더에 문서를 추가하고 아래 목록에 올린다.

| 문서 | 다루는 것 |
|---|---|
| [기술 스택](tech-stack.md) | 언어·프레임워크·영속성·직렬화·테스트 기술의 선택과 이유 |
| [패키지 구조](package-structure.md) | 레이어·패키지 트리, 외부 연동 포트, 애그리거트 간 협력 방식 |
| [예외 구조](exception.md) | `DomainException`·`ErrorCode`·`ErrorType`과 HTTP 상태 매핑 |

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
| 인증·인가 방식 | JWT, 세션 등 구체 방식과 본사관리자·가맹점주 역할 표현 | [기술 스택](tech-stack.md) |
| 예약 배치 스케줄러 | `@Scheduled` 단일 인스턴스 전제인지, 다중 인스턴스에서 스케줄러 자체의 중복 실행을 어떻게 막을지 (예약 row 단위 중복 처리는 `FOR UPDATE SKIP LOCKED`로 대응) | [ERD](../erd.md#동시성-처리) |
| 재고 재동기화 | 재고관리 서비스의 다매장 재고 일괄 조회 API가 확정되면 재활성화·판매 범위 재포함 시점에 재고 투영을 다시 맞춘다 | [도메인 모델](../domain-model.md#주요-결정) |
| ERD 미결정 항목 | Store BC PK 타입, `created_by`/`updated_by` | [ERD](../erd.md#확인-필요-미결정) |
