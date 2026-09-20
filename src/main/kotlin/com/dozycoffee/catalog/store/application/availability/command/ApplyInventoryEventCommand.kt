package com.dozycoffee.catalog.store.application.availability.command

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import java.time.Instant

// 재고관리 서비스의 재고 변동 이벤트(요구사항 2.4 / 시나리오 S7).
// "매장 재고 생김"은 ON_SALE, "매장 재고 없음"은 SOLD_OUT으로 번역해 담는다. 번역은 메시징 연동(5단계)의 책임이다.
// occurredAt은 이벤트가 발생한 시각이며, 이미 반영한 것보다 오래되었거나 같으면 무시하는 기준이 된다.
data class ApplyInventoryEventCommand(
    val storeId: StoreId,
    val productId: ProductId,
    val stockStatus: StockStatus,
    val occurredAt: Instant,
)
