# ADR-0018: 가맹점주의 요청은 Store 서비스가 받고, Catalog는 내부 API로 제공한다

- **상태**: 채택
- **날짜**: 2026-10-01
- **관련**: ADR-0017(경로 분리 기준 일부 대체), [API 명세](../api/README.md#경로와-인가), [매장 API](../api/store.md), `dozy-auth` 토큰 계약 v1.0

## 맥락

ADR-0017은 호출자를 본사관리자와 가맹점주로 보고 경로를 `/admin/**`와 `/stores/{storeId}/**`, 그리고 점주용 조회 경로로 나눴다. 인증은 미정이었고, 점주의 매장을 토큰으로 판별할 수 있다고 전제했다.

그 뒤 `dozy-auth`가 토큰 계약 v1.0과 서비스용 스타터를 내놓았고, 전제가 깨졌다.

- Catalog가 받을 수 있는 realm은 `internal`뿐이다. 점주 토큰은 realm이 `partner`이고 `aud`가 `["store"]`로 고정이라 Catalog에 들어올 수 없다.
- 파트너에게는 role을 부여하지 않으며, **매장과 점주의 관계(`store_member`)는 Store 서비스가 소유한다**(dozy-auth DOM-04).
- 토큰에는 매장 정보가 없다. 리소스 인가(소유 여부)는 각 서비스가 자기 데이터로 하라는 것이 Auth의 규칙이다.

즉 Catalog는 "이 요청자가 이 매장의 점주인가"를 스스로 판단할 자료가 없다.

## 결정

- **가맹점주의 요청은 Store 서비스가 받는다.** Store 서비스가 점주 토큰을 검증하고 `store_member`로 매장 소유를 확인한 뒤, system token으로 Catalog를 호출한다.
- **Catalog의 호출자는 본사 직원과 Store 서비스뿐이다.** 경로를 둘로 나눈다.
  - `/api/v1/admin/**` — 본사 직원(realm `internal`, 역할 `catalog:admin`)
  - `/api/v1/internal/**` — Store 서비스(system token, 역할 `catalog:store_agent`)
- **역할은 둘로 나눈다.** 본사 직원은 `catalog:admin`, Store 서비스는 `catalog:store_agent`다. Store 서비스에 본사 역할을 주지 않아, Store 쪽이 노출돼도 본사 API까지 열리지 않게 한다.
- **Catalog는 경로의 `storeId`를 신뢰한다.** 매장 소유 확인은 Store 서비스의 책임이다. Catalog는 그 매장이 그 상품을 취급할 수 있는지(판매 범위, 요구사항 2.2)만 확인한다.
- **내부 API 응답에는 본사 내부 정보를 담지 않는다.** 상품 그룹(요구사항 1.8), 판매 범위, 낙관적 잠금 버전이다. 점주와 손님에게 그대로 전달될 수 있다.
- **매장 상품 목록은 상품 기준 정보(SKU, 이름, 카테고리 ID, 이미지, 기준가, 재고 추적 여부)를 함께 담는다.** Store 서비스가 상품 조회를 한 번 더 부르지 않게 하기 위해서다. 점주용 상품 조회 경로는 두지 않는다.
- **오류 응답은 RFC 9457 Problem Details로 통일한다.** 스타터가 401·403을 그 형식으로 돌려주므로 Catalog의 도메인 오류도 같은 형식으로 맞춘다.
- 요구사항의 액터(가맹점주)와 규칙은 그대로다. 그 요청을 누가 받아 전달하는지만 정한다.

## 검토한 대안

- **Auth가 partner 토큰의 `aud`에 `catalog`를 넣고 점주 role을 허용**: Catalog가 점주 요청을 직접 받을 수 있다. 하지만 Auth의 결정(DOM-04, `aud` 고정)을 바꿔야 하고, 그래도 매장 소유 확인은 Catalog가 할 수 없어 Store 서비스를 동기 호출하거나 `store_member`를 로컬 투영해야 한다. 경계가 두 서비스에 걸쳐 흐려진다.
- **Store 서비스가 점주 화면 응답까지 조립**: Catalog는 지금 결정과 같은 내부 API를 제공하고, 화면 조립만 Store가 맡는다. 결정과 사실상 같으며, 어디까지 조립할지는 Store 담당자와 정하면 된다.
- **점주 앱이 Catalog를 직접 호출하고 Catalog가 Store에 소유를 물어봄**: 요청마다 서비스 간 호출이 생기고 Store가 죽으면 점주 화면 전체가 멈춘다.

## 결과

- **얻는 것**
  - 매장과 점주의 관계가 Store 서비스 한곳에만 있다. Catalog는 `storeId`만 참조한다는 기존 BC 경계와 맞는다.
  - Catalog는 realm `internal`만 받으므로 인증 설정이 단순하다.
  - 오류 형식이 서비스 전체에서 하나다.
- **감수하는 것**
  - 점주 화면 요청이 서비스를 한 번 더 거친다.
  - Catalog의 내부 API는 호출자를 신뢰한다. system client의 비밀 관리와 `/internal/**`의 노출 범위가 보안 경계가 된다.
  - 매장 상품 목록이 상품 기준 정보를 함께 담아 ADR-0017의 "리소스는 자기 데이터만 담는다"에서 한 걸음 물러난다. 대신 Store 서비스의 호출 수가 줄어든다.
- **후속 작업**: Catalog 역할 코드와 Store의 system client를 Auth에 등록한다. Store 서비스와 내부 API의 호출 시점·캐시를 맞춘다. Catalog → Store BC 호출은 WebFlux용 system token 클라이언트가 나온 뒤에 구현한다.
