# 가맹점주 API

> 공통 규약은 [API 명세](README.md)를 따른다. 규칙은 [요구사항](../requirements.md) 2·3장과 [시나리오](../scenarios.md) S6, S7에 있다.

가맹점주가 쓰는 API다. 경로 앞에 `/api/v1`이 붙는다. 점주는 가격을 바꿀 수 없다(전 매장 동일가, 요구사항 2.1).

- **매장 상품**(`/stores/{storeId}/…`): 한 매장에서 각 상품의 상태(진열 순서, 숨김, 품절)다. 상품은 알아볼 최소 정보(SKU, 이름)만 함께 담는다.
- **판매 상품 조회**(`/products`, `/categories`, `/tags`): 점주가 판매하는 상품의 정보다. 이미지·가격·카테고리 등이 필요하면 클라이언트가 매장 상품의 `productId`로 이 API를 불러 합친다.

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | 근거 |
|---|---|---|---|
| GET | `/stores/{storeId}/products` | 매장 상품 목록 | 2.2, 3장 |
| PUT | `/stores/{storeId}/products/display-order` | 진열 순서 일괄 변경 | 2.3 |
| PUT | `/stores/{storeId}/products/{productId}/visibility` | 숨김·노출 전환 | 2.2 |
| PUT | `/stores/{storeId}/products/{productId}/stock-status` | 수동 품절 설정·해제 (재고 미추적 상품만) | 2.5 |
| GET | `/products` | 판매 상품 목록 | 2.2 |
| GET | `/products/{productId}` | 판매 상품 조회 | 2.2 |
| GET | `/products/{productId}/effective-options` | 유효 옵션 구성과 표시용 시작가 | 1.9 |
| GET | `/categories` | 카테고리 목록 | 1.6 |
| GET | `/tags` | 태그 목록 | 1.7 |

본사관리자가 매장 상품을 조회할 때는 [`GET /admin/stores/{storeId}/products`](#본사관리자의-매장-상품-조회)를 쓴다.

---

## 매장 상품

`storeId`가 요청자의 매장이 아니면 `403`이다. 변경 요청의 상품이 이 매장에서 판매할 수 없는 상품(판매 범위 밖)이면 `404`(`PRODUCT_NOT_FOUND`)다(요구사항 2.2).

### 매장 상품 (`StoreProduct`)

노출 판단(요구사항 3장)을 적용한 결과를 담는다.

```json
{
  "productId": 12,
  "sku": "DZ-00000012",
  "name": "아이스 아메리카노",
  "displayOrder": 1,
  "visibility": "VISIBLE",
  "stockStatus": "ON_SALE"
}
```

| 필드 | 설명 |
|---|---|
| `sku`, `name` | 대상 상품을 알아보기 위한 정보([설계 원칙](README.md#설계-원칙)). 매장 상품 목록의 상품은 모두 `ACTIVE`라 상태는 담지 않는다 |
| `displayOrder` | 점주가 정한 진열 순서(1부터). 정하지 않았으면 `null` |
| `visibility` | `VISIBLE` / `HIDDEN`. 개별 설정이 없으면 `VISIBLE` |
| `stockStatus` | `ON_SALE` / `SOLD_OUT`. 재고 미추적 상품은 점주의 수동 품절(없으면 `ON_SALE`), 재고 추적 상품은 매장 재고(받은 적 없으면 `SOLD_OUT`). `HIDDEN`이면 `null` |

### GET `/stores/{storeId}/products` — 매장 상품 목록 (시나리오 S6)

`ACTIVE`이고 이 매장이 판매 범위에 든 상품이 대상이다. 점주가 숨긴 상품도 다시 노출로 되돌릴 수 있도록 목록에 포함한다.

| 파라미터 | 설명 |
|---|---|
| `ids` | 상품 ID로 거른다 |

- 응답: `StoreProduct` 배열. 페이징하지 않는다.
- 정렬: 진열 순서를 정한 상품이 앞에 오름차순, 정하지 않은 상품은 뒤에 본사 등록 순(요구사항 2.3)

### PUT `/stores/{storeId}/products/display-order` — 진열 순서 일괄 변경 (요구사항 2.3)

점주가 화면에서 순서를 완성한 뒤 최종 순서 전체를 한 번에 보낸다. 상품 하나의 순서만 바꾸는 요청은 없다.

```json
{ "productIds": [15, 12, 20] }
```

- 목록의 상품은 그 순서대로 1부터 번호를 받는다.
- 목록에 없는 상품은 순서를 정하지 않은 상품이 되어 뒤로 간다.
- 요청 전체가 한 번에 반영된다. 일부만 반영되는 경우는 없다.
- 응답: 변경 후 `StoreProduct` 배열(목록 조회와 같음)
- 오류: `PRODUCT_NOT_FOUND`(404, 이 매장에서 판매할 수 없는 상품이 있음), `DUPLICATE_DISPLAY_ORDER_PRODUCT`(400, 같은 상품이 두 번)

### PUT `/stores/{storeId}/products/{productId}/visibility` — 숨김·노출 전환 (요구사항 2.2)

```json
{ "visibility": "HIDDEN" }
```

- 이 매장·상품의 개별 설정이 없으면 기본값으로 만든 뒤 바꾼다.
- 응답: `StoreProduct`
- 오류: `PRODUCT_NOT_FOUND`(404)

### PUT `/stores/{storeId}/products/{productId}/stock-status` — 수동 품절 (요구사항 2.5)

```json
{ "stockStatus": "SOLD_OUT" }
```

- 응답: `StoreProduct`
- 오류: `PRODUCT_NOT_FOUND`(404), `STOCK_STATUS_NOT_MANUALLY_EDITABLE`(422, 재고 추적 상품)

---

## 판매 상품 조회

`ACTIVE`이고 요청자의 매장이 판매 범위에 든 상품만 돌려준다([경로와 인가](README.md#경로와-인가)). 본사 내부 정보(상품 그룹, 판매 범위, 버전)는 담지 않는다.

### 판매 상품 (`SellableProduct`)

```json
{
  "id": 12,
  "sku": "DZ-00000012",
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "description": "산미가 적은 원두",
  "imageUrl": "https://cdn.example.com/p/12.png",
  "basePrice": 4500,
  "tracksInventory": false,
  "tagIds": [3]
}
```

상태는 항상 `ACTIVE`라 담지 않는다. 옵션은 [유효 옵션 구성](#get-productsproductideffective-options--유효-옵션-구성-요구사항-19)으로 받는다.

### GET `/products` — 판매 상품 목록

| 파라미터 | 설명 |
|---|---|
| `ids` | [목록 조회](README.md#목록-조회) |
| `keyword` | 상품명 부분 일치 또는 SKU 완전 일치 |
| `categoryId` | 소분류 또는 대분류. 대분류면 그 아래 소분류의 상품을 모두 포함 |
| `tagId` | |
| `page`, `size` | [페이징](README.md#페이징) |

- 응답: `SellableProduct`의 페이지. 본사 등록 순이다.

### GET `/products/{productId}` — 판매 상품 조회

- 응답: `SellableProduct`
- 오류: `PRODUCT_NOT_FOUND`(404, 없거나 판매할 수 없는 상품)

### GET `/products/{productId}/effective-options` — 유효 옵션 구성 (요구사항 1.9)

- 응답: 본사 API의 [유효 옵션 구성](product.md#get-productsproductideffective-options--유효-옵션-구성-요구사항-19)과 같은 형식
- 오류: `PRODUCT_NOT_FOUND`(404, 없거나 판매할 수 없는 상품)

### GET `/categories`, `/tags` — 카테고리·태그 목록

본사 API의 [카테고리 목록](product.md#get-categories--목록), [태그 목록](product.md#태그-요구사항-17)과 같은 형식과 파라미터다. 판매 상품의 `categoryId`, `tagIds`에 이름을 붙이는 데 쓴다.

---

## 본사관리자의 매장 상품 조회

### GET `/admin/stores/{storeId}/products`

본사관리자가 한 매장의 상품 상태를 조회한다. 파라미터와 응답은 [매장 상품 목록](#get-storesstoreidproducts--매장-상품-목록-시나리오-s6)과 같다. 상품별로 여러 매장을 보려면 [노출 현황 API](exposure.md)를 쓴다.
