# ADR-0014: 예약 대상은 상품·옵션 그룹뿐이고, 예약 값은 필드별 타입으로 표현하며, 활성화와 단종은 별도 필드로 예약한다

- **상태**: 채택
- **날짜**: 2026-09-19
- **관련**: 이슈 #49, ADR-0012, [요구사항](../requirements.md) 1.3·1.4, [시나리오](../scenarios.md) S2, [도메인 모델](../domain-model.md#예약-가능한-필드-요구사항-14), [ERD](../erd.md#scheduled_changes--예약-변경-필드-단위-00시-고정-적용)

## 맥락

예약 Repository를 구현하면서 V1의 예약 모델에 세 가지 문제가 드러났다.

- **대상 표현**: `target_kind`에 `PRODUCT_OPTION_GROUP`(상품-옵션 그룹 연결)이 있었지만 `target_id`는 하나뿐이다. (상품 ID, 옵션 그룹 ID) 쌍을 담을 수 없고, "같은 대상·같은 필드의 대기 예약은 최대 1건"을 부분 UNIQUE로 걸 수도 없다.
- **값 타입**: `ScheduledChange.newValue`가 `Any`였다. 필드마다 값 타입이 다르고(`Money`, `StoreScope`, 옵션 목록 등) 필드 이름은 문자열이라, 필드 이름과 값이 어긋나도, 새 필드를 추가하고 배치의 적용이나 JSONB 직렬화를 빠뜨려도 컴파일러가 알려 주지 않는다. 값은 다른 애그리거트의 모델을 담으므로 ADR-0012에 따라 domain `scheduledchange` 패키지에 둘 수 없다.
- **상태 예약**: 상태를 필드 하나로 예약하면 "10/1 활성화 + 10/31 단종" 같은 시즌 상품을 한 번에 예약할 수 없다. 같은 필드의 새 예약이 기존 예약을 대체하므로 두 번째 예약이 첫 번째를 취소한다.

## 결정

### 예약 대상은 상품과 옵션 그룹뿐이다
- `target_kind`는 `PRODUCT`, `OPTION_GROUP`만 둔다. V3 마이그레이션에서 CHECK를 교체한다.
- 상품-옵션 그룹 연결의 예약은 상품을 대상으로 한다.
  - 연결 목록과 순서는 상품의 `optionGroupLinks` 필드 하나로 예약한다(연결할 옵션 그룹 ID 목록 전체).
  - 상품별 옵션 예외는 필드 이름에 옵션 그룹 ID를 넣는다(`optionOverrides:{optionGroupId}`). 값은 그 옵션 그룹에 대한 예외 전체이며, 옵션 목록 예약처럼 등록 시점의 스냅샷이다. 옵션 그룹이 다르면 다른 필드라 함께 대기할 수 있다.

### 예약 값은 필드별 타입으로 표현한다
- 예약 가능한 필드마다 값 타입을 하나씩 두고, 필드 이름과 대상 종류는 그 타입이 정한다. 목록은 [도메인 모델](../domain-model.md#예약-가능한-필드-요구사항-14)에 있다.
- domain은 인터페이스 `ScheduledValue`(대상 종류, 필드 이름)만 안다. `ScheduledChange.newValue`의 타입이 되고, `NewScheduledChange.of`는 대상 종류와 필드 이름을 값에서 얻는다.
- 구현은 application의 sealed 계층 `ScheduledFieldValue`(`ProductFieldValue`, `OptionGroupFieldValue`)로 `application/scheduledchange`에 둔다. 값이 다른 애그리거트의 모델을 담기 때문이고(ADR-0012), Kotlin sealed 타입은 같은 패키지에서만 구현할 수 있어 하위 타입도 모두 그 패키지에 둔다.
- 배치의 적용과 JSONB 직렬화는 sealed 타입을 `when`으로 빠짐없이 처리한다. 필드를 추가하고 처리를 빠뜨리면 컴파일 에러가 된다.
- JSONB에는 타입 구분자와 값을 함께 저장한다(`{"type": …, "value": …}`). 직렬화는 Jackson 하나로 하고, 형식은 infrastructure의 코덱에 명시적으로 적는다. 클래스 이름에 기대는 자동 다형 직렬화는 쓰지 않는다.
- 재고 추적 여부, 옵션 그룹의 이름·선택 방식·필수 여부는 예약 대상이 아니다(즉시 반영만).

### 활성화와 단종은 별도 필드로 예약한다
- 활성화(`activation`) 예약과 단종(`discontinuation`) 예약은 서로 다른 필드라 한 상품에 함께 대기할 수 있다. 같은 종류의 새 예약은 기존 대기 예약을 대체한다.
- 날짜 순서가 맞지 않아 적용 시점에 상태 전이가 불가능하면 기존 규칙대로 그 예약만 `실패`로 기록한다.

## 검토한 대안

- **필드 이름 → 값 타입 레지스트리(Map)**: domain이나 infrastructure에 필드 이름 문자열과 값 클래스를 잇는 표를 둔다. 필드를 추가하고 표나 적용 로직을 빠뜨려도 런타임에야 드러나고, 필드 이름과 값이 어긋난 예약을 만들 수 있다.
- **`target_sub_id` 컬럼 추가**: `PRODUCT_OPTION_GROUP`을 유지하고 옵션 그룹 ID를 별도 컬럼에 담는다. 부분 UNIQUE를 (target_id, target_sub_id, target_kind, field_name)로 넓혀야 하고, 대부분의 예약에서 NULL인 컬럼이 생기며 NULL을 포함한 UNIQUE를 따로 다뤄야 한다. 연결 예약은 결국 상품의 필드를 바꾸는 것이라 상품을 대상으로 보는 편이 적용 로직(Product 행 잠금과 저장)과도 맞는다.
- **상태를 필드 하나(`status`)로 예약**: 목표 상태를 값으로 담는다. 같은 필드의 새 예약이 기존 예약을 대체하므로 시즌 상품의 활성화·단종을 함께 예약할 수 없다. "같은 필드 대기 예약 최대 1건" 규칙에 상태만 예외를 두면 규칙과 DB 제약이 복잡해진다.
- **sealed 값 타입을 domain에 둠**: 필드별 타입이 `StoreScope`, `OptionOverride`, `Option` 같은 다른 애그리거트의 모델을 담게 되어 ADR-0012의 의존 규칙을 어긴다.

## 결과

- **얻는 것**
  - 필드와 값의 대응, 적용·직렬화 누락을 컴파일러가 잡는다.
  - "같은 대상·같은 필드의 대기 예약 1건"이 모든 예약 종류에 하나의 부분 UNIQUE로 걸린다.
  - 시즌 상품의 활성화·단종, 옵션 그룹별 예외를 각각 예약할 수 있다.
- **감수하는 것**
  - domain의 `ScheduledChange`는 값의 실제 타입을 모른다. 배치는 `newValue`를 `ScheduledFieldValue`로 좁혀 쓴다.
  - 옵션 그룹별 예외의 필드 이름이 옵션 그룹 ID를 포함한 문자열이라, 대기 예약을 찾을 때 `ProductFieldValue.OptionOverrides.fieldNameOf(optionGroupId)`로 이름을 만든다.
  - JSONB 형식(`type` 문자열과 키 이름)은 저장된 데이터와의 계약이 되어 바꾸기 어렵다.
  - 활성화와 단종의 날짜 순서가 맞지 않는 예약도 등록은 된다. 적용 시점에 실패로 드러난다.
- **후속 작업**
  - 예약 등록·취소 유스케이스와 00시 배치(`ScheduledChangeBatchApplicationService`)에서 필드별 적용을 구현한다.
