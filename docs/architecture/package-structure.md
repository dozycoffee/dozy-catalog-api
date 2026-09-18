# 패키지 구조 (레이어 우선)

> 문서별 역할과 수정 순서는 [문서 안내](../README.md)를, 의존 방향 원칙은 [아키텍처 개요](README.md)를 참고한다.

base package: `com.dozycoffee.catalog`

## 원칙

1. **레이어 우선, 애그리거트는 그 안에서 분리한다.** 최상위 패키지는 `domain` / `application` / `infrastructure` / `presentation`이고, 그 아래에 애그리거트별 서브패키지(`product`, `optiongroup`, ...)를 둔다.
   → catalog-service 자체가 이미 하나의 BC(=마이크로서비스 배포 단위)이므로, 내부 애그리거트마다 포트/어댑터를 강제해 억지로 격리하지 않는다. 애그리거트 간 협력은 같은 레이어 안에서 직접 호출하거나 도메인 이벤트로 처리한다.
2. **포트 인터페이스는 "진짜 외부 시스템"에만 쓴다.** Store BC REST 호출, 재고관리 서비스 이벤트 구독, POS/정산 이벤트 발행처럼 실제로 다른 배포 단위·네트워크 경계를 넘는 연동만 `application/*/port`로 인터페이스화하고 `infrastructure/acl`, `infrastructure/messaging`에서 구현한다. 같은 서비스 안의 애그리거트끼리는 포트로 감싸지 않는다.
3. **서브패키지 규칙**: 애그리거트 폴더 안 파일이 6~7개를 넘거나, 역할 종류(model/service/event/exception)가 3가지 이상 섞이면 역할별 서브패키지로 나눈다. 그 미만이면 평평하게 유지해 불필요한 클릭 depth를 늘리지 않는다. Repository 인터페이스는 서브패키지 도입 여부와 무관하게 애그리거트 최상위에 둔다 (model과 짝을 이루는 존재라 바로 보이는 게 낫기 때문).
4. **DTO 정책**: Request/Response DTO는 `presentation/dto`에서만 사용한다. `application` 레이어 경계에서 Command/Query 객체로 변환한다. Exposed `Table` 객체와 row 매핑 로직은 `infrastructure/persistence` 내부에서만 쓰고 domain 모델과 분리한다 (JPA Entity가 아니라 Exposed DSL 기반).

## 외부 호출 지점 (포트로 인터페이스화 대상)

| 연동 대상 | 방향 | 포트 위치 | 어댑터 위치 |
|---|---|---|---|
| Store BC — 매장 존재/진열 자격 검증 | outbound | `application.product.port.ValidateStoreExistsPort` | `infrastructure.acl.StoreBcClient` |
| 재고관리 서비스 — 입고/품절/재입고 이벤트 구독 | inbound | (application/eventhandler에서 직접 처리) | `infrastructure.messaging.InventoryEventConsumer` + `infrastructure.acl.InventoryServiceEventTranslator` |
| POS/정산 등 — 상품 상태·정보 변경 이벤트 발행 | outbound | `application.product.port.ProductEventPublisherPort` | `infrastructure.eventing.DomainEventPublisher` |
| DB(Exposed/R2DBC) — 모든 애그리거트 공통 | outbound | `domain.<module>.<Module>Repository` | `infrastructure.persistence.<module>.Exposed<Module>RepositoryImpl` |

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
│   │   │   └── ProductStatus.kt / StoreScope.kt  # enum/sealed class
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
│   ├── tag/                                       # 평평 (파일 4개)
│   │   ├── Tag.kt
│   │   ├── TagRepository.kt
│   │   ├── TagRegistrar.kt                       # domain service (Repository 사용)
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
│   │   │   ├── Visibility.kt
│   │   │   └── StoreVisibility.kt                # 노출 판단 결과
│   │   ├── service/
│   │   │   └── ProductVisibilityPolicy.kt        # 순수 도메인 서비스 (Product·진열 설정·판매 가능 여부를 함께 봄)
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
│       └── Money.kt
│
├── application/
│   ├── product/
│   │   ├── ProductApplicationService.kt
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
│   │   └── (각 모듈)                             # Exposed Table 객체 + row 매핑 + Repository 구현체
│   ├── acl/
│   │   ├── StoreBcClient.kt                      # ValidateStoreExistsPort + StoreEligibilityChecker 구현
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

- **애그리거트 간 협력은 직접 호출/도메인 이벤트로**: `product`가 `optiongroup`을 참조하거나, `storedisplay`가 `product`의 상태를 읽어야 할 때 별도 포트를 만들지 않는다. 같은 서비스·같은 트랜잭션 경계 안이므로 `application` 레이어에서 서로의 Repository/ApplicationService를 직접 호출하거나, `domain.shared.DomainEvent`를 통해 이벤트로 처리한다.
- **외부 시스템 연동만 포트화**: Store BC(매장 존재/자격 검증), 재고관리 서비스(이벤트 구독), POS/정산(이벤트 발행)처럼 네트워크 경계를 넘는 지점만 `application/*/port` 인터페이스로 감싸고 `infrastructure/acl`, `infrastructure/messaging`, `infrastructure/eventing`에서 구현한다. domain 레이어에는 외부 연동 인터페이스를 두지 않는다 — 도메인 로직이 직접 호출하지 않는 한 domain에 둘 근거가 없고, 같은 외부 BC 호출이 레이어별로 흩어지는 것을 막기 위함.
- **exposure**는 쓰기 로직이 없는 순수 조회 모듈이라 domain 레이어 없이 application(query)+infrastructure(persistence)만 존재.
- 예외 클래스와 `ErrorCode` 배치 규칙은 [예외 구조](exception.md)를 따른다.
