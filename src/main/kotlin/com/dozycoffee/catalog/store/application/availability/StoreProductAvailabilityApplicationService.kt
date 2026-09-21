package com.dozycoffee.catalog.store.application.availability

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.availability.command.ApplyInventoryEventCommand
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailability
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// 매장별 판매 가능 여부의 두 가지 변경 경로(요구사항 2.4, 2.5 / 시나리오 S6, S7).
// 점주의 수동 품절은 재고 미추적 상품만, 재고 이벤트 반영은 재고 추적 상품만 가능하며 그 구분은
// 출처(AvailabilitySource)가 지킨다. 출처는 상품의 재고 추적 여부로 처음 만들 때 정해지고 이후 바뀌지 않는다.
@Service
class StoreProductAvailabilityApplicationService(
    private val availabilityRepository: StoreProductAvailabilityRepository,
    private val productRepository: ProductRepository,
    private val transactionRunner: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 점주의 수동 품절 설정·해제. 재고 추적 상품이면 changeByOwner가 거부하므로 아무것도 저장되지 않는다.
    // 이 매장이 판매 범위에 들지 않은 상품은 점주에게 없는 상품이므로 404로 거부한다(요구사항 2.2).
    // 판매 범위에서 빠지면 수동 품절은 지워지므로(요구사항 1.5) 범위 밖에서 새로 만들지 않는다. 상품 상태는 보지 않는다.
    suspend fun changeStockStatusByOwner(command: ChangeStockStatusByOwnerCommand): StoreProductAvailability =
        transactionRunner.inTransaction {
            val product =
                productRepository.findById(command.productId) ?: throw ProductNotFoundException(command.productId)
            if (!product.storeScope.covers(command.storeId)) throw ProductNotFoundException(command.productId)
            val id = StoreProductAvailabilityId(command.storeId, command.productId)
            val availability =
                availabilityRepository.findById(id)
                    ?: StoreProductAvailability.initial(id, AvailabilitySource.of(product.tracksInventory))
            availability.changeByOwner(command.stockStatus)
            availabilityRepository.saveByOwner(availability)
            availability
        }

    // 재고관리 서비스의 재고 변동을 반영한다. 상품 상태(단종 등)와 판매 범위는 보지 않는다 — 재고의 주인은
    // 재고관리 서비스이고, 재판매·재포함 시점에 현재 재고가 이미 반영되어 있어야 하기 때문이다(요구사항 2.4).
    // 실제로 반영했는지를 돌려준다. 오래되었거나 같은 시각의 이벤트, 반영할 수 없는 상품의 이벤트는 false다.
    // 메시징 연동(InventoryEventConsumer)은 5단계이며, 여기서는 호출 가능한 유스케이스까지만 둔다.
    suspend fun applyInventoryEvent(command: ApplyInventoryEventCommand): Boolean =
        transactionRunner.inTransaction {
            val product = productRepository.findById(command.productId)
            if (product == null) {
                // Catalog에 없는 상품의 이벤트는 기록만 하고 무시한다(시나리오 S7 3c).
                log.warn("Catalog에 없는 상품의 재고 이벤트를 무시합니다: {}", command)
                return@inTransaction false
            }
            if (!product.tracksInventory) {
                // 재고 미추적 상품의 판매 가능 여부는 점주가 주인이라 재고 이벤트를 반영하지 않는다.
                log.warn("재고 미추적 상품의 재고 이벤트를 무시합니다: {}", command)
                return@inTransaction false
            }

            val id = StoreProductAvailabilityId(command.storeId, command.productId)
            val availability =
                availabilityRepository.findById(id)
                    ?: StoreProductAvailability.initial(id, AvailabilitySource.INVENTORY)
            // 애그리거트가 먼저 순서를 확인하고, 그 사이 다른 트랜잭션이 더 새 이벤트를 반영했을 수 있어
            // 저장 시점에 DB가 한 번 더 막는다(ERD 동시성 처리).
            if (!availability.applyInventoryEvent(command.stockStatus, command.occurredAt)) {
                log.info("이미 반영한 것보다 오래되었거나 같은 시각의 재고 이벤트를 무시합니다: {}", command)
                return@inTransaction false
            }
            availabilityRepository.saveInventoryEvent(availability)
        }
}
