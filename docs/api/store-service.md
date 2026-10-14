# Store 서비스용 상품 조회 API (내부 호출)

> 공통 규약은 [API 명세](README.md)를 따른다. 배경과 결정은 [ADR-0020](../adr/0020-store-owns-store-product-state.md)에 있다.

매장 단위의 상품 상태는 Store 서비스가 소유하고, Catalog는 판단에 필요한 상품 상태를 [이벤트](../events/product.md)로 알린다. 이름·가격·옵션 같은 **표시용 정보**는 이벤트에 없으므로 Store가 이 API로 조회한다. 점주 토큰과 로그인하지 않은 손님은 Catalog를 직접 부를 수 없고, Store가 이 결과를 합쳐서 준다.

- 호출자는 Store 서비스(system token)이고 역할은 `catalog:store_agent`다. 경로 앞에 `/api/v1/internal`이 붙는다.
- **매장과 점주를 모른다.** 경로에 `storeId`가 없다. 어느 매장이 상품을 볼 수 있는지(판매 범위, 숨김, 품절)는 Store가 이벤트로 복제한 상태로 판단한다.
- **상품 상태와 관계없이 조회된다.** `Draft`·`Discontinued` 상품도 돌려준다. 보여 줄지는 Store가 정한다.
- **본사 내부 정보를 담지 않는다.** 상품 그룹, 낙관적 잠금 버전, 설명은 응답에 없다. 판매 범위는 상태 목록에만 있다(이벤트와 같은 값이다).
- 기존 매장별 API([매장](store.md))는 Store 이전이 끝나면 없어진다([#138](https://github.com/dozycoffee/dozy-catalog-api/issues/138)).

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | 근거 |
|---|---|---|---|
| GET | `/internal/products?ids=` | 상품 기준 정보를 ID 목록으로 조회 | 1.9 |
| GET | `/internal/categories` | 카테고리 트리 | 1.6 |
| GET | `/internal/products/{productId}/effective-options` | 유효 옵션 구성과 표시용 시작가 (매장 무관) | 1.9 |
| GET | `/internal/product-states` | 전체 상품 상태 (커서 페이징) | [이벤트 계약 6절](../events/product.md#6-처음-채우기와-다시-맞추기) |

## GET `/internal/products` — 상품 기준 정보

```
GET /api/v1/internal/products?ids=1,2,3
```

```json
[
  {
    "productId": 1,
    "sku": "DZ-00000001",
    "name": "아메리카노",
    "categoryId": 3,
    "imageUrl": "https://cdn.example.com/p/1.png",
    "basePrice": 4500,
    "tracksInventory": false,
    "displayStartingPrice": 4800,
    "tags": [{ "id": 2, "name": "베스트" }, { "id": 1, "name": "신메뉴" }]
  }
]
```

- `ids`는 **필수**이고 최대 100개다. 없거나 넘으면 `400 INVALID_REQUEST`. 전체 상품을 한 번에 주지 않으며, 처음 채울 때는 `/internal/product-states`를 쓴다.
- 상품 id 순으로 돌려주고, 없는 ID는 오류 없이 빠진다. `ids=`처럼 비어 있으면 `[]`다.
- `sku`는 과거 데이터에 없을 수 있어 `null`일 수 있다.
- `displayStartingPrice`: 기준가에 필수 옵션 그룹마다 선택 가능한 옵션 중 최저가를 더한 값이다(요구사항 1.9). 목록에서 상품마다 유효 옵션 구성을 따로 부르지 않게 하려고 함께 준다.
- `tags`: 이름 순(가나다순 뒤에 영문)이다. 태그는 손님 메뉴의 배지·모아 보기에 쓴다.
- 카테고리 이름은 담지 않는다. `categoryId`로 `/internal/categories`에서 찾는다.

## GET `/internal/categories` — 카테고리 트리

```json
[
  {
    "id": 1,
    "name": "음료",
    "children": [{ "id": 3, "name": "커피" }, { "id": 5, "name": "차" }]
  },
  { "id": 2, "name": "푸드", "children": [{ "id": 4, "name": "디저트" }] }
]
```

- 대분류마다 소분류를 담는다. 상품은 소분류만 참조한다(요구사항 1.6).
- 대분류 배열과 각 `children` 배열은 본사가 정한 **노출 순서**다(요구사항 1.6). 모든 매장과 채널에서 같고, 손님 메뉴의 카테고리 탭 순서로 쓴다. 순서 값 자체는 담지 않고 배열 순서가 곧 순서다.
- 카테고리는 건수가 적어 페이징하지 않는다.

## GET `/internal/products/{productId}/effective-options` — 유효 옵션 구성

본사 API의 [유효 옵션 구성](product.md#get-productsproductideffective-options--유효-옵션-구성-요구사항-19)과 같은 응답이다. 매장과 상품 상태를 보지 않고 상품만으로 계산한다(판매 가능 여부는 Store가 먼저 판단한다).

- 없는 상품이면 `404 PRODUCT_NOT_FOUND`.

## GET `/internal/product-states` — 전체 상품 상태

Store가 복제본을 처음 채우거나 다시 맞출 때 쓴다([이벤트 계약 6절](../events/product.md#6-처음-채우기와-다시-맞추기)). 각 항목은 이벤트의 `data`와 같은 값이고, `sequence`도 같다. Store는 이벤트와 같은 규칙으로 반영하므로(순번 비교) 이벤트와 겹쳐도 안전하다.

```
GET /api/v1/internal/product-states?size=200&cursor=1024
```

```json
{
  "content": [
    {
      "productId": 1025,
      "sku": "DZ-00001025",
      "status": "ACTIVE",
      "storeScope": { "kind": "LIMITED", "targetStoreIds": [12, 35, 107] },
      "tracksInventory": false,
      "sequence": 42
    }
  ],
  "nextCursor": 1300
}
```

| 파라미터 | 설명 |
|---|---|
| `size` | 한 번에 받을 건수. 1~500, 기본 200. 벗어나면 `400 INVALID_REQUEST` |
| `cursor` | 이 ID보다 **큰** 상품부터 준다. 처음에는 주지 않는다. 양수가 아니면 `400 INVALID_REQUEST` |

- 상품 id 순이다. 응답의 `nextCursor`를 다음 요청의 `cursor`에 그대로 넣고, `null`이면 마지막이다. 한 번씩 빠짐없이 받는다.
- **삭제된 상품은 빠진다.** 삭제는 이벤트(`PRODUCT_DELETED`)로만 알린다. 다시 맞출 때는 이 목록에 없는 상품을 삭제로 표시한다.
- API 응답은 [공통 규약](README.md#요청응답-형식)대로 값이 없는 필드를 `null`로 준다. 이벤트와 달리 `sku`가 없으면 `null`이고, `ALL`의 `targetStoreIds`는 `null`이다(이벤트는 필드를 보내지 않는다). Store는 둘을 같게 본다.
- 페이지를 넘기는 사이 상품이 바뀌어도 `sequence`가 상태의 새로움을 말해 주므로 오래된 값이 새 값을 덮지 않는다.
