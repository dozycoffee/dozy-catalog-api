# 공개 참조 데이터 API (인증 없음)

> 공통 규약은 [API 명세](README.md)를 따른다. 배경과 결정은 [ADR-0022](../adr/0022-public-read-only-reference-data.md)에 있다.

매장과 무관한 읽기 전용 참조 데이터를 토큰 없이 제공한다. 점주·손님 채널이 Store 서비스를 거치지 않고 직접 읽는 용도다. 매장 소유 확인이 필요한 데이터와 상품은 여기에 없고, 계속 Store 서비스를 거친다([ADR-0018](../adr/0018-partner-requests-through-store-service.md)).

- 경로 앞에 `/api/v1/public`이 붙는다. **`GET`만 열려 있고** 다른 메서드는 인증을 요구한다.
- 토큰을 보내도 응답은 같다. 유효하지 않은 토큰을 보내면 `401`이다.
- 응답에는 본사 내부 정보를 담지 않는다. 공개 범위를 넓힐 때는 새 ADR이 필요하다.
- **캐시**: `Cache-Control: public, max-age=600`. 채널과 중간 캐시는 10분 동안 다시 묻지 않으므로, 본사의 변경(예: 순서)이 채널에 보이기까지 최대 10분 걸린다.
- 이 경로의 CORS는 열지 않았다. 브라우저에서 직접 부르는 채널이 생기면 출처를 정해 연다.

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | 근거 |
|---|---|---|---|
| GET | `/public/categories` | 카테고리 트리 | 1.6 |

## GET `/public/categories` — 카테고리 트리

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

- Store용 [`/internal/categories`](store-service.md#get-internalcategories--카테고리-트리)와 같은 응답이다.
- 대분류 배열과 각 `children` 배열은 본사가 정한 **노출 순서**다(요구사항 1.6). 순서 값 자체는 담지 않고 배열 순서가 곧 순서다.
- 상품은 소분류만 참조한다. 상품의 `categoryId`로 이 트리에서 이름을 찾는다.
- 카테고리는 건수가 적어 페이징하지 않는다.
