# 테스트

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

테스트를 어떻게 쓰는지 다룬다. 실행 방법과 빌드 설정 주의사항은 루트 [README](../../README.md#테스트)에, 테스트 도구를 고른 이유는 [기술 스택](tech-stack.md#테스트)에 있다.

## 계층별 전략

| 계층 | 도구 | 검증 대상 | 상태 |
|---|---|---|---|
| domain 단위 | JUnit 5 + `kotlin.test` | 애그리거트 불변식과 상태 전이, 도메인 서비스의 판단. 저장소·Spring 없이 객체만 만든다 | 정해짐 |
| persistence 통합 | Testcontainers(PostgreSQL) | Exposed Table 매핑, ENUM·부분 UNIQUE 등 DB 제약, 동시성 처리([ERD](../erd.md#동시성-처리)) | 해당 작업 때 정함 |
| application | 미정 (가짜 저장소 또는 Testcontainers) | 조회·잠금·저장 오케스트레이션, 도메인 이벤트 발행, 외부 포트 호출 | 미정 |
| API 슬라이스 | `spring-boot-starter-webflux-test`, `spring-boot-starter-security-test` | 요청·응답 형식, `ErrorType`별 HTTP 상태([예외 구조](exception.md)), 인가 | 해당 작업 때 정함 |

여러 애그리거트와 외부 시스템이 얽히는 흐름의 통합 테스트는 [시나리오](../scenarios.md)의 S1~S7을 기준으로 쓴다. 시나리오의 기본·대체·예외 흐름이 테스트 케이스가 된다.

## 작성 관례

- **이름**: 테스트 메서드 이름은 한국어 백틱 문장으로 쓴다. 규칙별로 `@Nested` 내부 클래스로 묶고 `@DisplayName`을 붙인다.
- **단언**: `kotlin.test`(`assertEquals`, `assertFailsWith`, `assertIs` 등)를 쓴다.
- **예외**: `assertFailsWith<XxxException>`으로 예외 타입을 구분한다. 분류가 중요한 경우에는 `errorCode`나 `errorCode.type`(`ErrorType`)까지 검증한다.
- **거부 시 상태 불변**: 요청이 거부되는 테스트는 예외뿐 아니라 애그리거트 상태(기존 값, 등록된 이벤트 등)가 그대로인지도 검증한다.
- **버그 수정 테스트**: 수정 전 코드에서 실패하는 것을 확인한 뒤 수정한다.
- **반복 케이스**: 입력만 다르고 모양이 같은 케이스는 `@ParameterizedTest`로 묶는다.

## 픽스처

테스트 객체 생성 헬퍼는 `src/test/kotlin/com/dozycoffee/catalog/fixture/`에 애그리거트별 파일(`ProductFixtures.kt`, `OptionGroupFixtures.kt` 등)로 둔다. 이름 있는 인자와 기본값을 가진 최상위 함수로 만들고 빌더 클래스는 만들지 않는다. 검증 대상을 호출하는 헬퍼(예: `resolve(...)`, `select(...)`)는 각 테스트 클래스에 둔다.

1. 기본값은 **유효한 최소 상태**다. 기본값만으로 만든 객체는 불변식을 통과해야 한다. 예: `product()`의 기본 상태는 등록 직후인 `DRAFT`.
2. **테스트 결과에 영향을 주는 값은 기본값과 같더라도 테스트에 명시한다.** 픽스처 기본값이 바뀌어도 테스트의 의미가 바뀌지 않게 하기 위해서다.
3. 픽스처는 객체를 만들기만 하고, 검증 대상이 되는 행위(예: `activate()`)는 하지 않는다. 기본값이 아닌 상태를 애그리거트 메서드로 만드는 것(예: 숨김 진열 설정을 `hide()`로 만듦)은 생성에 해당한다.
4. ID와 값은 고정값을 쓴다. 랜덤 데이터는 쓰지 않는다.
5. 이름 있는 시나리오 픽스처(예: "옵션 두 개짜리 사이즈 그룹")는 여러 테스트에서 3회 이상 반복되는 상황에만 만든다.

## 미정 사항

| 항목 | 내용 |
|---|---|
| application 테스트의 저장소 | 가짜(in-memory) 저장소를 쓸지 Testcontainers로 실제 DB를 쓸지. application 계층 구현 때 정한다 |
| 통합 테스트 데이터 정리 | R2DBC는 테스트 트랜잭션 롤백이 어려워 테스트 간 데이터 정리 방식(테이블 비우기, 테스트별 데이터 분리 등)을 정해야 한다 |
| 단위·통합 테스트 분리 | JUnit 태그 등으로 단위와 통합 테스트를 나눠 실행할지, CI에서 통합 테스트 시간을 어떻게 관리할지 |
