# 매장 API (내부 호출)

> 공통 규약은 [API 명세](README.md)를 따른다. 규칙은 [요구사항](../requirements.md) 2·3장과 [시나리오](../scenarios.md) S6, S7에 있다.

가맹점주의 요청을 받은 **Store 서비스**가 system token으로 부르는 내부 API다. 경로 앞에 `/api/v1/internal`이 붙고, 역할은 `catalog:store_agent`다([ADR-0018](../adr/0018-partner-requests-through-store-service.md)).

- **매장 소유 확인은 Store 서비스가 한다.** Catalog는 경로의 `storeId`를 그대로 신뢰하고, 그 매장이 그 상품을 취급할 수 있는지(판매 범위)만 확인한다.
- **본사 내부 정보를 담지 않는다.** 상품 그룹, 판매 범위, 낙관적 잠금 버전은 응답에 없다. 점주와 손님에게 그대로 전달될 수 있기 때문이다.
- 점주는 가격을 바꿀 수 없다(전 매장 동일가, 요구사항 2.1). 가격을 바꾸는 API는 이 문서에 없다.

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | 근거 |
|---|---|---|---|
| GET | `/internal/stores/{storeId}/products` | 매장 상품 목록 (상태 + 상품 정보) | 2.2, 3장 |
| PUT | `/internal/stores/{storeId}/products/display-order` | 진열 순서 일괄 변경 | 2.3 |
| PUT | `/internal/stores/{storeId}/products/{productId}/visibility` | 숨김·노출 전환 | 2.2 |
| PUT | `/internal/stores/{storeId}/products/{productId}/stock-status` | 수동 품절 설정·해제 (재고 미추적 상품만) | 2.5 |
| GET | `/internal/stores/{storeId}/products/{productId}/effective-options` | 유효 옵션 구성과 표시용 시작가 | 1.9 |

- 본사관리자가 한 매장의 상태를 볼 때는 [`GET /admin/stores/{storeId}/products`](#본사관리자의-매장-상품-조회)를 쓴다.
- 카테고리·태그 이름은 본사 API의 목록(`/admin/categories`, `/admin/tags`)과 같은 데이터라 내부 API에 따로 두지 않는다. Store 서비스가 그 목록을 받아 캐시해 쓴다.

## 매장 상품 (`StoreProduct`)

한 매장에서 한 상품의 상태와, 그 상품을 알아볼 최소 정보다. 노출 판단(요구사항 3장)을 적용한 결과를 담는다.

```json
{
  "productId": 12,
  "sku": "DZ-00000012",
  "name": "아이스 아메리카노",
  "categoryId": 5,
  "imageUrl": "https://cdn.example.com/p/12.png",
  "basePrice": 4500,
  "tracksInventory": false,
  "displayOrder": 1,
  "visibility": "VISIBLE",
  "stockStatus": "ON_SALE"
}
```

| 필드 | 설명 |
|---|---|
| `sku`, `name`, `categoryId`, `imageUrl`, `basePrice`, `tracksInventory` | 본사 기준 정보. 점주 화면이 그대로 쓸 수 있도록 함께 담는다 |
| `displayOrder` | 점주가 정한 진열 순서(1부터). 정하지 않았으면 `null` |
| `visibility` | `VISIBLE` / `HIDDEN`. 개별 설정이 없으면 `VISIBLE` |
| `stockStatus` | `ON_SALE` / `SOLD_OUT`. 재고 미추적 상품은 점주의 수동 품절(없으면 `ON_SALE`), 재고 추적 상품은 매장 재고(받은 적 없으면 `SOLD_OUT`). `HIDDEN`이면 `null` |

상품 그룹, 판매 범위, 상태(`status`), 버전은 담지 않는다. 목록의 상품은 모두 `ACTIVE`다. 숨김·수동 품절 변경의 응답은 `ACTIVE`가 아닌 상품일 수도 있다(각 엔드포인트 참고).

## GET `/internal/stores/{storeId}/products` — 매장 상품 목록 (시나리오 S6)

`ACTIVE`이고 이 매장이 판매 범위에 든 상품이 대상이다. 점주가 숨긴 상품도 다시 노출로 되돌릴 수 있도록 목록에 포함한다.

| 파라미터 | 설명 |
|---|---|
| `ids` | 상품 ID로 거른다 |

- 응답: `StoreProduct` 배열. 페이징하지 않는다.
- 정렬: 진열 순서를 정한 상품이 앞에 오름차순, 정하지 않은 상품은 뒤에 본사 등록 순(요구사항 2.3)

## PUT `/internal/stores/{storeId}/products/display-order` — 진열 순서 일괄 변경 (요구사항 2.3)

점주가 화면에서 순서를 완성한 뒤 최종 순서 전체를 한 번에 보낸다. 상품 하나의 순서만 바꾸는 요청은 없다.

```json
{ "productIds": [15, 12, 20] }
```

- 목록의 상품은 그 순서대로 1부터 번호를 받는다.
- 목록에 없는 상품은 순서를 정하지 않은 상품이 되어 뒤로 간다.
- 요청 전체가 한 번에 반영된다. 일부만 반영되는 경우는 없다.
- 응답: 변경 후 `StoreProduct` 배열(목록 조회와 같음)
- 오류: `PRODUCT_NOT_FOUND`(404, 이 매장에서 판매할 수 없는 상품이 있음), `DUPLICATE_DISPLAY_ORDER_PRODUCT`(400, 같은 상품이 두 번)

## PUT `/internal/stores/{storeId}/products/{productId}/visibility` — 숨김·노출 전환 (요구사항 2.2)

```json
{ "visibility": "HIDDEN" }
```

- 이 매장·상품의 개별 설정이 없으면 기본값으로 만든 뒤 바꾼다.
- 판매 범위에 든 상품이면 상태와 무관하게 바꿀 수 있다(`DRAFT`·`DISCONTINUED`도 미리 바꿔 두면 활성화 후 그대로 쓰인다, 요구사항 2.2, 1.3).
- 응답: 바꾼 결과의 `StoreProduct`. 목록과 달리 상품 상태를 보지 않고 점주의 설정을 담는다. `visibility`는 숨김 여부, `stockStatus`는 숨겼으면 `null`이고 아니면 판매 가능 여부(없으면 출처별 기본값)다.
- 오류: `PRODUCT_NOT_FOUND`(404, 없거나 판매 범위 밖인 상품). 거부되면 아무것도 바뀌지 않는다

## PUT `/internal/stores/{storeId}/products/{productId}/stock-status` — 수동 품절 (요구사항 2.5)

```json
{ "stockStatus": "SOLD_OUT" }
```

- 숨김·노출 전환과 같이 판매 범위에 든 상품이면 상태와 무관하게 바꿀 수 있고, 응답은 점주의 설정을 담은 `StoreProduct`다.
- 오류: `PRODUCT_NOT_FOUND`(404, 없거나 판매 범위 밖인 상품), `STOCK_STATUS_NOT_MANUALLY_EDITABLE`(422, 재고 추적 상품)

## GET `/internal/stores/{storeId}/products/{productId}/effective-options` — 유효 옵션 구성 (요구사항 1.9)

- 응답: 본사 API의 [유효 옵션 구성](product.md#get-productsproductideffective-options--유효-옵션-구성-요구사항-19)과 같은 형식
- 오류: `PRODUCT_NOT_FOUND`(404, 없거나 이 매장에서 판매할 수 없는 상품)

---

## 본사관리자의 매장 상품 조회

### GET `/admin/stores/{storeId}/products`

본사관리자(`catalog:admin`)가 한 매장의 상품 상태를 조회한다. 파라미터와 응답은 [매장 상품 목록](#get-internalstoresstoreidproducts--매장-상품-목록-시나리오-s6)과 같다. 상품별로 여러 매장을 보려면 [노출 현황 API](exposure.md)를 쓴다.
