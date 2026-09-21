# 예약 API (본사관리자)

> 공통 규약은 [API 명세](README.md)를 따른다. 경로 앞에 `/api/v1/admin`이 붙고, 모두 본사관리자 전용이다. 예약의 규칙은 [요구사항](../requirements.md) 1.4와 [시나리오](../scenarios.md) S2에 있다.

예약은 대상(상품, 옵션 그룹)의 하위 리소스이고, **필드 이름으로 가리킨다**. 같은 대상·같은 필드의 대기 예약은 최대 1건이라 필드 이름만으로 하나를 정할 수 있기 때문이다. 예약 ID는 API에 드러내지 않는다.

## 엔드포인트 목록

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/products/{productId}/scheduled-changes` | 상품의 대기 예약 목록 |
| PUT | `/products/{productId}/scheduled-changes/{field}` | 상품 필드 예약 등록 (대기 예약이 있으면 대체) |
| DELETE | `/products/{productId}/scheduled-changes/{field}` | 상품 필드 예약 취소 |
| GET | `/option-groups/{optionGroupId}/scheduled-changes` | 옵션 그룹의 대기 예약 목록 |
| PUT | `/option-groups/{optionGroupId}/scheduled-changes/options` | 옵션 목록 예약 등록 |
| DELETE | `/option-groups/{optionGroupId}/scheduled-changes/options` | 옵션 목록 예약 취소 |

- 예약 등록은 버전(`If-Match`)을 받지 않는다. 적용 시점의 값에 대해 규칙을 다시 검증하기 때문이다([ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)).
- 등록은 "이 필드의 대기 예약을 이 값으로 둔다"는 뜻이라 `PUT`이다. 같은 요청을 반복해도 대기 예약은 하나다. 기존 예약은 `CANCELLED`로 남고 새 예약이 만들어진다.
- 대상의 현재 값과 대기 예약을 함께 보여 주는 것은 클라이언트가 한다. 대상 조회(`GET /products/{id}`)와 대기 예약 목록을 따로 받아 합친다.

## 필드와 값

`{field}`는 경로에 쓰는 필드 이름이다. 옵션 그룹별 예외만 `option-overrides/{optionGroupId}`처럼 두 단계다.

| 대상 | `{field}` | 요청의 `value` | 설명 |
|---|---|---|---|
| 상품 | `name` | 문자열 | |
| 상품 | `category` | 소분류 ID | |
| 상품 | `description` | 문자열 또는 `null` | |
| 상품 | `image` | 문자열 또는 `null` | 이미지 URL |
| 상품 | `basePrice` | 정수 | 0 이상 |
| 상품 | `tags` | 태그 이름 배열 | 즉시 변경과 같이 이름으로 받는다. 없는 태그는 예약을 등록할 때 만들어진다 |
| 상품 | `groups` | 상품 그룹 ID 배열 | |
| 상품 | `storeScope` | `{"kind": "ALL" \| "LIMITED", "targetStoreIds": [...]}` | |
| 상품 | `activation` | 없음 (`null`) | 활성화 |
| 상품 | `discontinuation` | 없음 (`null`) | 단종 |
| 상품 | `optionGroupLinks` | 옵션 그룹 ID 배열 (순서 = 노출 순서) | 목록에서 빠진 연결은 예외와 함께 해제 |
| 상품 | `option-overrides/{optionGroupId}` | `[{"optionKey": "LARGE", "type": "PRICE", "price": 700}, {"optionKey": "XLARGE", "type": "EXCLUDE"}]` | 그 옵션 그룹에 대한 이 상품의 예외 전체 |
| 옵션 그룹 | `options` | `[{"optionKey": "REGULAR", "name": "레귤러", "price": 0}, ...]` (순서 = 노출 순서) | 옵션 목록 전체 스냅샷 |

재고 관리 여부, 옵션 그룹의 이름·선택 방식·필수 여부, 카테고리·태그·상품 그룹 이름은 예약할 수 없다. 목록에 없는 `{field}`는 `404`(`UNKNOWN_SCHEDULE_FIELD`)다.

## 예약 (`ScheduledChange`)

```json
{
  "field": "basePrice",
  "value": 4800,
  "effectiveDate": "2026-10-01",
  "status": "PENDING"
}
```

- `status`는 `PENDING` / `APPLIED` / `CANCELLED` / `FAILED`다. 등록 응답과 목록은 항상 `PENDING`이다.
- `value`의 형식은 위 표와 같다. 단 `tags`는 등록할 때 태그 ID로 바뀌어 저장되므로 응답에서는 태그 ID 배열이다.

## GET `…/scheduled-changes` — 대기 예약 목록 (요구사항 1.4)

```json
[
  { "field": "activation", "value": null, "effectiveDate": "2026-10-01", "status": "PENDING" },
  { "field": "discontinuation", "value": null, "effectiveDate": "2026-10-31", "status": "PENDING" },
  { "field": "option-overrides/7", "value": [ { "optionKey": "LARGE", "type": "PRICE", "price": 700 } ], "effectiveDate": "2026-10-01", "status": "PENDING" }
]
```

- 오류: `PRODUCT_NOT_FOUND` / `OPTION_GROUP_NOT_FOUND`(404)

## PUT `…/scheduled-changes/{field}` — 예약 등록 (요구사항 1.3, 1.4)

```json
{ "effectiveDate": "2026-10-01", "value": 4800 }
```

- `effectiveDate`의 00시(한국 시간)에 적용된다. 내일 이후만 허용한다.
- 같은 필드에 대기 예약이 있으면 그 예약은 `CANCELLED`가 되고 새 예약이 대기한다.
- 활성화와 단종은 서로 다른 필드라 함께 대기할 수 있다. 옵션 예외도 옵션 그룹마다 따로 대기한다.
- 응답: `200 OK`, `ScheduledChange`

### 검증 시점

예약은 등록할 때와 적용할 때(00시) 두 번 검증한다. 기준은 "적용 전에 다른 변경으로 바뀔 수 있는가"다.

| 시점 | 검증하는 것 | 이유 |
|---|---|---|
| 등록할 때 | 적용일, 값 자체(금액 0 이상, 옵션 1개 이상, 옵션 키 유일), 참조 대상의 존재(대상, 소분류인 카테고리, 상품 그룹, 옵션 그룹, 매장) | 언제 적용하든 틀린 값이라 바로 알리는 편이 낫다 |
| 적용할 때만 | 상태 전이(`DRAFT` 단종 등), 옵션 그룹 연결 여부, 옵션 키 존재, 선택 가능한 옵션 0개 | 적용 전에 다른 예약이나 즉시 변경으로 바뀔 수 있다. 예: 10/1 활성화, 10/31 단종을 함께 예약하면 단종 예약을 등록하는 시점에는 상품이 아직 `DRAFT`다 |
| 적용할 때 다시 | 참조 대상의 존재 | 그 사이 삭제될 수 있다 |

적용할 때 규칙을 위반하면 대상 값을 바꾸지 않고 예약을 `FAILED`로 기록한다. 실패한 예약을 조회하는 API는 없다(요구사항은 "기록"만 정함).

### 오류

| 코드 | 상태 | 상황 |
|---|---|---|
| `PRODUCT_NOT_FOUND` / `OPTION_GROUP_NOT_FOUND` | 404 | 대상이 없음 |
| `UNKNOWN_SCHEDULE_FIELD` | 404 | 예약할 수 없는 필드 |
| `INVALID_EFFECTIVE_DATE` | 400 | 오늘이거나 지난 날짜 |
| `CATEGORY_NOT_FOUND` | 404 | `category`: 없는 카테고리 |
| `CATEGORY_NOT_ASSIGNABLE` | 400 | `category`: 대분류 |
| `PRODUCT_GROUP_NOT_FOUND` | 404 | `groups`: 없는 상품 그룹 |
| `OPTION_GROUP_NOT_FOUND` | 404 | `optionGroupLinks`, `option-overrides/{id}`: 없는 옵션 그룹 |
| `DUPLICATE_OPTION_GROUP_LINK` | 409 | `optionGroupLinks`: 같은 옵션 그룹이 두 번 |
| `TARGET_STORE_NOT_FOUND` | 404 | `storeScope`: 존재하지 않는 매장 |
| `INVALID_MONEY_AMOUNT` | 400 | `basePrice`, 옵션·예외 가격이 음수 |
| `EMPTY_OPTION_GROUP` | 422 | `options`: 옵션 0개 |
| `DUPLICATE_OPTION_KEY` | 400 | `options`: 옵션 키 중복 |

## DELETE `…/scheduled-changes/{field}` — 예약 취소 (요구사항 1.4)

- 응답: `204 No Content`. 예약은 `CANCELLED`로 남는다. `tags` 예약으로 만들어진 태그는 남는다.
- 오류

| 코드 | 상태 | 상황 |
|---|---|---|
| `NO_PENDING_SCHEDULE` | 404 | 이 필드에 대기 예약이 없음. 취소하는 사이 배치가 먼저 적용·실패 처리한 경우도 같다 |
| `SCHEDULE_ALREADY_PROCESSED` | 409 | 읽은 직후 배치가 먼저 처리함(드묾) |
