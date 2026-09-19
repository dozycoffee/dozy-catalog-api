package com.dozycoffee.catalog.domain.storeavailability

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.storeavailability.exception.InventoryEventNotApplicableException
import com.dozycoffee.catalog.domain.storeavailability.exception.StockStatusNotManuallyEditableException
import com.dozycoffee.catalog.fixture.inventoryAvailability
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("StoreProductAvailability")
class StoreProductAvailabilityTest {
    private val id = StoreProductAvailabilityId(StoreId(1), ProductId(1))
    private val t0 = Instant.parse("2026-09-18T00:00:00Z")

    @Nested
    @DisplayName("출처별 기본값")
    inner class Initial {
        @Test
        fun `재고 추적 상품은 처음 재고 0이라 품절로 시작한다`() {
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.INVENTORY)

            assertEquals(StockStatus.SOLD_OUT, availability.stockStatus)
            assertNull(availability.lastEventAt)
        }

        @Test
        fun `재고 미추적 상품은 판매중으로 시작한다`() {
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.OWNER)

            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
        }

        @Test
        fun `출처는 상품의 재고 추적 여부로 정해진다`() {
            assertEquals(AvailabilitySource.INVENTORY, AvailabilitySource.of(tracksInventory = true))
            assertEquals(AvailabilitySource.OWNER, AvailabilitySource.of(tracksInventory = false))
        }
    }

    @Nested
    @DisplayName("점주의 수동 품절")
    inner class ByOwner {
        @Test
        fun `재고 미추적 상품은 점주가 품절을 설정하고 해제할 수 있다`() {
            // 재료 소진 등의 이유로 점주가 직접 설정/해제한다(요구사항 2.5).
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.OWNER)

            availability.changeByOwner(StockStatus.SOLD_OUT)
            assertEquals(StockStatus.SOLD_OUT, availability.stockStatus)

            availability.changeByOwner(StockStatus.ON_SALE)
            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
        }

        @Test
        fun `재고 추적 상품의 품절은 점주가 바꿀 수 없고 상태도 그대로다`() {
            // 재고 추적 상품의 품절은 재고관리 서비스 이벤트로만 전환된다(요구사항 2.5).
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.INVENTORY)

            assertFailsWith<StockStatusNotManuallyEditableException> {
                availability.changeByOwner(StockStatus.ON_SALE)
            }
            assertEquals(StockStatus.SOLD_OUT, availability.stockStatus)
        }
    }

    @Nested
    @DisplayName("재고 이벤트 반영")
    inner class InventoryEvent {
        @Test
        fun `재고가 생기면 품절이 해제되고 소진되면 다시 품절이 된다`() {
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.INVENTORY)

            assertTrue(availability.applyInventoryEvent(StockStatus.ON_SALE, t0))
            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
            assertEquals(t0, availability.lastEventAt)

            assertTrue(availability.applyInventoryEvent(StockStatus.SOLD_OUT, t0.plusSeconds(60)))
            assertEquals(StockStatus.SOLD_OUT, availability.stockStatus)
        }

        @Test
        fun `이미 반영한 것보다 오래된 이벤트는 무시한다`() {
            // 순서가 뒤바뀌어 늦게 도착한 이벤트가 최신 값을 덮어쓰면 안 된다.
            val availability = inventoryAvailability(stockStatus = StockStatus.ON_SALE, occurredAt = t0)

            assertFalse(availability.applyInventoryEvent(StockStatus.SOLD_OUT, t0.minusSeconds(60)))
            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
            assertEquals(t0, availability.lastEventAt)
        }

        @Test
        fun `같은 시각의 이벤트는 중복 수신으로 보고 무시한다`() {
            val availability = inventoryAvailability(stockStatus = StockStatus.ON_SALE, occurredAt = t0)

            assertFalse(availability.applyInventoryEvent(StockStatus.SOLD_OUT, t0))
            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
        }

        @Test
        fun `재고 미추적 상품에는 재고 이벤트를 반영할 수 없다`() {
            val availability = StoreProductAvailability.initial(id, AvailabilitySource.OWNER)

            assertFailsWith<InventoryEventNotApplicableException> {
                availability.applyInventoryEvent(StockStatus.SOLD_OUT, t0)
            }
            assertEquals(StockStatus.ON_SALE, availability.stockStatus)
        }
    }
}
