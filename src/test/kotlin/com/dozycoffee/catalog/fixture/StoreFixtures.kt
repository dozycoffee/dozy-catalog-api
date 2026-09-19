package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.shared.StoreId
import com.dozycoffee.catalog.domain.storeavailability.AvailabilitySource
import com.dozycoffee.catalog.domain.storeavailability.StockStatus
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailability
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailabilityId
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySetting
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySettingId
import com.dozycoffee.catalog.domain.storedisplay.model.Visibility
import java.time.Instant

// 매장·상품 ID는 타입으로 받는다. 이 픽스처를 쓰는 테스트가 StoreScope와 결과 ID를 타입으로 다루기 때문이다.
// 기본값이 아닌 상태는 생성자에 값을 직접 넣지 않고 애그리거트 자신의 메서드로 만든다.
// 재고 이벤트 시각(lastEventAt)처럼 상태와 함께 바뀌어야 하는 값이 어긋나지 않게 하기 위해서다.

fun displaySetting(
    id: Long = 1,
    storeId: StoreId = StoreId(1),
    productId: ProductId = ProductId(1),
    visibility: Visibility = Visibility.VISIBLE,
) = StoreDisplaySetting(StoreDisplaySettingId(id), storeId, productId, visibility = visibility)

// stockStatus가 null이면 처음 만든 상태(판매중) 그대로다.
fun ownerAvailability(
    storeId: StoreId = StoreId(1),
    productId: ProductId = ProductId(1),
    stockStatus: StockStatus? = null,
) = StoreProductAvailability.initial(StoreProductAvailabilityId(storeId, productId), AvailabilitySource.OWNER).also {
    if (stockStatus != null) it.changeByOwner(stockStatus)
}

// stockStatus가 null이면 재고 이벤트를 받은 적 없는 상태(품절, lastEventAt 없음)다.
fun inventoryAvailability(
    storeId: StoreId = StoreId(1),
    productId: ProductId = ProductId(1),
    stockStatus: StockStatus? = null,
    occurredAt: Instant = Instant.parse("2026-09-18T00:00:00Z"),
) = StoreProductAvailability.initial(StoreProductAvailabilityId(storeId, productId), AvailabilitySource.INVENTORY).also {
    if (stockStatus != null) it.applyInventoryEvent(stockStatus, occurredAt)
}
