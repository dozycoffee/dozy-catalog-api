package com.dozycoffee.catalog.domain.storeproductlisting.model

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.storeproductlisting.exception.StockStatusNotManuallyEditableException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("StoreProductListing")
class StoreProductListingTest {
    @Test
    fun `개별 설정을 처음 만들면 노출 판매중 상태로 시작한다`() {
        val listing = listing()

        assertEquals(Visibility.VISIBLE, listing.visibility)
        assertEquals(StockStatus.ON_SALE, listing.stockStatus)
        assertNull(listing.displayOrder)
    }

    @Nested
    @DisplayName("점주의 노출 조정")
    inner class OwnerVisibility {
        @Test
        fun `상품을 숨겼다가 다시 노출할 수 있다`() {
            val listing = listing()

            listing.hide()
            assertEquals(Visibility.HIDDEN, listing.visibility)

            listing.show()
            assertEquals(Visibility.VISIBLE, listing.visibility)
        }

        @Test
        fun `진열 순서를 조정한다`() {
            val listing = listing()

            listing.changeDisplayOrder(3)

            assertEquals(3, listing.displayOrder)
        }
    }

    @Nested
    @DisplayName("점주의 품절 토글")
    inner class OwnerStockStatus {
        @Test
        fun `재고 미추적 상품은 점주가 직접 품절 처리할 수 있다`() {
            // 재료 소진 등의 이유로 점주가 직접 설정/해제한다(2.5).
            val listing = listing()

            listing.changeStockStatusByOwner(StockStatus.SOLD_OUT, tracksInventory = false)

            assertEquals(StockStatus.SOLD_OUT, listing.stockStatus)
        }

        @Test
        fun `재고 추적 상품의 품절 상태는 점주가 변경할 수 없다`() {
            // 재고 추적 상품의 품절은 재고관리 서비스 이벤트로만 전환된다(2.5).
            val listing = listing()

            assertFailsWith<StockStatusNotManuallyEditableException> {
                listing.changeStockStatusByOwner(StockStatus.SOLD_OUT, tracksInventory = true)
            }
        }

        @Test
        fun `거부된 품절 요청은 상태를 바꾸지 않는다`() {
            val listing = listing()

            runCatching { listing.changeStockStatusByOwner(StockStatus.SOLD_OUT, tracksInventory = true) }

            assertEquals(StockStatus.ON_SALE, listing.stockStatus)
        }
    }

    @Nested
    @DisplayName("재고 서비스 연동")
    inner class InventoryDrivenStockStatus {
        @Test
        fun `재고가 소진되면 품절로 전환된다`() {
            val listing = listing()

            listing.applyInventoryStockStatus(StockStatus.SOLD_OUT)

            assertEquals(StockStatus.SOLD_OUT, listing.stockStatus)
        }

        @Test
        fun `재입고되면 품절이 해제된다`() {
            val listing = listing(stockStatus = StockStatus.SOLD_OUT)

            listing.applyInventoryStockStatus(StockStatus.ON_SALE)

            assertEquals(StockStatus.ON_SALE, listing.stockStatus)
        }
    }

    @Test
    fun `노출 여부와 품절 상태는 서로 독립적이다`() {
        // 품절이어도 점주가 숨기지 않는 한 노출은 유지된다(2.5).
        val listing = listing()

        listing.applyInventoryStockStatus(StockStatus.SOLD_OUT)

        assertEquals(Visibility.VISIBLE, listing.visibility)
        assertEquals(StockStatus.SOLD_OUT, listing.stockStatus)
    }

    private fun listing(
        visibility: Visibility = Visibility.VISIBLE,
        stockStatus: StockStatus = StockStatus.ON_SALE,
    ) = StoreProductListing(
        id = StoreProductListingId(1),
        storeId = StoreId(1),
        productId = ProductId(1),
        visibility = visibility,
        stockStatus = stockStatus,
    )
}
