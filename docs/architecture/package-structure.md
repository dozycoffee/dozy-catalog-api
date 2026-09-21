# 패키지 구조 (도메인 모듈 우선)

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를, 의존 방향 원칙은 [아키텍처 개요](README.md)를 참고한다. 모듈 구조의 근거는 [ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md)에, 애그리거트 간 참조 규칙과 Repository·포트 배치의 근거는 [ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)에 있다.

base package: `com.dozycoffee.catalog`

## 원칙

1. **최상위는 도메인 모듈이고, 그 안에서 레이어로 나눈다.** 모듈은 요구사항의 장 구분을 따른다.
   - `product` — 본사가 정의하는 상품 (요구사항 1장)
   - `store` — 가맹점의 진열과 판매 가능 여부 (요구사항 2·3장)
   - `schedule` — 예약 변경 (요구사항 1.4)
   - `exposure` — 노출 현황 조회 (요구사항 1.10)
   - 모듈 안은 `domain` / `application` / `infrastructure` / `presentation`으로 나누고, 의존 방향은 `presentation → application → domain ← infrastructure`다.
2. **애그리거트는 모듈 안의 패키지다.** 애그리거트는 트랜잭션 경계이지 모듈 경계가 아니다. 한 트랜잭션에서 함께 바뀌는 애그리거트(Product와 OptionGroup 등)는 같은 모듈에 둔다.
3. **모듈 간 의존은 단방향이다.** `store → product`, `schedule → product`, `exposure → product, store`. `product`은 다른 모듈을 참조하지 않는다. 순환은 만들지 않는다.
4. **모듈 밖에 두는 것은 두 가지뿐이다.**
   - `core` — 여러 모듈이 쓰는 도메인 타입(`AggregateRoot`, `DomainEvent`, `DomainException`, `ErrorCode`, `Money`, `StoreId` 등). 프레임워크를 모르고, 특정 애그리거트의 개념은 두지 않는다. 계층 없이 평평하다.
   - `common` — 애그리거트에 속하지 않는 기술 공통(`TransactionRunner`, `BusinessTimeZone`, `event/`, `exposed/`, `paging/`, `time/`, `web/`). 모듈을 참조하지 않는다.
5. **서브패키지 규칙**: 폴더 안 파일이 6~7개를 넘거나 역할 종류(event/exception 등)가 3가지 이상 섞이면 역할별 서브패키지로 나눈다. 그 미만이면 평평하게 유지한다. Repository 인터페이스는 애그리거트 패키지 최상위에 둔다(모델과 짝을 이루는 존재라 바로 보이는 게 낫다).
6. **DTO 정책**: Request/Response DTO는 모듈의 `presentation/dto`에서만 쓴다. `application` 경계에서 Command/Query 객체로 변환한다. Exposed `Table` 객체와 row 매핑은 모듈의 `infrastructure` 안에서만 쓰고 domain 모델과 분리한다.

## 모듈 간 참조 규칙

| 계층 | 다른 모듈의 무엇을 참조할 수 있나 |
|---|---|
| `domain` | ID 타입만 (`ProductId`, `OptionGroupId`, `OptionKey`, `CategoryId` 등) |
| `application` | 다른 모듈의 도메인 모델 전체. 단 **읽기만** 한다 |
| `infrastructure` | 모듈 의존 방향과 같은 방향이면 허용 (JSONB 직렬화, FK 선언 등 기술적인 이유) |
| `presentation` | 자기 모듈만 |

- **다른 모듈의 애그리거트는 읽기만 한다.** 바꿔야 하면 그 모듈의 유스케이스를 호출하거나 이벤트를 발행한다. 예약 적용 배치는 `Product`를 직접 조작하지 않고 `product` 모듈의 유스케이스를 호출하고, 판매 범위 변경으로 매장 설정을 지우는 일은 `product`이 이벤트를 발행하고 `store`가 구독한다. 그래야 잠금·검증·이벤트 발행이 한곳에 남고 의존 방향이 유지된다.
- **모듈 안에서도 애그리거트끼리는 ID로만 참조한다.** 여러 애그리거트를 함께 보는 판단은 그 모듈의 `application/policy`에 I/O 없는 순수 클래스로 둔다([ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)).

## 유스케이스 작성 관례

- 유스케이스 서비스는 `<모듈>/application/<애그리거트>/`에 `<Aggregate>ApplicationService`로 두고, 입력 Command는 그 아래 `command/`에 클래스 하나당 한 파일로 둔다.
- 한 애그리거트의 유스케이스가 많아지면 관심사별로 서비스를 나눈다. 협력하는 상대가 다르면 나눌 이유가 된다(예: 상품의 옵션 그룹 연결·예외는 옵션 그룹을 함께 조회하므로 `ProductOptionApplicationService`로 나눴다). 나눠도 Command는 애그리거트의 같은 `command/`에 둔다.
- **Command는 도메인 타입으로 담는다.** 문자열·정수 ID를 그대로 넘기지 않고 `CategoryId`, `Money` 같은 타입으로 바꿔 담는다. 변환은 presentation 경계에서 한다.
- 필드가 둘 이상인 입력만 Command로 만든다. 식별자 하나만 받는 유스케이스(삭제, 단건 조회)는 파라미터로 받는다.
- 서비스가 `TransactionRunner.inTransaction { … }`으로 유스케이스의 트랜잭션 경계를 연다. Repository는 그 안에서 호출된다([ADR-0011](../adr/0011-transaction-boundary-with-transaction-runner.md)).
- 조회가 필요한 규칙(하위 카테고리 존재, 참조 상품 존재 등)은 서비스가 확인해 도메인 메서드에 값으로 넘긴다. 판단 자체가 여러 애그리거트를 보면 `application/policy`의 정책 클래스에 둔다([ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)).
- 대상이 없으면 애그리거트별 `XxxNotFoundException`(`NOT_FOUND`, 404)으로 거부한다.
- **같은 규칙을 여러 경로(즉시 변경, 예약 등록, 예약 적용)가 확인하면 그 규칙을 가진 모듈에 공용 컴포넌트로 한 번만 둔다.** 예: 상품이 가리키는 카테고리·태그·상품 그룹·옵션 그룹·매장의 존재는 `product`의 `ProductReferenceValidator`가 확인하고, 예약 등록(`ScheduledValueValidator`)도 이를 부른다. 공용 컴포넌트는 트랜잭션을 열지 않고 부르는 유스케이스의 트랜잭션 안에서 쓴다.
- **여러 애그리거트를 한 트랜잭션에서 바꿀 때는 서비스가 잠금·검증·저장 순서를 정한다.** 잠그고 → 정책에 넘겨 판단하고 → 애그리거트 메서드로 바꾸고 → 저장한다. 이때 **실제로 바뀐 애그리거트만 저장한다.** 바뀐 것 없이 저장하면 낙관적 잠금 대상의 `version`만 올라가 다음 수정이 충돌로 거부된다([ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)). 예: 옵션 목록 교체는 옵션 그룹과 연결 상품을 함께 잠그지만, 사라진 옵션 키의 예외를 실제로 갖고 있던 상품만 저장한다.
- **여러 애그리거트를 묶어 보여 주는 조회**는 각 Repository로 불러와 application에서 합치고(`<모듈>/application/<화면 단위>/`의 `XxxQueryService`), 결과는 그 옆의 View 타입에 담는다. 전용 SQL이나 집계가 필요해지면 그때 조회 포트로 옮긴다.
- **조건 검색과 페이징이 필요한 목록**은 조회 포트가 조건에 맞는 애그리거트 ID의 한 페이지(`common.paging.Page`)만 고르고, application이 그 ID로 Repository에서 애그리거트를 불러와 포트가 정한 순서대로 합친다(예: 상품 목록의 `ProductSearchQueryPort` → `ProductRepository.findAllByIds`). 애그리거트 복원은 Repository 한 곳에만 두고, 검색 조건은 여러 테이블을 볼 수 있게 하기 위해서다. 같은 목록을 호출자별로 다른 범위로 보여 줄 때(본사의 전체 상품, 점주의 판매 상품)는 같은 포트에 범위 조건만 달리 넘긴다.
- 페이지 크기·`ids` 개수 상한은 presentation이 요청 오류로 먼저 거르고, application 타입(`PageRequest`, 검색 조건)은 `require`로 한 번 더 막는다([예외 구조](exception.md)).
- **예약 적용처럼 사람이 보던 화면이 없는 경로는 버전을 요구하지 않는다.** 즉시 반영(PUT)은 화면이 보던 버전을 받아 그 사이의 변경을 거부하지만(낙관적 잠금, [ADR-0013](../adr/0013-optimistic-locking-for-product-and-option-group.md)), 배치는 비교할 화면이 없으므로 대상 행을 잠그고(`findByIdForUpdate`) 최신 상태에 적용한다. 그래서 필드 하나만 바꾸는 유스케이스(`ProductFieldApplicationService`, 버전 없는 `replaceOptions`·`replaceOptionGroupLinks`·`replaceOptionOverrides`)를 즉시 반영과 나란히 둔다. 잠금·검증·이벤트 발행은 그대로 그 모듈의 유스케이스 안에서 일어난다.
- **배치는 처리 단위마다 트랜잭션을 연다.** 예약 적용 배치는 대상 목록을 짧은 트랜잭션에서 고른 뒤, 예약 한 건마다 그 행을 다시 잠그고(`FOR UPDATE SKIP LOCKED`) 적용한다. 적용과 `APPLIED` 기록은 같은 트랜잭션에서 함께 커밋해 "적용됐는데 대기로 남는" 상태를 만들지 않고, 규칙 위반(`DomainException`)이면 적용 트랜잭션을 통째로 롤백해 대상 값을 되돌린 뒤 **별도 트랜잭션에서** `FAILED`만 기록한다(같은 트랜잭션에서 기록하면 롤백에 함께 쓸려 나간다). 그 밖의 예외는 규칙 위반이 아니므로 실패로 확정하지 않고 대기로 남겨 다음 실행에서 다시 시도한다. 한 번에 처리할 건수에는 상한을 둔다.
- **도메인 이벤트는 저장 후 `pullDomainEvents()`로 꺼내 같은 트랜잭션에서 동기로 처리한다.** 중간 상태를 만들지 않고, 핸들러가 실패하면 원래 변경도 함께 롤백된다. 발행·구독 방식은 아래를 따른다. 규모가 커지거나 다른 BC로 나가는 전파가 생기면 비동기로 바꾼다(전환 조건은 [미정 사항](README.md#미정-사항)).

### 도메인 이벤트 발행과 구독

- **BC 안의 구독자에게는 `common.event.DomainEventDispatcher`로 전달한다.** 저장한 애그리거트에서 `pullDomainEvents()`로 꺼낸 이벤트를 넘기면, 구독 모듈이 Spring 빈으로 등록한 `DomainEventHandler`가 발행 측의 트랜잭션·코루틴에서 동기로 실행된다. 별도 스레드나 큐를 쓰지 않으므로 정리가 끝나기 전의 중간 상태가 생기지 않고, 핸들러가 예외를 던지면 원래 변경까지 롤백된다.
- **발행 측은 구독 측을 모른다.** `product`이 `ProductStoreScopeChanged`를 발행하고 `store`의 `StoreScopeCleanupHandler`가 구독해 매장 설정을 정리하므로, `product`이 `store`를 참조하지 않고도 정리가 일어난다. 모듈 간 의존은 `store → product` 한 방향으로 유지된다([ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md)).
- **외부 서비스(POS·정산 등)로 나가는 전파는 이 경로가 아니라 `ProductEventPublisherPort`가 맡는다.** 실패해도 원래 요청을 되돌리면 안 되는 처리라 트랜잭션과 수명이 다르기 때문이다. 같은 이벤트를 두 경로로 보내지 않는다.
- 핸들러는 구독 모듈의 `application` 아래에 두고, 자기 모듈의 Repository와 정책만 쓴다. 이미 열린 트랜잭션을 이어 쓰도록 `TransactionRunner.inTransaction { … }` 안에서 작업해, 예약 적용처럼 다른 경로에서 불려도 같게 동작하게 한다.

## 인터페이스와 구현 배치

| 종류 | 인터페이스 위치 | 구현 | 구현이 쓰는 것 |
|---|---|---|---|
| Repository | `<모듈>.domain.<애그리거트>.<Aggregate>Repository` | `<모듈>.infrastructure.<애그리거트>.Exposed<Aggregate>Repository` | 자기 애그리거트 테이블만 |
| 외부 시스템 포트 | `<모듈>.application.port` | `<모듈>.infrastructure.acl.…Adapter` | Store BC 등의 Client(번역 포함) |
| 조회 포트 (여러 애그리거트·테이블에 걸친 읽기, 조건 검색) | `<모듈>.application.port` | `<모듈>.infrastructure.query.…` | Exposed로 테이블 직접 조회 |

- Repository는 자기 애그리거트만 다룬다. 자기 테이블에 대한 단순 존재 조회(예: `ProductRepository.existsByCategory`)는 그 애그리거트의 Repository에 둔다.
- 포트 구현체는 domain Repository를 호출해 조합하지 않는다. 무엇을 불러와 어떻게 합칠지는 오케스트레이션이므로 application에 둔다.

### 외부 호출 지점

| 연동 대상 | 방향 | 포트 위치 | 구현 위치 |
|---|---|---|---|
| Store BC — 매장 존재 검증 | outbound | `product.application.port.ValidateStoreExistsPort` | 지금은 모든 매장이 존재한다고 답하는 `product.infrastructure.acl.AlwaysExistingStoreAdapter`. 호출 방식이 정해지면 `StoreBcAdapter`(포트 구현) + `StoreBcClient`(호출)로 교체 |
| Store BC — 전체 매장 목록(노출 현황의 판매 가능 매장) | outbound | `exposure.application.port.StoreDirectoryPort` | 지금은 설정값(`catalog.exposure.store-ids`)을 돌려주는 `exposure.infrastructure.acl.ConfiguredStoreDirectory`. 호출 방식이 정해지면 실제 어댑터로 교체 |
| 재고관리 서비스 — 입고/품절/재입고 이벤트 구독 | inbound | (`store.application`에서 직접 처리) | `store.infrastructure.messaging.InventoryEventConsumer` + `store.infrastructure.acl.InventoryServiceEventTranslator` |
| POS/정산 등 — 상품 상태·정보 변경 이벤트 발행 | outbound | `product.application.port.ProductEventPublisherPort` | `common`의 이벤트 발행 구현 또는 `product.infrastructure.eventing.DomainEventPublisher` |

## 전체 트리

목표 구조다. 아직 구현되지 않은 것(유스케이스, presentation, exposure)도 포함한다.

```
com.dozycoffee.catalog
├── DozyCatalogApiApplication.kt
│
├── core/                                  # 도메인 공통 타입 (평평, 프레임워크 무관)
│   ├── AggregateRoot.kt / Entity.kt / VersionedAggregateRoot.kt
│   ├── DomainEvent.kt
│   ├── DomainException.kt / ErrorCode.kt / ErrorType.kt / SharedErrorCode.kt
│   ├── VersionConflictException.kt / InvalidMoneyAmountException.kt
│   └── Money.kt / StoreId.kt
│
├── common/                                # 기술 공통 (모듈을 참조하지 않음)
│   ├── TransactionRunner.kt               # 유스케이스의 트랜잭션 경계 (ADR-0011)
│   ├── BusinessTimeZone.kt
│   ├── event/                             # DomainEventDispatcher, DomainEventHandler + 구현 (BC 안 동기 전달)
│   ├── exposed/                           # ExposedConfiguration, ExposedTransactionRunner, AuditColumns, JsonbColumnType,
│   │                                      #   ListQueryExpressions(목록 조회의 검색어·이름 순 정렬)
│   ├── paging/                            # PageRequest, Page (목록 조회의 페이징), requireIdsWithinLimit(ids 개수 상한)
│   ├── time/                              # TimeConfiguration
│   └── web/                               # GlobalExceptionHandler, ErrorResponse
│
├── product/                               # 본사가 정의하는 상품 (요구사항 1장)
│   ├── domain/
│   │   ├── product/                       # Product(Aggregate Root), ProductOptionGroupLink, OptionOverride,
│   │   │   ├── event/                     #   ProductId·Sku·ProductStatus·StoreScope, ProductRepository
│   │   │   └── exception/
│   │   ├── optiongroup/                   # OptionGroup, Option, OptionKey, SelectionType, Repository + exception/
│   │   ├── category/                      # Category(sealed), CategoryId, Repository + exception/
│   │   ├── tag/                           # Tag, TagId, Repository + event/
│   │   └── productgroup/                  # ProductGroup, ProductGroupId, Repository + event/
│   ├── application/
│   │   ├── policy/                        # EffectiveOptionResolver, EffectiveOptionConfig, OptionReplacementPolicy
│   │   ├── product/                       # ProductApplicationService, ProductOptionApplicationService,
│   │   │   │                              #   ProductFieldApplicationService(예약 적용용 필드 단위) + command/ (+ SkuGenerator),
│   │   │   │                              #   ProductReferenceValidator·ProductTagResolver(즉시 변경·예약 등록·적용이 함께 쓰는 참조 확인·태그 이름 해석)
│   │   │   └── query/                     # 상품 목록 검색: ProductQueryService(본사), SellableProductQueryService(점주) + 검색 조건
│   │   ├── optiongroup/ category/ tag/ productgroup/  # 애그리거트별 유스케이스 서비스 + command/
│   │   └── port/                          # ProductEventPublisherPort, ValidateStoreExistsPort, ProductSearchQueryPort(조회)
│   ├── infrastructure/                    # 애그리거트별 Exposed Table + Repository 구현
│   │   ├── product/ optiongroup/ category/ tag/ productgroup/
│   │   ├── query/                         # ExposedProductSearchQuery (조건 검색·페이징으로 상품 ID를 고름)
│   │   ├── eventing/                      # 상품 이벤트 발행 구현 (지금은 로그)
│   │   └── acl/                           # AlwaysExistingStoreAdapter (StoreBcAdapter + StoreBcClient는 5단계)
│   └── presentation/                      # (4단계)
│
├── store/                                 # 가맹점 진열·판매 가능 여부 (요구사항 2·3장)
│   ├── domain/
│   │   ├── display/                       # StoreDisplaySetting, StoreDisplaySettingId, Visibility, Repository
│   │   └── availability/                  # StoreProductAvailability, AvailabilitySource, StockStatus, Repository + exception/
│   ├── application/
│   │   ├── policy/                        # ProductVisibilityPolicy, StoreVisibility, StoreScopeCleanupPolicy
│   │   ├── display/ availability/         # 점주의 진열·수동 품절 유스케이스와 재고 이벤트 반영 + command/
│   │   ├── storeproduct/                  # 매장 상품 목록 조회(StoreProductQueryService, StoreProductView)
│   │   └── scope/                         # StoreScopeCleanupHandler (ProductStoreScopeChanged 구독)
│   ├── infrastructure/                    # display/ availability/ (+ messaging: 재고 이벤트 구독, 5단계)
│   └── presentation/                      # (4단계)
│
├── schedule/                              # 예약 변경 (요구사항 1.4)
│   ├── domain/                            # ScheduledChange, ScheduledValue, TargetKind, ScheduleStatus, Repository + exception/
│   ├── application/                       # ScheduledChangeApplicationService(등록·취소·조회), ScheduledChangeApplier,
│   │                                      #   ScheduledChangeApplicationBatch, ScheduledValueValidator(등록 시점 검증),
│   │                                      #   값 타입(ScheduledFieldValue 등) + command/
│   └── infrastructure/                    # ScheduledChangesTable, ExposedScheduledChangeRepository, ScheduledValueJsonbCodec
│
└── exposure/                              # 노출 현황 조회 (요구사항 1.10)
    ├── application/                       # ProductExposureQueryService, ProductExposureFilter + 결과 View
    │   └── port/                          # ProductExposureQueryPort(조회), StoreDirectoryPort(전체 매장, 외부)
    └── infrastructure/
        ├── query/                         # ExposedProductExposureQuery (여러 테이블을 직접 조회)
        └── acl/                           # ConfiguredStoreDirectory (Store BC 연동 전 임시 구현)
```

테스트도 같은 트리를 따른다. 다만 여러 모듈의 테스트가 함께 쓰는 `fixture/`와 `support/`는 테스트 소스 최상위에 둔다. Exposed `Table` 정의 목록(`support/ExposedTables`)도 스키마 검사 테스트만 쓰므로 테스트 소스에 있다.

## 설계 메모

- **모듈 경계는 의존이 성긴 곳에 긋는다.** 애그리거트마다 모듈을 만드는 안은 Product와 OptionGroup처럼 한 트랜잭션에서 함께 바뀌는 사이를 갈라 모듈 간 의존을 촘촘하게 만든다([ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md)).
- **애그리거트 간 협력은 application에서**: `product`의 판단에 옵션 그룹의 옵션이 필요하거나 `store`의 판단에 상품 상태가 필요할 때 별도 포트를 만들지 않는다. application이 각 Repository로 불러와 정책에 넘기거나, 애그리거트 메서드에 ID나 값(예: 옵션 그룹의 옵션 키 목록)으로 넘긴다. 이벤트로 처리할 때는 `core.DomainEvent`를 쓴다.
- **포트는 application에**: 외부 시스템 포트와 조회 포트는 이를 쓰는 application이 정의하고 infrastructure가 구현한다. domain은 Repository 외의 outbound 인터페이스를 두지 않는다.
- **exposure**는 쓰기 로직이 없는 순수 조회 모듈이라 domain 없이 application(조회 포트)+infrastructure만 존재한다.
- 예외 클래스와 `ErrorCode` 배치 규칙은 [예외 구조](exception.md)를 따른다.
