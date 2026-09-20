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
   - `common` — 애그리거트에 속하지 않는 기술 공통(`TransactionRunner`, `BusinessTimeZone`, `exposed/`, `time/`, `web/`). 모듈을 참조하지 않는다.
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

## 인터페이스와 구현 배치

| 종류 | 인터페이스 위치 | 구현 | 구현이 쓰는 것 |
|---|---|---|---|
| Repository | `<모듈>.domain.<애그리거트>.<Aggregate>Repository` | `<모듈>.infrastructure.<애그리거트>.Exposed<Aggregate>Repository` | 자기 애그리거트 테이블만 |
| 외부 시스템 포트 | `<모듈>.application.port` | `<모듈>.infrastructure.acl.…Adapter` | Store BC 등의 Client(번역 포함) |
| 조회 포트 (여러 애그리거트에 걸친 읽기) | `<모듈>.application.port` | `<모듈>.infrastructure.query.…` | Exposed로 테이블 직접 조회 |

- Repository는 자기 애그리거트만 다룬다. 자기 테이블에 대한 단순 존재 조회(예: `ProductRepository.existsByCategory`)는 그 애그리거트의 Repository에 둔다.
- 포트 구현체는 domain Repository를 호출해 조합하지 않는다. 무엇을 불러와 어떻게 합칠지는 오케스트레이션이므로 application에 둔다.

### 외부 호출 지점

| 연동 대상 | 방향 | 포트 위치 | 구현 위치 |
|---|---|---|---|
| Store BC — 매장 존재 검증 | outbound | `product.application.port.ValidateStoreExistsPort` | `product.infrastructure.acl.StoreBcAdapter`(포트 구현) + `StoreBcClient`(호출) |
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
│   ├── exposed/                           # ExposedConfiguration, ExposedTransactionRunner, AuditColumns, JsonbColumnType
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
│   │   ├── command/                       # (3단계)
│   │   └── port/                          # ValidateStoreExistsPort, ProductEventPublisherPort (3단계)
│   ├── infrastructure/                    # 애그리거트별 Exposed Table + Repository 구현
│   │   ├── product/ optiongroup/ category/ tag/ productgroup/
│   │   └── acl/                           # StoreBcAdapter, StoreBcClient (5단계)
│   └── presentation/                      # (4단계)
│
├── store/                                 # 가맹점 진열·판매 가능 여부 (요구사항 2·3장)
│   ├── domain/
│   │   ├── display/                       # StoreDisplaySetting, StoreDisplaySettingId, Visibility, Repository
│   │   └── availability/                  # StoreProductAvailability, AvailabilitySource, StockStatus, Repository + exception/
│   ├── application/
│   │   └── policy/                        # ProductVisibilityPolicy, StoreVisibility, StoreScopeCleanupPolicy
│   ├── infrastructure/                    # display/ availability/ (+ messaging: 재고 이벤트 구독, 5단계)
│   └── presentation/                      # (4단계)
│
├── schedule/                              # 예약 변경 (요구사항 1.4)
│   ├── domain/                            # ScheduledChange, ScheduledValue, TargetKind, ScheduleStatus, Repository + exception/
│   ├── application/                       # ProductFieldValue, OptionGroupFieldValue, ScheduledFieldValue (+ 적용 배치, 3단계)
│   └── infrastructure/                    # ScheduledChangesTable, ExposedScheduledChangeRepository, ScheduledValueJsonbCodec
│
└── exposure/                              # 노출 현황 조회 (요구사항 1.10, 3단계)
    ├── application/                       # 조회 서비스와 조회 포트
    └── infrastructure/                    # 여러 테이블을 직접 조회
```

테스트도 같은 트리를 따른다. 다만 여러 모듈의 테스트가 함께 쓰는 `fixture/`와 `support/`는 테스트 소스 최상위에 둔다. Exposed `Table` 정의 목록(`support/ExposedTables`)도 스키마 검사 테스트만 쓰므로 테스트 소스에 있다.

## 설계 메모

- **모듈 경계는 의존이 성긴 곳에 긋는다.** 애그리거트마다 모듈을 만드는 안은 Product와 OptionGroup처럼 한 트랜잭션에서 함께 바뀌는 사이를 갈라 모듈 간 의존을 촘촘하게 만든다([ADR-0015](../adr/0015-domain-modules-as-top-level-packages.md)).
- **애그리거트 간 협력은 application에서**: `product`의 판단에 옵션 그룹의 옵션이 필요하거나 `store`의 판단에 상품 상태가 필요할 때 별도 포트를 만들지 않는다. application이 각 Repository로 불러와 정책에 넘기거나, 애그리거트 메서드에 ID나 값(예: 옵션 그룹의 옵션 키 목록)으로 넘긴다. 이벤트로 처리할 때는 `core.DomainEvent`를 쓴다.
- **포트는 application에**: 외부 시스템 포트와 조회 포트는 이를 쓰는 application이 정의하고 infrastructure가 구현한다. domain은 Repository 외의 outbound 인터페이스를 두지 않는다.
- **exposure**는 쓰기 로직이 없는 순수 조회 모듈이라 domain 없이 application(조회 포트)+infrastructure만 존재한다.
- 예외 클래스와 `ErrorCode` 배치 규칙은 [예외 구조](exception.md)를 따른다.
