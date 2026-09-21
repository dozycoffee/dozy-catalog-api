# 상품 API (본사관리자)

> 공통 규약(경로, 인가, 형식, 목록 조회, 낙관적 잠금, 오류 응답)은 [API 명세](README.md)를 따른다.

경로 앞에 `/api/v1/admin`이 붙고, 모두 본사관리자 전용이다. 가맹점주가 쓰는 상품·카테고리·태그 조회는 [가맹점주 API](store.md#판매-상품-조회)에 있다.

예약 변경은 [예약 API](schedule.md)에 있다.

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | If-Match | 근거 |
|---|---|---|---|---|
| POST | `/products` | 상품 등록 | | 1.2 |
| GET | `/products` | 상품 목록·검색 | | 1.12 |
| GET | `/products/{productId}` | 상품 조회 | | 1.4 |
| PUT | `/products/{productId}` | 상품 정보 즉시 교체 | 필요 | 1.3, 1.4 |
| DELETE | `/products/{productId}` | 상품 삭제 (`DRAFT`만) | | 1.11 |
| POST | `/products/{productId}/activate` | 활성화 | | 1.3 |
| POST | `/products/{productId}/discontinue` | 단종 | | 1.3 |
| PUT | `/products/{productId}/store-scope` | 판매 범위 변경 | 필요 | 1.5 |
| POST | `/products/{productId}/option-groups` | 옵션 그룹 연결 | 필요 | 1.9 |
| DELETE | `/products/{productId}/option-groups/{optionGroupId}` | 옵션 그룹 연결 해제 | 필요 | 1.9 |
| PUT | `/products/{productId}/option-groups/order` | 옵션 그룹 노출 순서 변경 | 필요 | 1.9 |
| PUT | `/products/{productId}/option-groups/{optionGroupId}/overrides/{optionKey}` | 옵션 예외 지정 (가격·제외) | 필요 | 1.9 |
| DELETE | `/products/{productId}/option-groups/{optionGroupId}/overrides/{optionKey}` | 옵션 예외 해제 | 필요 | 1.9 |
| GET | `/products/{productId}/effective-options` | 유효 옵션 구성과 표시용 시작가 | | 1.9 |
| POST | `/option-groups` | 옵션 그룹 생성 | | 1.9 |
| GET | `/option-groups` | 옵션 그룹 목록 | | 1.9 |
| GET | `/option-groups/{optionGroupId}` | 옵션 그룹 조회 | | 1.9 |
| PUT | `/option-groups/{optionGroupId}` | 이름·선택 방식·필수 여부 변경 | 필요 | 1.9 |
| PUT | `/option-groups/{optionGroupId}/options` | 옵션 목록 즉시 교체 | 필요 | 1.9 |
| DELETE | `/option-groups/{optionGroupId}` | 옵션 그룹 삭제 | | 1.9 |
| GET | `/categories` | 카테고리 목록 | | 1.6 |
| POST | `/categories` | 카테고리 등록 (대분류·소분류) | | 1.6 |
| PUT | `/categories/{categoryId}` | 카테고리 이름 변경 | | 1.6 |
| PUT | `/categories/{categoryId}/parent` | 부모 변경 (이동·강등·승격) | | 1.6 |
| DELETE | `/categories/{categoryId}` | 카테고리 삭제 | | 1.6 |
| GET | `/tags` | 태그 목록 | | 1.7 |
| PUT | `/tags/{tagId}` | 태그 이름 변경 | | 1.7 |
| DELETE | `/tags/{tagId}` | 태그 삭제 | | 1.7 |
| GET | `/product-groups` | 상품 그룹 목록 | | 1.8 |
| POST | `/product-groups` | 상품 그룹 등록 | | 1.8 |
| PUT | `/product-groups/{productGroupId}` | 상품 그룹 이름 변경 | | 1.8 |
| DELETE | `/product-groups/{productGroupId}` | 상품 그룹 삭제 | | 1.8 |

- 태그는 등록 엔드포인트가 없다. 상품 등록·수정에서 이름으로 입력하면 생긴다(요구사항 1.7).
- 근거가 1.12인 목록 조회와 1.6~1.9의 목록 조회는 요구사항 보완 후 확정한다.

---

## 상품

### 상품 (`Product`)

상품 조회·목록과 모든 상품 변경 요청이 이 형식을 돌려준다.

```json
{
  "id": 12,
  "sku": "DZ-00000012",
  "name": "아이스 아메리카노",
  "status": "ACTIVE",
  "categoryId": 5,
  "description": "산미가 적은 원두",
  "imageUrl": "https://cdn.example.com/p/12.png",
  "basePrice": 4500,
  "tracksInventory": false,
  "tagIds": [3],
  "groupIds": [2],
  "storeScope": { "kind": "LIMITED", "targetStoreIds": [101, 102] },
  "optionGroups": [
    {
      "optionGroupId": 7,
      "overrides": [
        { "optionKey": "LARGE", "type": "PRICE", "price": 700 },
        { "optionKey": "XLARGE", "type": "EXCLUDE", "price": null }
      ]
    }
  ],
  "version": 4
}
```

| 필드 | 설명 |
|---|---|
| `sku` | 시스템이 부여한 외부 연동 키([ADR-0016](../adr/0016-system-generated-sku.md)). 과거 데이터는 `null`일 수 있다 |
| `status` | `DRAFT` / `ACTIVE` / `DISCONTINUED` |
| `categoryId` | 상품이 참조하는 소분류 |
| `tagIds`, `groupIds` | 태그 ID, 상품 그룹 ID |
| `storeScope` | `{"kind": "ALL", "targetStoreIds": []}` 또는 `{"kind": "LIMITED", "targetStoreIds": [...]}` |
| `optionGroups` | 연결된 옵션 그룹. 배열 순서가 이 상품에서의 노출 순서다. `overrides`는 이 상품의 예외이며, `EXCLUDE`면 `price`가 `null`이다. 옵션 그룹의 내용은 `/option-groups`에서, 예외를 반영한 결과는 `/effective-options`에서 받는다 |
| `version` | 낙관적 잠금 버전 |

대기 중인 예약은 담지 않는다. 클라이언트가 [예약 API](schedule.md)에서 받아 합친다.

### POST `/products` — 상품 등록 (요구사항 1.2)

```json
{
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "basePrice": 4500,
  "tracksInventory": false,
  "description": null,
  "imageUrl": null,
  "tagNames": ["신메뉴", "베스트"],
  "groupIds": [2],
  "optionGroupIds": [7, 8]
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `name` | O | |
| `categoryId` | O | 소분류 ID |
| `basePrice` | O | 0 이상 |
| `tracksInventory` | O | 재고 추적 여부. 등록 후 바꿀 수 없다 |
| `description`, `imageUrl` | | |
| `tagNames` | | 태그 이름. 같은 이름의 태그가 있으면 재사용하고 없으면 새로 만든다(요구사항 1.7) |
| `groupIds` | | 상품 그룹 ID |
| `optionGroupIds` | | 연결할 옵션 그룹 ID. 배열 순서가 노출 순서다 |

- 응답: `201 Created`, `Location: /api/v1/admin/products/{id}`, `Product`. 상태는 항상 `DRAFT`이고 SKU가 부여된다.
- 오류

| 코드 | 상태 | 상황 |
|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | 카테고리가 없음 |
| `CATEGORY_NOT_ASSIGNABLE` | 400 | 대분류를 지정함 |
| `PRODUCT_GROUP_NOT_FOUND` | 404 | 없는 상품 그룹 |
| `OPTION_GROUP_NOT_FOUND` | 404 | 없는 옵션 그룹 |
| `DUPLICATE_OPTION_GROUP_LINK` | 409 | 같은 옵션 그룹을 두 번 지정함 |
| `INVALID_MONEY_AMOUNT` | 400 | 기준가가 음수 |

### GET `/products` — 상품 목록·검색 (요구사항 1.12)

| 파라미터 | 설명 |
|---|---|
| `ids` | [목록 조회](README.md#목록-조회) |
| `keyword` | 상품명 부분 일치 또는 SKU 완전 일치 |
| `categoryId` | 소분류 또는 대분류. 대분류면 그 아래 소분류의 상품을 모두 포함(요구사항 1.10과 같은 규칙) |
| `tagId` | |
| `groupId` | |
| `status` | `DRAFT` / `ACTIVE` / `DISCONTINUED` |
| `page`, `size` | [페이징](README.md#페이징) |

- 응답: `Product`의 페이지. 최근 등록한 상품이 먼저 온다.

### GET `/products/{productId}` — 상품 조회

- 응답: `Product`, 헤더 `ETag`
- 오류: `PRODUCT_NOT_FOUND`(404)

### PUT `/products/{productId}` — 상품 정보 즉시 교체 (요구사항 1.3, 1.4)

입력한 값 전체로 교체한다. `DISCONTINUED` 상품도 바꿀 수 있다. 상태, 재고 추적 여부, 판매 범위, 옵션 그룹 연결·예외는 이 요청으로 바꾸지 않는다(각 전용 엔드포인트).

- 헤더: `If-Match`
- 본문: 등록 본문에서 `tracksInventory`, `optionGroupIds`를 뺀 것

```json
{
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "basePrice": 4800,
  "description": null,
  "imageUrl": null,
  "tagNames": ["베스트"],
  "groupIds": []
}
```

- 응답: `Product`
- 오류: `PRODUCT_NOT_FOUND`(404), `CATEGORY_NOT_FOUND`(404), `CATEGORY_NOT_ASSIGNABLE`(400), `PRODUCT_GROUP_NOT_FOUND`(404), `INVALID_MONEY_AMOUNT`(400), `VERSION_CONFLICT`(409)

### DELETE `/products/{productId}` — 상품 삭제 (요구사항 1.11)

- 응답: `204 No Content`. 옵션 그룹 연결·예외와 매장 설정이 함께 삭제되며 복구할 수 없다.
- 오류: `PRODUCT_NOT_FOUND`(404), `PRODUCT_NOT_DELETABLE`(409, `DRAFT`가 아님)

### POST `/products/{productId}/activate`, `/discontinue` — 상태 전환 (요구사항 1.3)

본문 없음. 버전을 받지 않는다(상태 전이는 서버가 행 잠금으로 막는다). 예약 전환은 [예약 API](schedule.md)를 쓴다.

- 응답: `Product`
- 오류: `PRODUCT_NOT_FOUND`(404), `INVALID_PRODUCT_STATUS_TRANSITION`(409)
  - 활성화: 이미 `ACTIVE`
  - 단종: `ACTIVE`가 아님 (`DRAFT` 단종 불가, 이미 `DISCONTINUED`)

### PUT `/products/{productId}/store-scope` — 판매 범위 변경 (요구사항 1.5)

- 헤더: `If-Match`

```json
{ "kind": "LIMITED", "targetStoreIds": [101, 102] }
```

`kind`가 `ALL`이면 `targetStoreIds`는 비워야 한다. `LIMITED`에 빈 목록도 허용한다(어떤 매장에도 노출되지 않음). 대상에서 빠진 매장의 개별 설정과 수동 품절은 같은 요청 안에서 삭제된다.

- 응답: `Product`
- 오류: `PRODUCT_NOT_FOUND`(404), `TARGET_STORE_NOT_FOUND`(404, 존재하지 않는 매장), `VERSION_CONFLICT`(409)

### 옵션 그룹 연결 (요구사항 1.9)

모두 `If-Match`가 필요하고 `Product`를 돌려준다.

**POST `/products/{productId}/option-groups`** — 연결. 기존 연결들 뒤에 붙는다.

```json
{ "optionGroupId": 9 }
```

오류: `PRODUCT_NOT_FOUND`(404), `OPTION_GROUP_NOT_FOUND`(404), `DUPLICATE_OPTION_GROUP_LINK`(409), `VERSION_CONFLICT`(409)

**DELETE `/products/{productId}/option-groups/{optionGroupId}`** — 연결 해제. 그 연결의 예외도 함께 사라지고, 다시 연결해도 복원되지 않는다.

오류: `PRODUCT_NOT_FOUND`(404), `PRODUCT_OPTION_GROUP_NOT_LINKED`(404), `VERSION_CONFLICT`(409)

**PUT `/products/{productId}/option-groups/order`** — 노출 순서 변경. 연결된 옵션 그룹 전체를 정확히 한 번씩 담아야 한다.

```json
{ "optionGroupIds": [8, 7, 9] }
```

오류: `PRODUCT_NOT_FOUND`(404), `INVALID_OPTION_GROUP_ORDER`(400, 빠지거나 중복됨), `PRODUCT_OPTION_GROUP_NOT_LINKED`(404, 연결되지 않은 그룹), `VERSION_CONFLICT`(409)

### 옵션 예외 (요구사항 1.9)

모두 `If-Match`가 필요하고 `Product`를 돌려준다.

**PUT `/products/{productId}/option-groups/{optionGroupId}/overrides/{optionKey}`** — 가격 예외 또는 제외를 지정한다. 같은 옵션 키에 기존 예외가 있으면 대체한다.

```json
{ "type": "PRICE", "price": 700 }
```
```json
{ "type": "EXCLUDE" }
```

| 코드 | 상태 | 상황 |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | |
| `OPTION_GROUP_NOT_FOUND` | 404 | |
| `PRODUCT_OPTION_GROUP_NOT_LINKED` | 404 | 상품에 연결되지 않은 옵션 그룹 |
| `OPTION_KEY_NOT_FOUND` | 404 | 옵션 그룹에 없는 옵션 키 |
| `NO_SELECTABLE_OPTION` | 422 | 제외하면 이 상품의 선택 가능한 옵션이 0개 |
| `INVALID_MONEY_AMOUNT` | 400 | 음수 가격 |
| `VERSION_CONFLICT` | 409 | |

**DELETE `/products/{productId}/option-groups/{optionGroupId}/overrides/{optionKey}`** — 예외 해제. 그 옵션은 다시 옵션 그룹의 구성과 가격을 따른다.

오류: `PRODUCT_NOT_FOUND`(404), `PRODUCT_OPTION_GROUP_NOT_LINKED`(404), `VERSION_CONFLICT`(409)

### GET `/products/{productId}/effective-options` — 유효 옵션 구성 (요구사항 1.9)

연결된 옵션 그룹에 이 상품의 예외를 반영한 결과와 표시용 시작가다. 업무 규칙이 들어간 계산이라 백엔드가 제공한다. 조합 금액 계산과 선택 검증은 주문·POS의 책임이다([ADR-0006](../adr/0006-catalog-pricing-boundary.md)).

```json
{
  "productId": 12,
  "basePrice": 4500,
  "displayStartingPrice": 4500,
  "groups": [
    {
      "optionGroupId": 7,
      "name": "사이즈",
      "selectionType": "SINGLE",
      "required": true,
      "autoSelectedOptionKey": null,
      "options": [
        { "optionKey": "REGULAR", "name": "레귤러", "price": 0, "priceOverridden": false },
        { "optionKey": "LARGE", "name": "라지", "price": 700, "priceOverridden": true }
      ]
    }
  ]
}
```

- `groups`는 상품의 연결 순서, `options`는 옵션 그룹의 순서를 따르고 제외된 옵션은 빠진다.
- `autoSelectedOptionKey`: 필수 그룹에 유효 옵션이 1개면 그 옵션 키, 아니면 `null`
- `displayStartingPrice`: 기준가 + 필수 그룹마다 유효 옵션 중 최저가
- 오류: `PRODUCT_NOT_FOUND`(404)

---

## 옵션 그룹 (요구사항 1.9)

### 옵션 그룹 (`OptionGroup`)

```json
{
  "id": 7,
  "name": "사이즈",
  "selectionType": "SINGLE",
  "required": true,
  "options": [
    { "optionKey": "REGULAR", "name": "레귤러", "price": 0 },
    { "optionKey": "LARGE", "name": "라지", "price": 500 }
  ],
  "version": 2
}
```

`options`의 배열 순서가 노출 순서다. `selectionType`은 `SINGLE` / `MULTI`다. `optionKey`는 옵션 목록을 교체해도 유지되는 논리 식별자이고, 상품의 예외가 이 키로 옵션을 가리킨다.

### POST `/option-groups` — 생성

```json
{
  "name": "사이즈",
  "selectionType": "SINGLE",
  "required": true,
  "options": [
    { "optionKey": "REGULAR", "name": "레귤러", "price": 0 },
    { "optionKey": "LARGE", "name": "라지", "price": 500 }
  ]
}
```

- 응답: `201 Created`, `OptionGroup`
- 오류: `EMPTY_OPTION_GROUP`(422, 옵션 0개), `DUPLICATE_OPTION_KEY`(400), `INVALID_MONEY_AMOUNT`(400)

### GET `/option-groups` — 목록

| 파라미터 | 설명 |
|---|---|
| `ids` | |
| `keyword` | 이름 부분 일치 |

- 응답: `OptionGroup` 배열, 등록 순. 페이징하지 않는다.

### GET `/option-groups/{optionGroupId}` — 조회

- 응답: `OptionGroup`, 헤더 `ETag`
- 오류: `OPTION_GROUP_NOT_FOUND`(404)

### PUT `/option-groups/{optionGroupId}` — 이름·선택 방식·필수 여부 변경

즉시 반영만 한다(예약 불가). 옵션 목록은 아래 엔드포인트로 바꾼다.

- 헤더: `If-Match`

```json
{ "name": "사이즈", "selectionType": "SINGLE", "required": true }
```

- 응답: `OptionGroup`
- 오류: `OPTION_GROUP_NOT_FOUND`(404), `VERSION_CONFLICT`(409)

### PUT `/option-groups/{optionGroupId}/options` — 옵션 목록 즉시 교체 (시나리오 S5)

입력한 목록 전체로 교체한다. 목록에서 사라진 옵션 키의 상품별 예외는 모든 연결 상품에서 함께 삭제된다.

- 헤더: `If-Match`

```json
{
  "options": [
    { "optionKey": "REGULAR", "name": "레귤러", "price": 0 },
    { "optionKey": "LARGE", "name": "라지", "price": 700 }
  ]
}
```

- 응답: `OptionGroup`
- 오류: `OPTION_GROUP_NOT_FOUND`(404), `EMPTY_OPTION_GROUP`(422), `DUPLICATE_OPTION_KEY`(400), `INVALID_MONEY_AMOUNT`(400), `NO_SELECTABLE_OPTION`(422, 어떤 연결 상품의 선택 가능한 옵션이 0개가 됨), `VERSION_CONFLICT`(409)

### DELETE `/option-groups/{optionGroupId}` — 삭제

- 응답: `204 No Content`
- 오류: `OPTION_GROUP_NOT_FOUND`(404), `OPTION_GROUP_STILL_REFERENCED`(409, 연결한 상품이 있음)

---

## 카테고리 (요구사항 1.6)

### 카테고리 (`Category`)

```json
{ "id": 5, "name": "커피", "parentId": 1 }
```

`parentId`가 `null`이면 대분류, 값이 있으면 그 대분류의 소분류다.

### GET `/categories` — 목록

| 파라미터 | 설명 |
|---|---|
| `ids` | |
| `parentId` | 그 대분류의 소분류만 |
| `topLevel` | `true`면 대분류만 |

- 응답: `Category` 배열, 등록 순. 대분류·소분류를 평평한 목록으로 주고, 계층은 클라이언트가 `parentId`로 구성한다.

### POST `/categories` — 등록

```json
{ "name": "커피", "parentId": 1 }
```

- `parentId`가 `null`이면 대분류, 값이 있으면 그 대분류의 소분류로 만든다.
- 응답: `201 Created`, `Category`
- 오류: `TOP_LEVEL_CATEGORY_NOT_FOUND`(404, 부모가 없거나 소분류임)

### PUT `/categories/{categoryId}` — 이름 변경

```json
{ "name": "커피" }
```

- 응답: `Category`
- 오류: `CATEGORY_NOT_FOUND`(404)

### PUT `/categories/{categoryId}/parent` — 부모 변경

```json
{ "parentId": 2 }
```

| 대상 | `parentId` | 결과 |
|---|---|---|
| 소분류 | 다른 대분류 ID | 이동. 참조하는 상품에는 영향 없음 |
| 대분류 | 다른 대분류 ID | 강등(소분류가 됨) |
| 소분류 | `null` | 승격(대분류가 됨) |
| 대분류 | `null` | 변화 없음 |

- 응답: `Category`
- 오류

| 코드 | 상태 | 상황 |
|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | |
| `TOP_LEVEL_CATEGORY_NOT_FOUND` | 404 | 새 부모가 없거나 소분류임 |
| `INVALID_PARENT_CATEGORY` | 400 | 자기 자신을 부모로 지정 |
| `CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE` | 409 | 소분류를 가진 대분류를 강등 |
| `REFERENCED_CATEGORY_NOT_PROMOTABLE` | 409 | 상품이 참조 중인 소분류를 승격 |

### DELETE `/categories/{categoryId}` — 삭제

- 응답: `204 No Content`
- 오류: `CATEGORY_NOT_FOUND`(404), `CATEGORY_HAS_CHILDREN`(409), `CATEGORY_STILL_REFERENCED`(409)

---

## 태그 (요구사항 1.7)

태그(`Tag`)는 `{"id": 3, "name": "베스트"}`다. 상품 등록·수정에서 이름으로 입력하면 생기므로 등록 엔드포인트가 없다.

| 요청 | 본문 | 응답 | 오류 |
|---|---|---|---|
| GET `/tags` (`ids`, `keyword`) | | `Tag` 배열, 이름 순 | |
| PUT `/tags/{tagId}` | `{"name": "베스트"}` | `Tag` | `TAG_NOT_FOUND`(404), `TAG_NAME_DUPLICATED`(409, 다른 태그와 같은 이름) |
| DELETE `/tags/{tagId}` | | `204` | `TAG_NOT_FOUND`(404) |

삭제하면 참조하던 모든 상품에서 자동으로 빠진다.

## 상품 그룹 (요구사항 1.8)

상품 그룹(`ProductGroup`)은 `{"id": 2, "name": "여름 시즌"}`다. 본사 내부용이라 가맹점주 API에는 없다(요구사항 1.8).

| 요청 | 본문 | 응답 | 오류 |
|---|---|---|---|
| GET `/product-groups` (`ids`) | | `ProductGroup` 배열, 등록 순 | |
| POST `/product-groups` | `{"name": "여름 시즌"}` | `201`, `ProductGroup` | |
| PUT `/product-groups/{productGroupId}` | `{"name": "여름 시즌"}` | `ProductGroup` | `PRODUCT_GROUP_NOT_FOUND`(404) |
| DELETE `/product-groups/{productGroupId}` | | `204` | `PRODUCT_GROUP_NOT_FOUND`(404) |

삭제하면 참조하던 모든 상품에서 자동으로 빠진다.
