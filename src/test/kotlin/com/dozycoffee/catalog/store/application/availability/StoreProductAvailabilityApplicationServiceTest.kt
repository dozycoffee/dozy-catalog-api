package com.dozycoffee.catalog.store.application.availability

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.availability.command.ApplyInventoryEventCommand
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.availability.exception.StockStatusNotManuallyEditableException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("S6·S7. 판매 가능 여부 변경 (요구사항 2.4, 2.5)")
class StoreProductAvailabilityApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: StoreProductAvailabilityApplicationService

    @Autowired
    private lateinit var availabilityRepository: StoreProductAvailabilityRepository

    private val gangnam = StoreId(10)
    private val americano = ProductId(1) // 재고 미추적 상품
    private val tumbler = ProductId(2) // 재고 추적 상품
    private val stockedAt = Instant.parse("2026-09-18T01:00:00Z")

    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute(
                "INSERT INTO products (name, category_id, base_price, status, tracks_inventory) " +
                    "VALUES ('아메리카노', 2, 4500, 'ACTIVE', false)",
            )
            execute(
                "INSERT INTO products (name, category_id, base_price, status, tracks_inventory) " +
                    "VALUES ('텀블러', 2, 30000, 'ACTIVE', true)",
            )
        }

    @Nested
    @DisplayName("점주의 수동 품절 (S6)")
    inner class ByOwner {
        @Test
        fun `재고 미추적 상품은 점주가 품절로 바꾸고 다시 해제할 수 있다`() =
            runTest {
                service.changeStockStatusByOwner(
                    ChangeStockStatusByOwnerCommand(gangnam, americano, StockStatus.SOLD_OUT),
                )
                assertEquals(StockStatus.SOLD_OUT, stored(americano)?.stockStatus)

                service.changeStockStatusByOwner(
                    ChangeStockStatusByOwnerCommand(gangnam, americano, StockStatus.ON_SALE),
                )

                assertEquals(StockStatus.ON_SALE, stored(americano)?.stockStatus)
            }

        @Test
        fun `첫 수동 품절 시점에 OWNER 출처로 만들어진다`() =
            runTest {
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))

                service.changeStockStatusByOwner(
                    ChangeStockStatusByOwnerCommand(gangnam, americano, StockStatus.SOLD_OUT),
                )

                val availability = assertNotNull(stored(americano))
                assertEquals(AvailabilitySource.OWNER, availability.source)
                assertNull(availability.lastEventAt)
            }

        @Test
        fun `재고 추적 상품을 수동으로 품절 처리하려 하면 거부하고 아무 행도 만들지 않는다`() =
            runTest {
                assertFailsWith<StockStatusNotManuallyEditableException> {
                    service.changeStockStatusByOwner(
                        ChangeStockStatusByOwnerCommand(gangnam, tumbler, StockStatus.SOLD_OUT),
                    )
                }

                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `재고 이벤트로 만들어진 판매 가능 여부도 점주가 바꿀 수 없다`() =
            runTest {
                service.applyInventoryEvent(
                    ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt),
                )

                assertFailsWith<StockStatusNotManuallyEditableException> {
                    service.changeStockStatusByOwner(
                        ChangeStockStatusByOwnerCommand(gangnam, tumbler, StockStatus.SOLD_OUT),
                    )
                }

                assertEquals(StockStatus.ON_SALE, stored(tumbler)?.stockStatus)
            }

        @Test
        fun `없는 상품이면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.changeStockStatusByOwner(
                        ChangeStockStatusByOwnerCommand(gangnam, ProductId(999), StockStatus.SOLD_OUT),
                    )
                }
            }
    }

    @Nested
    @DisplayName("재고 이벤트 반영 (S7)")
    inner class InventoryEvent {
        @Test
        fun `재고 있음 이벤트를 받으면 INVENTORY 출처로 만들고 판매중으로 바꾼다`() =
            runTest {
                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt),
                    )

                assertTrue(applied)
                val availability = assertNotNull(stored(tumbler))
                assertEquals(AvailabilitySource.INVENTORY, availability.source)
                assertEquals(StockStatus.ON_SALE, availability.stockStatus)
                assertEquals(stockedAt, availability.lastEventAt)
            }

        @Test
        fun `재고 없음 이벤트를 받으면 품절로 바꾼다`() =
            runTest {
                service.applyInventoryEvent(ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt))

                service.applyInventoryEvent(
                    ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.SOLD_OUT, stockedAt.plusSeconds(60)),
                )

                assertEquals(StockStatus.SOLD_OUT, stored(tumbler)?.stockStatus)
            }

        @Test
        fun `이미 반영한 것보다 오래된 이벤트는 무시한다`() =
            runTest {
                service.applyInventoryEvent(ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt))

                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.SOLD_OUT, stockedAt.minusSeconds(60)),
                    )

                assertFalse(applied)
                assertEquals(StockStatus.ON_SALE, stored(tumbler)?.stockStatus)
            }

        @Test
        fun `같은 시각의 이벤트가 중복 도착하면 무시한다`() =
            runTest {
                service.applyInventoryEvent(ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt))

                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.SOLD_OUT, stockedAt),
                    )

                assertFalse(applied)
                assertEquals(StockStatus.ON_SALE, stored(tumbler)?.stockStatus)
            }

        @Test
        fun `Catalog에 없는 상품의 이벤트는 기록만 하고 무시한다`() =
            runTest {
                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, ProductId(999), StockStatus.ON_SALE, stockedAt),
                    )

                assertFalse(applied)
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `재고 미추적 상품의 이벤트는 기록만 하고 무시한다`() =
            runTest {
                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, americano, StockStatus.SOLD_OUT, stockedAt),
                    )

                assertFalse(applied)
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }

        @Test
        fun `상품이 단종 중이거나 매장이 판매 범위 밖이어도 반영한다`() =
            runTest {
                execute("UPDATE products SET status = 'DISCONTINUED', store_scope = 'LIMITED' WHERE id = ${tumbler.value}")

                val applied =
                    service.applyInventoryEvent(
                        ApplyInventoryEventCommand(gangnam, tumbler, StockStatus.ON_SALE, stockedAt),
                    )

                assertTrue(applied)
                assertEquals(StockStatus.ON_SALE, stored(tumbler)?.stockStatus)
            }
    }

    private suspend fun stored(productId: ProductId) =
        tx { availabilityRepository.findById(StoreProductAvailabilityId(gangnam, productId)) }
}
