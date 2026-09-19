# ADR-0011: 트랜잭션 경계는 application의 TransactionRunner로 연다

- **상태**: 채택
- **날짜**: 2026-09-19
- **관련**: 이슈 #45, ADR-0001, ADR-0008, [영속성 규칙](../architecture/persistence.md)

## 맥락

ADR-0008은 트랜잭션 경계를 application이 정한다고 했다. 그런데 트랜잭션을 실제로 여는 수단은 Exposed R2DBC의 `suspendTransaction`이다. application이 이를 직접 호출하면 infrastructure(Exposed)를 알게 되어 의존 방향(ADR-0001)이 깨진다. Spring의 `@Transactional`은 Exposed R2DBC 트랜잭션과 별개로 동작한다.

## 결정

- application에 `TransactionRunner` 인터페이스(`suspend fun <T> inTransaction(block: suspend () -> T): T`)를 둔다. infrastructure가 Exposed `suspendTransaction`으로 구현한다.
- application 서비스는 유스케이스의 트랜잭션 경계를 `inTransaction { … }`으로 연다. 블록 안의 Repository 호출은 모두 같은 트랜잭션이고, 블록이 예외로 끝나면 모두 롤백된다.
- 이미 트랜잭션 안에서 다시 호출하면 바깥 트랜잭션을 이어 쓴다(통합 테스트로 확인).
- Spring `@Transactional`과 섞지 않는다.

## 검토한 대안

- **application이 Exposed `suspendTransaction`을 직접 호출**: application이 infrastructure를 알게 된다.
- **Spring `@Transactional`(리액티브 트랜잭션 관리자)**: Exposed R2DBC는 자체 트랜잭션으로 연결을 관리한다. 두 트랜잭션을 연결하려면 별도 통합이 필요하고, 섞어 쓰면 어느 트랜잭션에서 실행되는지 불명확해진다.
- **Repository마다 자체 트랜잭션**: 여러 애그리거트를 한 트랜잭션에서 바꿔야 하는 유스케이스(옵션 목록 교체, ADR-0008)를 표현할 수 없다.

## 결과

- **얻는 것**: application은 Exposed를 모르고, 트랜잭션 경계는 application 코드에서 보인다. 테스트에서는 가짜 구현으로 바꿀 수 있다.
- **감수하는 것**: 선언형(어노테이션) 대신 블록으로 감싸는 명시적 코드가 필요하다.
- **후속 작업**: 잠금 조회(`FOR UPDATE`) API와 도메인 이벤트 발행 시점은 Repository·application 작업 때 정한다.
