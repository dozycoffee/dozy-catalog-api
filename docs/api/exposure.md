# 노출 현황 API (본사관리자)

> 공통 규약은 [API 명세](README.md)를 따른다. 경로 앞에 `/api/v1/admin`이 붙고, 모두 본사관리자 전용이다. 규칙은 [요구사항](../requirements.md) 1.10과 3장에 있다.

상품별로 판매 가능 매장 수와 노출 중인 매장 수를 계산한 결과다. 매장마다 노출 판단(3장)을 돌려 세는 업무 계산이라 백엔드가 제공한다. 상품 정보(이름, SKU, 상태)는 담지 않는다. 클라이언트가 `GET /admin/products?ids=…`로 받아 합친다. 매출·판매량은 다루지 않는다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/product-exposures` | 상품별 노출 현황 요약 |
| GET | `/product-exposures/{productId}` | 한 상품의 매장별 노출 상태 |

## 노출 현황 (`ProductExposure`)

```json
{
  "productId": 12,
  "sellableStoreCount": 120,
  "exposedStoreCount": 97
}
```

| 필드 | 설명 |
|---|---|
| `sellableStoreCount` | 판매 범위만으로 정한 매장 수. `ALL`이면 전체 매장, `LIMITED`면 대상 매장 수. 상품 상태와 무관 |
| `exposedStoreCount` | 노출 판단(3장)을 통과한 매장 수. 품절이어도 숨기지 않았으면 센다. `ACTIVE`가 아니면 0 |

## GET `/product-exposures` — 요약

| 파라미터 | 설명 |
|---|---|
| `ids` | 상품 ID로 거른다 |
| `categoryId` | 소분류 또는 대분류. 대분류면 그 아래 소분류의 상품을 모두 포함 |
| `tagId`, `groupId` | |
| `page`, `size` | [페이징](README.md#페이징) |

- 응답: `ProductExposure`의 페이지. 상품 등록 순이다.
- 페이지에 든 상품만 노출 판단을 계산한다.

## GET `/product-exposures/{productId}` — 매장별 상태

판매 가능한 매장마다 노출 상태를 준다. 요약의 수치와 항상 맞아떨어진다.

```json
{
  "productId": 12,
  "sellableStoreCount": 2,
  "exposedStoreCount": 1,
  "stores": [
    { "storeId": 101, "exposed": true, "stockStatus": "SOLD_OUT" },
    { "storeId": 102, "exposed": false, "stockStatus": null }
  ]
}
```

- `stores`는 매장 ID 순이다. `exposed`는 노출 판단 결과다(점주의 숨김 여부인 `visibility`와 다르다). `false`면 `stockStatus`는 `null`이다.
- 매장 이름 등 매장 정보는 Store BC의 것이라 담지 않는다.
- 오류: `PRODUCT_NOT_FOUND`(404)
