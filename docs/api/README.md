# API 명세

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를 참고한다.

Catalog가 외부에 제공하는 HTTP API의 계약을 다룬다. 규칙 자체는 [요구사항](../requirements.md)이 원본이며, 각 엔드포인트의 `(요구사항 N.N)`은 근거가 되는 절을 가리킨다. 이 문서에는 모든 API에 공통인 규약을 두고, 엔드포인트는 모듈별 문서에 둔다.

| 문서 | 호출자 | 다루는 리소스 |
|---|---|---|
| [상품](product.md) | 본사관리자 | 상품, 옵션 그룹 연결·예외, 유효 옵션 구성, 옵션 그룹, 카테고리, 태그, 상품 그룹 |
| [예약](schedule.md) | 본사관리자 | 상품·옵션 그룹의 필드 단위 예약 |
| [노출 현황](exposure.md) | 본사관리자 | 상품별 노출 매장 집계 |
| [매장](store.md) | Store 서비스 (일부 본사관리자) | 매장별 상품 상태(진열 순서, 숨김, 품절), 그 매장에서 판매하는 상품 조회 |

API는 클라이언트와의 계약이다. 필드 이름, 경로, 오류 코드를 바꾸는 변경은 하위 호환을 깨므로 새 버전 경로나 새 필드로 추가한다. 설계 원칙의 근거는 [ADR-0017](../adr/0017-resource-oriented-api-split-by-caller.md)에 있다.

## 설계 원칙

- **화면이 아니라 리소스 단위로 설계한다.** 한 화면에 필요한 정보는 클라이언트가 여러 API를 불러 조립한다. 백엔드는 특정 화면을 위한 조합 응답을 만들지 않는다.
- **리소스는 자기 데이터만 담고, 다른 리소스는 ID로만 가리킨다.** 상품 응답은 `categoryId`, `tagIds`를 담고 카테고리 이름·태그 이름은 담지 않는다. 도메인 모델의 "애그리거트끼리는 ID로만 참조"와 같은 원칙이다.
- **단, 상품에 대한 계산·상태 목록은 대상 상품을 알아볼 정보를 함께 담는다.** 이런 목록은 상품 정보 없이는 화면에 쓸 수 없어 매번 상품 조회를 한 번 더 부르게 되기 때문이다.
  - 노출 현황(`/admin/product-exposures`): `sku`, `name`, `status`까지. 나머지는 상품 API로 받는다.
  - 매장 상품(`/internal/stores/{storeId}/products`): 점주 화면이 그대로 쓸 수 있도록 상품 기준 정보(`sku`, `name`, `categoryId`, `imageUrl`, `basePrice`, `tracksInventory`)까지 담는다. 호출자가 Store 서비스 하나이고, 상품 조회를 한 번 더 부르면 매장마다 같은 조회가 반복되기 때문이다([ADR-0018](../adr/0018-partner-requests-through-store-service.md)).
- **조립할 수 있도록 목록 API는 `ids`로 여러 건을 한 번에 조회할 수 있게 한다.** 예: `GET /admin/products?ids=12,15,20`. 클라이언트가 건수만큼 호출하지 않게 하기 위해서다.
- **업무 규칙이 들어간 계산은 백엔드가 제공한다.** 매장별 노출 판단(요구사항 3장), 유효 옵션 구성과 표시용 시작가(1.9), 노출 매장 수(1.10)는 클라이언트가 재구현하지 않도록 계산 결과를 리소스로 제공한다. 클라이언트가 조립하는 것은 표시를 위한 결합(ID에 이름 붙이기 등)까지다.

## 경로와 인가

호출자는 둘이다. **본사 직원**과 **Store 서비스**다. 가맹점주는 Catalog를 직접 호출하지 않는다. 점주의 요청은 Store 서비스가 받아 매장 소유를 확인한 뒤 Catalog의 내부 API를 부른다([ADR-0018](../adr/0018-partner-requests-through-store-service.md)).

| 경로 | 호출자 | 토큰 | 담는 것 |
|---|---|---|---|
| `/api/v1/admin/**` | 본사 직원 | 직원 access token (realm `internal`) | 본사의 모든 API. 조회와 변경을 모두 담는다 |
| `/api/v1/internal/**` | Store 서비스 | system token (realm `internal`) | 매장별 상품 상태의 조회·변경과 그 매장에서 판매하는 상품 조회 |

- Catalog가 받는 realm은 `internal`뿐이고 audience는 `catalog`다. 점주 토큰(realm `partner`, `aud: ["store"]`)은 Catalog에 들어오지 않는다.
- **매장 소유 확인은 Store 서비스가 한다.** 매장과 점주의 관계(`store_member`)는 Store 서비스의 데이터이므로 Catalog는 경로의 `storeId`를 그대로 신뢰한다. Catalog는 그 매장이 그 상품을 취급할 수 있는지(판매 범위, 요구사항 2.2)만 확인한다.
- **`/internal/**`의 응답에는 본사 내부 정보를 담지 않는다.** 상품 그룹(요구사항 1.8), 판매 범위, 낙관적 잠금 버전이다. 점주와 손님에게 그대로 전달될 수 있기 때문이다.
- 같은 리소스를 두 경로에서 조회하면(예: `/admin/products`와 `/internal/stores/{storeId}/products`) 같은 조회 로직을 쓰고, 조회 범위와 응답 필드만 다르다.
- 경로를 호출자별로 나누므로 인가 규칙이 경로 단위로 끝나고, 게이트웨이나 네트워크에서 `/internal/**`를 따로 막을 수 있다.

### 역할 (role)

토큰의 `roles`는 `catalog:{code}` 형식이고, 스타터가 접두사를 떼어 `ROLE_{code}` 권한으로 바꾼다. 역할은 Auth에 등록한다.

| 역할 | 부여 대상 | 허용 범위 |
|---|---|---|
| `catalog:admin` | 본사 직원 | `/admin/**` 전체(조회·변경) |
| `catalog:store_agent` | Store 서비스의 system client | `/internal/**` 전체 |

- 경로마다 역할이 하나라 엔드포인트별로 따로 적지 않는다.
- **Store 서비스에 `catalog:admin`을 주지 않는다.** Store 서비스나 그 비밀값이 노출돼도 본사 API(상품 변경·삭제, 판매 범위 변경 등)까지 열리지 않게 하기 위해서다.
- 조회 전용 직원처럼 본사 권한을 나눌 필요가 생기면 역할을 추가하고 해당 엔드포인트의 인가 조건만 바꾼다. 토큰 계약과 API 형식은 바뀌지 않는다.

### 경로 규칙

- 리소스 이름은 복수형 kebab-case(`option-groups`), 식별자는 경로 변수로 둔다.
- 필드 교체로 표현하기 어려운 행위는 `POST /{리소스}/{id}/{동사}`로 둔다(예: `POST /admin/products/{id}/activate`).
- 토큰 검증과 401·403 응답은 `dozy-auth`의 스타터가 맡는다. Catalog는 `dozy.auth.audience=catalog`, `accepted-realms=[internal]`로 설정한다.

## 요청·응답 형식

- 본문은 JSON(`application/json`), 필드 이름은 camelCase다.
- **ID**: 숫자(`long`). 옵션 키(`optionKey`)만 문자열이다.
- **금액**: 원 단위 정수. 0 이상이다.
- **날짜**: ISO-8601 날짜(`2026-10-01`). 업무 시간대(한국 시간) 기준이다.
- **시각**: ISO-8601 UTC(`2026-10-01T00:00:00Z`).
- **상태값**: 대문자 문자열(`DRAFT`, `ACTIVE` 등). 도메인 모델의 enum 이름과 같다.
- 값이 없는 선택 필드는 `null`로 보낸다. 응답에서도 필드를 빼지 않고 `null`로 준다.
- 목록 필드가 비었으면 `[]`다. `null`로 주지 않는다.

### 상태 코드

| 상황 | 상태 코드 | 본문 |
|---|---|---|
| 생성 | `201 Created` + `Location` 헤더 | 생성된 리소스 |
| 조회·변경·상태 전환 | `200 OK` | 변경 후 리소스 |
| 삭제, 예약 취소 | `204 No Content` | 없음 |

변경 요청은 변경 후 리소스 전체를 돌려준다. 클라이언트가 다시 조회하지 않고 새 `version`을 받을 수 있게 하기 위해서다.

## 목록 조회

- `ids`: 쉼표로 구분한 ID 목록. 주면 그 ID의 리소스만 돌려준다. 없는 ID는 오류 없이 빠진다. 최대 100개다.
- 다른 필터와 함께 주면 모두 만족하는 것만 남는다.
- 건수가 계속 늘어나는 목록(상품, 노출 현황)만 페이징한다. 나머지는 한 번에 준다.

### 페이징

- 요청: `page`(0부터, 기본 0), `size`(기본 20, 최대 100)
- 응답:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7
}
```

## 낙관적 잠금

상품과 옵션 그룹은 관리자가 화면에서 보던 버전을 함께 보내야 하는 변경이 있다([ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)).

- 응답의 `version` 필드가 현재 버전이다. 단건 응답은 헤더 `ETag`에도 `"3"`처럼 같은 값을 담는다.
- 버전이 필요한 요청은 `If-Match: "3"` 헤더로 보낸다. 본문이 없는 `DELETE`에도 같은 방식을 쓰기 위해 본문 필드가 아니라 헤더로 받는다.
- 헤더가 없으면 `428 Precondition Required`(`VERSION_REQUIRED`)로 거부한다.
- 그 사이 다른 변경이 반영됐으면 `409 Conflict`(`VERSION_CONFLICT`)로 거부하고, 알 수 있으면 현재 버전을 `currentVersion`에 담는다. 서버는 자동으로 재시도하지 않는다. 클라이언트가 최신 값을 다시 불러와 관리자가 다시 판단한다.
- 버전이 필요한 엔드포인트는 모듈별 문서의 표에 `If-Match` 열로 표시한다. 상태 전환(활성화·단종)과 예약 등록은 버전을 받지 않는다.

## 오류 응답

Dozy 서비스 공통 형식인 RFC 9457 Problem Details를 쓴다. `Content-Type`은 `application/problem+json`이다. `dozy-auth` 스타터가 401·403을 이 형식으로 응답하므로 Catalog의 오류도 같은 형식으로 맞춘다.

```json
{
  "type": "https://docs.dozycoffee.com/errors/product-not-deletable",
  "title": "Product not deletable",
  "status": 409,
  "detail": "Draft 상태의 상품만 삭제할 수 있습니다: 상품 12",
  "instance": "/api/v1/admin/products/12",
  "code": "PRODUCT_NOT_DELETABLE",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `type` | ✅ | `https://docs.dozycoffee.com/errors/{code를 kebab-case로}` |
| `title` | ✅ | 오류 종류의 짧은 영어 이름 |
| `status` | ✅ | HTTP 상태 코드 |
| `detail` | | 사람이 읽는 설명. 클라이언트가 그대로 보여주지 않는다 |
| `instance` | ✅ | 요청 경로 |
| `code` | ✅ | 클라이언트가 분기에 쓰는 값. 도메인 예외는 `ErrorCode.code`(enum 이름)다 |
| `traceId` | ✅ | 응답 헤더 `X-Trace-Id`와 같은 값 |

- 클라이언트는 `code`로만 분기하고 문구는 클라이언트가 정한다.
- `VERSION_CONFLICT`만 `currentVersion`(숫자 또는 `null`)을 확장 필드로 담는다.
- `500`은 내부 정보(스택, SQL, 클래스 이름)를 담지 않는다.
- 도메인 규칙 위반의 상태 코드는 `ErrorType`으로 정해진다([예외 구조](../architecture/exception.md#errortype-분류-기준)).

| `ErrorType` | 상태 코드 |
|---|---|
| `INVALID_INPUT` | 400 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `BUSINESS_RULE_VIOLATION` | 422 |

도메인 예외가 아닌 오류는 다음 코드로 응답한다. 401·403은 스타터가 돌려준다.

| 코드 | 상태 코드 | 상황 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 본문을 읽을 수 없음, 필수 필드 누락, 타입 불일치, 허용되지 않는 enum 값, `ids`·`size` 상한 초과, 형식이 틀린 `If-Match`. `detail`에 문제가 된 필드나 파라미터 이름을 담는다 |
| `VERSION_REQUIRED` | 428 | 버전이 필요한 요청에 `If-Match`가 없음 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·검증 실패 (스타터) |
| `FORBIDDEN` | 403 | 역할이나 경로 인가 조건 불만족 (스타터) |
| `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` 등 | 404, 405, 415 등 | 없는 경로, 허용하지 않는 메서드, 지원하지 않는 `Content-Type` 같은 HTTP 수준의 오류. `code`는 HTTP 상태 이름이다. 대상 리소스가 없는 경우는 도메인 오류(`PRODUCT_NOT_FOUND` 등)로 따로 응답한다 |
| `INTERNAL_ERROR` | 500 | 그 밖의 서버 오류. 서버 로그에 error로 남는다 |

모듈별 문서의 각 엔드포인트에는 그 요청이 받을 수 있는 도메인 오류 코드를 적는다. `INVALID_REQUEST`, `INTERNAL_ERROR`, 인증·인가 오류는 모든 엔드포인트에 공통이라 따로 적지 않는다.
