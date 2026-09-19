# ADR-0002: 도메인 예외는 ErrorCode/ErrorType으로 분류하고 domain은 HTTP를 모른다

- **상태**: 채택
- **날짜**: 2026-09-18 (소급 작성)
- **관련**: 이슈 #17, PR #24, [예외 구조](../architecture/exception.md)

## 맥락

`DomainException`은 `code: String` 하나로 예외를 구분했고, `GlobalExceptionHandler`는 모든 도메인 예외를 400으로 응답했다. 예외 종류가 계속 늘면서 예외별로 다른 HTTP 상태를 내려야 했다. 그렇다고 핸들러에 예외별 분기를 쌓을 수는 없었다. 또 도메인 예외가 HTTP 상태를 직접 알면 "domain은 프레임워크를 모른다"는 의존 방향(ADR-0001)이 깨진다.

## 결정

- `DomainException`은 `errorCode: ErrorCode`를 가진다. 코드 값은 애그리거트별 enum(`<Aggregate>ErrorCode`)으로 각 애그리거트의 `exception/` 패키지에 둔다.
- `ErrorCode`는 HTTP와 무관한 분류 `ErrorType`(`INVALID_INPUT`, `NOT_FOUND`, `CONFLICT`, `BUSINESS_RULE_VIOLATION`)까지만 가진다.
- `ErrorType`을 HTTP 상태(400/404/409/422)로 바꾸는 일은 presentation의 `else` 없는 `when` 한 곳에서만 한다.
- 예외 서브클래스는 유지한다. 타입이 있는 생성자 인자로 메시지를 만들고, 테스트에서 예외를 타입으로 구분한다.

## 검토한 대안

- **`ErrorCode`가 `HttpStatus`를 직접 가짐**: 분기는 없어지지만 domain이 HTTP를 알게 된다.
- **모든 `ErrorCode`를 한 enum에 모음**: 목록을 한눈에 볼 수 있지만 애그리거트별 `exception/` 구조와 맞지 않는다. 코드가 겹치는 문제는 `ErrorCodeTest`의 유일성 검사로 막는다.
- **예외 서브클래스를 없애고 `DomainException(code, message)`만 사용**: 메시지를 만드는 코드가 호출하는 곳마다 흩어진다.

## 결과

- **얻는 것**: 예외가 늘어도 핸들러의 분기는 늘지 않는다. `ErrorType`을 추가하면 매핑 누락이 컴파일 에러로 드러난다.
- **감수하는 것**: 응답 본문의 `code`가 enum 이름이라 클라이언트 계약이 된다. 이름을 바꿀 때 주의해야 한다.
- **후속 작업**: 새 enum을 만들면 `ErrorCodeTest` 목록에도 추가한다. 분류 기준은 [예외 구조](../architecture/exception.md)에 있다.
