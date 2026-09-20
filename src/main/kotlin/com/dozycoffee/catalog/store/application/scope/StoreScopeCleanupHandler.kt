package com.dozycoffee.catalog.store.application.scope

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.event.DomainEventHandler
import com.dozycoffee.catalog.product.domain.product.event.ProductStoreScopeChanged
import com.dozycoffee.catalog.store.application.policy.StoreScopeCleanupPolicy
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import org.springframework.stereotype.Component

// 판매 범위가 바뀌면 대상에서 빠진 매장의 진열 설정과 점주 소유(OWNER) 판매 가능 여부를 지운다
// (요구사항 1.5, 시나리오 S4). 재고관리 서비스가 주인인 INVENTORY 출처는 판매 범위와 무관하게 남긴다.
//
// 이 상품의 매장 설정을 모두 읽어 정책에 넘기고 고른 ID만 지운다. 어떤 매장이 빠졌는지는 전체 매장
// 목록을 아는 쪽이 없어 이벤트에 담을 수 없으므로, 저장된 설정과 새 판매 범위를 대조해 판단한다.
//
// 발행 측(product)의 트랜잭션 안에서 동기로 실행된다. 정리가 끝나기 전의 중간 상태를 만들지 않기
// 위해서이고, 여기서 예외가 나면 판매 범위 변경도 함께 롤백된다(#65).
// inTransaction은 이미 열린 트랜잭션을 이어 쓴다 — 예약 적용 등 다른 경로에서 불려도 같게 동작한다.
@Component
class StoreScopeCleanupHandler(
    private val displaySettingRepository: StoreDisplaySettingRepository,
    private val availabilityRepository: StoreProductAvailabilityRepository,
    private val transactionRunner: TransactionRunner,
) : DomainEventHandler<ProductStoreScopeChanged> {
    override val eventType: Class<ProductStoreScopeChanged> = ProductStoreScopeChanged::class.java

    override suspend fun handle(event: ProductStoreScopeChanged) {
        transactionRunner.inTransaction {
            val targets =
                StoreScopeCleanupPolicy.selectTargets(
                    productId = event.productId,
                    newScope = event.newScope,
                    displaySettings = displaySettingRepository.findAllByProduct(event.productId),
                    availabilities = availabilityRepository.findAllByProduct(event.productId),
                )
            displaySettingRepository.deleteAll(targets.displaySettingIds)
            availabilityRepository.deleteAll(targets.availabilityIds)
        }
    }
}
