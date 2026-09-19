# 패키지 구조 (레이어 우선)

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를, 의존 방향 원칙은 [아키텍처 개요](README.md)를 참고한다. 레이어 우선 구조의 근거는 [ADR-0001](../adr/0001-layered-packages-ports-for-external-only.md)에, 애그리거트 간 참조 규칙과 Repository·포트 배치의 근거는 [ADR-0012](../adr/0012-cross-aggregate-judgment-in-application-policy.md)에 있다.

base package: `com.dozycoffee.catalog`

## 원칙

1. **레이어 우선, 애그리거트는 그 안에서 분리한다.** 최상위 패키지는 `domain` / `application` / `infrastructure` / `presentation`이고, 그 아래에 애그리거트별 서브패키지(`product`, `optiongroup`, ...)를 둔다.
   → catalog-service 자체가 이미 하나의 BC(=마이크로서비스 배포 단위)이므로, 내부 애그리거트마다 포트/어댑터를 강제해 억지로 격리하지 않는다.
2. **domain 애그리거트끼리는 ID로만 참조한다.** domain의 각 애그리거트 패키지는 자기 자신, `domain.shared`, 다른 애그리거트의 ID(`*Id`, `OptionKey`)만 참조한다. 여러 애그리거트를 함께 보는 판단은 `application/<module>/policy`의 I/O 없는 정책 클래스에 두고, 조회·잠금·저장 순서와 트랜잭션은 application 서비스가 맡는다. 애그리거트 간 협력은 application에서 직접 호출하거나 도메인 이벤트로 처리한다.
3. **Repository는 domain, 포트는 application에 둔다.** 배치는 아래 [인터페이스와 구현 배치](#인터페이스와-구현-배치)를 따른다. 같은 서비스 안의 애그리거트끼리는 포트로 감싸지 않는다.
4. **서브패키지 규칙**: 애그리거트 폴더 안 파일이 6~7개를 넘거나, 역할 종류(model/event/exception 등)가 3가지 이상 섞이면 역할별 서브패키지로 나눈다. 그 미만이면 평평하게 유지해 불필요한 클릭 depth를 늘리지 않는다. Repository 인터페이스는 서브패키지 도입 여부와 무관하게 애그리거트 최상위에 둔다 (model과 짝을 이루는 존재라 바로 보이는 게 낫기 때문).
5. **DTO 정책**: Request/Response DTO는 `presentation/dto`에서만 사용한다. `application` 레이어 경계에서 Command/Query 객체로 변환한다. Exposed `Table` 객체와 row 매핑 로직은 `infrastructure/persistence` 내부에서만 쓰고 domain 모델과 분리한다 (JPA Entity가 아니라 Exposed DSL 기반).

## 인터페이스와 구현 배치

| 종류 | 인터페이스 위치 | 구현 (infrastructure) | 구현이 쓰는 것 |
|---|---|---|---|
| Repository | `domain.<module>.<Module>Repository` | `persistence.<module>.Exposed<Module>Repository` | 자기 애그리거트 테이블만 |
| 외부 시스템 포트 | `application.<module>.port` | `acl.…Adapter` | Store BC 등의 Client(번역 포함) |
| 조회 포트 (여러 애그리거트에 걸친 읽기) | `application.<module>.port` | `persistence.query.…` | Exposed로 테이블 직접 조회 |

- Repository는 자기 애그리거트만 다룬다. 자기 테이블에 대한 단순 존재 조회(예: `ProductRepository.existsByCategory`)는 그 애그리거트의 Repository에 둔다. 다른 애그리거트의 데이터가 필요하면 application이 그 애그리거트의 Repository나 조회 포트를 호출한다.
- 포트 구현체는 domain Repository를 호출해 조합하지 않는다. 무엇을 불러와 어떻게 합칠지는 오케스트레이션이므로 application에 둔다.

### 외부 호출 지점

| 연동 대상 | 방향 | 포트 위치 | 구현 위치 |
|---|---|---|---|
| Store BC — 매장 존재 검증 | outbound | `application.product.port.ValidateStoreExistsPort` | `infrastructure.acl.StoreBcAdapter`(포트 구현) + `StoreBcClient`(호출) |
| 재고관리 서비스 — 입고/품절/재입고 이벤트 구독 | inbound | (application/eventhandler에서 직접 처리) | `infrastructure.messaging.InventoryEventConsumer` + `infrastructure.acl.InventoryServiceEventTranslator` |
| POS/정산 등 — 상품 상태·정보 변경 이벤트 발행 | outbound | `application.product.port.ProductEventPublisherPort` | `infrastructure.eventing.DomainEventPublisher` |

## 전체 트리

목표 구조다. 아직 구현되지 않은 패키지(application, infrastructure, presentation 대부분)도 포함한다.

```
com.dozycoffee.catalog
├── domain/
│   ├── product/
│   │   ├── model/
│   │   │   ├── Product.kt                       # Aggregate Root
│   │   │   ├── ProductOptionGroupLink.kt         # 내부 엔티티
│   │   │   ├── OptionOverride.kt                 # VO
│   │   │   ├── ProductId.kt / Sku.kt             # VO (value class)
│   │   │   └── ProductStatus.kt / StoreScope.kt  # enum/sealed class (StoreId는 shared)
│   │   ├── event/
│   │   │   ├── ProductActivated.kt
│   │   │   ├── ProductDiscontinued.kt
│   │   │   └── ProductStoreScopeChanged.kt
│   │   ├── exception/
│   │   │   ├── InvalidProductStatusTransitionException.kt
│   │   │   └── InvalidTargetStoreException.kt
│   │   └── ProductRepository.kt                 # Repository는 최상위에 (model과 짝이라 바로 보이게)
│   │
│   ├── optiongroup/                              # 평평 (파일 5개, 기준 미달)
│   │   ├── OptionGroup.kt
│   │   ├── Option.kt
│   │   ├── OptionListSnapshot.kt
│   │   ├── OptionGroupRepository.kt
│   │   └── exception/EmptyOptionGroupException.kt
│   │
│   ├── category/                                 # 평평 (파일 3개)
│   │   ├── Category.kt
│   │   ├── CategoryRepository.kt
│   │   └── exception/CategoryStillReferencedException.kt
│   │
│   ├── tag/                                       # 평평 (파일 3개)
│   │   ├── Tag.kt
│   │   ├── TagRepository.kt                      # findOrCreateByName (이름으로 재사용)
│   │   └── event/TagDeleted.kt
│   │
│   ├── productgroup/                              # 평평 (파일 3개)
│   │   ├── ProductGroup.kt
│   │   ├── ProductGroupRepository.kt
│   │   └── event/ProductGroupDeleted.kt
│   │
│   ├── scheduledchange/                           # 평평 (파일 2개)
│   │   ├── ScheduledChange.kt
│   │   └── ScheduledChangeRepository.kt
│   │
│   ├── storedisplay/                              # 점주의 매장별 진열 설정
│   │   ├── model/
│   │   │   ├── StoreDisplaySetting.kt            # Aggregate Root
│   │   │   ├── StoreDisplaySettingId.kt
│   │   │   └── Visibility.kt
│   │   └── StoreDisplaySettingRepository.kt
│   │
│   ├── storeavailability/                         # 매장별 판매 가능 여부 (평평 + exception/)
│   │   ├── StoreProductAvailability.kt           # Aggregate Root
│   │   ├── StoreProductAvailabilityId.kt         # (storeId, productId) 복합 식별자
│   │   ├── AvailabilitySource.kt                 # INVENTORY / OWNER
│   │   ├── StockStatus.kt
│   │   ├── StoreProductAvailabilityRepository.kt
│   │   └── exception/
│   │
│   └── shared/
│       ├── AggregateRoot.kt / Entity.kt
│       ├── DomainEvent.kt
│       ├── DomainException.kt                    # 모든 도메인 예외의 부모 (errorCode 보유)
│       ├── ErrorCode.kt                          # 인터페이스 — 애그리거트별 enum이 구현
│       ├── ErrorType.kt                          # 에러 성격 분류 (HTTP 무관)
│       ├── SharedErrorCode.kt
│       ├── InvalidMoneyAmountException.kt
│       ├── Money.kt
│       └── StoreId.kt                            # Store BC 참조 (여러 애그리거트가 씀)
│
├── application/
│   ├── shared/
│   │   ├── TransactionRunner.kt
│   │   └── BusinessTimeZone.kt
│   │
│   ├── product/
│   │   ├── ProductApplicationService.kt
│   │   ├── policy/                              # 여러 애그리거트를 보는 판단 (I/O 없는 순수 클래스)
│   │   │   ├── EffectiveOptionResolver.kt        # 유효 옵션 구성·표시용 시작가 계산 (Product·OptionGroup)
│   │   │   ├── EffectiveOptionConfig.kt          # 계산 결과 (그룹·옵션·자동 선택)
│   │   │   └── OptionReplacementPolicy.kt        # 옵션 목록 교체 판단 (연결 상품 검증 + 사라지는 옵션 키)
│   │   ├── command/
│   │   │   ├── RegisterProductCommand.kt
│   │   │   ├── ReplaceProductCommand.kt
│   │   │   ├── ChangeStoreScopeCommand.kt
│   │   │   ├── ActivateProductCommand.kt
│   │   │   └── DiscontinueProductCommand.kt
│   │   └── port/
│   │       ├── ValidateStoreExistsPort.kt
│   │       └── ProductEventPublisherPort.kt
│   │
│   ├── optiongroup/
│   │   ├── OptionGroupApplicationService.kt
│   │   └── command/ ...
│   │
│   ├── category/ tag/ productgroup/  (ApplicationService + command/)
│   │
│   ├── scheduledchange/
│   │   └── ScheduledChangeBatchApplicationService.kt
│   │
│   ├── storedisplay/
│   │   ├── StoreDisplaySettingApplicationService.kt
│   │   ├── policy/
│   │   │   ├── ProductVisibilityPolicy.kt        # 매장별 노출 판단 (Product·진열 설정·판매 가능 여부)
│   │   │   ├── StoreVisibility.kt                # 노출 판단 결과
│   │   │   └── StoreScopeCleanupPolicy.kt        # 판매 범위에서 빠진 매장의 정리 대상 선택
│   │   └── command/ ...
│   │
│   ├── storeavailability/
│   │   ├── StoreProductAvailabilityApplicationService.kt   # 점주 수동 품절 + 재고 이벤트 반영
│   │   └── command/ ...
│   │
│   ├── eventhandler/
│   │   ├── ProductDiscontinuedEventHandler.kt
│   │   ├── ProductActivatedEventHandler.kt
│   │   ├── ProductStoreScopeChangedEventHandler.kt
│   │   ├── TagDeletedEventHandler.kt
│   │   └── ProductGroupDeletedEventHandler.kt
│   │
│   └── exposure/
│       ├── ExposureQueryService.kt
│       └── query/GetExposureSummaryQuery.kt
│
├── infrastructure/
│   ├── persistence/
│   │   ├── product/ ├── optiongroup/ ├── category/ ├── tag/ ├── productgroup/
│   │   ├── scheduledchange/
│   │   ├── storedisplay/ ├── storeavailability/
│   │   ├── (각 모듈)                             # Exposed Table 객체 + row 매핑 + Repository 구현체 (자기 테이블만)
│   │   └── query/                                # 조회 포트 구현 (여러 테이블을 Exposed로 직접 조회)
│   ├── acl/
│   │   ├── StoreBcAdapter.kt                     # ValidateStoreExistsPort 구현 (StoreBcClient 사용, 번역)
│   │   ├── StoreBcClient.kt                      # Store BC 호출
│   │   └── InventoryServiceEventTranslator.kt
│   ├── eventing/
│   │   └── DomainEventPublisher.kt
│   ├── messaging/
│   │   └── InventoryEventConsumer.kt
│   ├── scheduler/
│   │   └── ScheduledChangeBatchTrigger.kt
│   └── config/
│       └── BeanConfiguration.kt
│
└── presentation/
    ├── admin/
    │   ├── ProductController.kt
    │   ├── OptionGroupController.kt
    │   ├── CategoryController.kt / TagController.kt / ProductGroupController.kt
    │   ├── ScheduledChangeController.kt
    │   └── ExposureController.kt
    ├── franchisee/
    │   └── StoreProductController.kt              # 진열 설정·수동 품절
    └── dto/
```

## 설계 메모

- **애그리거트 간 협력은 application에서**: `product`의 판단에 `optiongroup`의 옵션이 필요하거나, `storedisplay`의 판단에 `product`의 상태가 필요할 때 별도 포트를 만들지 않는다. application이 각 Repository로 불러와 정책에 넘기거나, 애그리거트 메서드에 ID나 값(예: 옵션 그룹의 옵션 키 목록)으로 넘긴다. 이벤트로 처리할 때는 `domain.shared.DomainEvent`를 쓴다.
- **포트는 application에**: 외부 시스템 포트와 조회 포트는 이를 쓰는 application이 정의하고 infrastructure가 구현한다. domain은 Repository 외의 outbound 인터페이스를 두지 않는다. domain 로직은 I/O를 하지 않으므로 domain에 둘 근거가 없다.
- **exposure**는 쓰기 로직이 없는 순수 조회 모듈이라 domain 레이어 없이 application(query, 조회 포트)+infrastructure(persistence/query)만 존재.
- 예외 클래스와 `ErrorCode` 배치 규칙은 [예외 구조](exception.md)를 따른다.
