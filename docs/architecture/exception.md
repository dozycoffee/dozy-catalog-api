# 예외 구조

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다. 결정 근거는 [ADR-0002](../adr/0002-error-code-and-error-type.md)(예외 구조)와 [ADR-0007](../adr/0007-domain-exception-vs-require-check.md)(`DomainException`과 `require`/`check`의 구분)에 있다.

도메인 규칙 위반은 `DomainException`을 상속한 애그리거트별 예외 클래스로 던진다. 각 예외는 `errorCode: ErrorCode`를 가지며, 코드 값은 애그리거트별 enum(`<Aggregate>ErrorCode`)으로 해당 애그리거트의 `exception/` 패키지에 둔다. 공통 커널용은 `core/SharedErrorCode`다.

```kotlin
// core
enum class ErrorType { INVALID_INPUT, NOT_FOUND, CONFLICT, BUSINESS_RULE_VIOLATION }

interface ErrorCode {
    val code: String
    val type: ErrorType
}

abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

// product/domain/product/exception
enum class ProductErrorCode(override val type: ErrorType) : ErrorCode {
    PRODUCT_NOT_DELETABLE(ErrorType.CONFLICT),
    ...;
    override val code: String get() = name
}
```

## 규칙

- **domain은 HTTP를 모른다.** `ErrorCode`는 프레임워크와 무관한 `ErrorType`까지만 가진다. `ErrorType`을 `HttpStatus`로 바꾸는 일은 `common.web.GlobalExceptionHandler`에서 `else` 없는 `when` 한 곳에서만 한다. 예외가 늘어도 핸들러 분기는 늘지 않고, `ErrorType`을 추가하면 매핑 누락이 컴파일 에러로 드러난다.
- **예외 서브클래스는 유지한다.** 타입 있는 생성자 파라미터(`ProductId` 등)로 메시지를 만드는 책임을 지고, 테스트에서 `assertFailsWith<XxxException>`으로 구분하기 위해서다.
- **응답 본문** `ErrorResponse(code, message)`의 `code`는 `ErrorCode.code`(enum 이름) 문자열이다. 클라이언트 계약이므로 enum 이름을 바꿀 때 주의한다.
- **새** `<Aggregate>ErrorCode` **enum을 만들면** `ErrorCodeTest`의 목록에도 추가해야 code 문자열 유일성 검사 대상이 된다.

## DomainException과 require/check의 구분

| 상황 | 쓰는 것 | 결과 |
|---|---|---|
| 사용자 요청으로 생길 수 있는 규칙 위반 (예: 참조 중인 카테고리 삭제, 이미 Active인 상품 활성화) | `DomainException` 서브클래스 | `ErrorType`에 따라 4xx와 `ErrorResponse`로 응답한다. 클라이언트가 이유를 알 수 있어야 한다 |
| 사용자가 일으킬 수 없고 호출하는 코드가 잘못됐을 때만 생기는 상황 (예: application 정책에 다른 상품의 데이터가 섞여 들어옴) | Kotlin 표준 함수 `require`(인자 검증, `IllegalArgumentException`) / `check`(상태 검증, `IllegalStateException`) | 도메인 예외로 처리하지 않고 500 `INTERNAL_ERROR`로 응답하며 error 로그로 남긴다([API 명세](../api/README.md#오류-응답)). 클라이언트 잘못처럼 4xx로 보고하지 않고 서버 버그로 드러나게 한다 |

- 사용자 요청으로 생길 수 있는지 애매하면 `DomainException`을 쓴다.
- HTTP가 아닌 경로(이벤트 핸들러, 예약 배치)에서는 어느 쪽이든 500으로 바뀌지 않고 호출자에게 그대로 올라간다. 이 경로의 예외 격리 방식은 [미정 사항](README.md#미정-사항)을 따른다.

## ErrorType 분류 기준

| `ErrorType` | HTTP | 기준 |
|---|---|---|
| `INVALID_INPUT` | 400 | 요청 값 자체의 형식·범위 위반 (현재 상태와 무관) |
| `NOT_FOUND` | 404 | 대상이 존재하지 않음 |
| `CONFLICT` | 409 | 현재 리소스 상태와 충돌 (상태가 바뀌면 성공할 수 있음) |
| `BUSINESS_RULE_VIOLATION` | 422 | 값은 유효하지만 도메인 불변식 위반 |
