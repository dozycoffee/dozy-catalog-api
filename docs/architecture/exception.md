# 예외 구조

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다. 도입 배경은 #17.

도메인 규칙 위반은 `DomainException`을 상속한 애그리거트별 예외 클래스로 던진다. 각 예외는 `errorCode: ErrorCode`를 가지며, 코드 값은 애그리거트별 enum(`<Aggregate>ErrorCode`)으로 해당 애그리거트의 `exception/` 패키지에 둔다. 공통 커널용은 `domain/shared/SharedErrorCode`다.

```kotlin
// domain/shared
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

// domain/product/exception
enum class ProductErrorCode(override val type: ErrorType) : ErrorCode {
    PRODUCT_NOT_DELETABLE(ErrorType.CONFLICT),
    ...;
    override val code: String get() = name
}
```

## 규칙

- **domain은 HTTP를 모른다.** `ErrorCode`는 프레임워크와 무관한 `ErrorType`까지만 가진다. `ErrorType`을 `HttpStatus`로 바꾸는 일은 `presentation.GlobalExceptionHandler`에서 `else` 없는 `when` 한 곳에서만 한다. 예외가 늘어도 핸들러 분기는 늘지 않고, `ErrorType`을 추가하면 매핑 누락이 컴파일 에러로 드러난다.
- **예외 서브클래스는 유지한다.** 타입 있는 생성자 파라미터(`ProductId` 등)로 메시지를 만드는 책임을 지고, 테스트에서 `assertFailsWith<XxxException>`으로 구분하기 위해서다.
- **응답 본문** `ErrorResponse(code, message)`의 `code`는 `ErrorCode.code`(enum 이름) 문자열이다. 클라이언트 계약이므로 enum 이름을 바꿀 때 주의한다.
- **새** `<Aggregate>ErrorCode` **enum을 만들면** `ErrorCodeTest`의 목록에도 추가해야 code 문자열 유일성 검사 대상이 된다.

## ErrorType 분류 기준

| `ErrorType` | HTTP | 기준 |
|---|---|---|
| `INVALID_INPUT` | 400 | 요청 값 자체의 형식·범위 위반 (현재 상태와 무관) |
| `NOT_FOUND` | 404 | 대상이 존재하지 않음 |
| `CONFLICT` | 409 | 현재 리소스 상태와 충돌 (상태가 바뀌면 성공할 수 있음) |
| `BUSINESS_RULE_VIOLATION` | 422 | 값은 유효하지만 도메인 불변식 위반 |
