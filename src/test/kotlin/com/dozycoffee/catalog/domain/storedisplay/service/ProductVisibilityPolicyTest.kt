package com.dozycoffee.catalog.domain.storedisplay.service

import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.shared.StoreId
import com.dozycoffee.catalog.domain.storeavailability.StockStatus
import com.dozycoffee.catalog.domain.storeavailability.StoreProductAvailability
import com.dozycoffee.catalog.domain.storedisplay.model.StoreDisplaySetting
import com.dozycoffee.catalog.domain.storedisplay.model.StoreVisibility
import com.dozycoffee.catalog.domain.storedisplay.model.Visibility
import com.dozycoffee.catalog.fixture.displaySetting
import com.dozycoffee.catalog.fixture.inventoryAvailability
import com.dozycoffee.catalog.fixture.ownerAvailability
import com.dozycoffee.catalog.fixture.product
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("ProductVisibilityPolicy — 매장별 노출 판단")
class ProductVisibilityPolicyTest {
    private val storeId = StoreId(1)

    @Nested
    @DisplayName("1단계 — 상품 상태")
    inner class ProductStatusStep {
        @Test
        fun `Draft 상품은 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.DRAFT))

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `단종 상품은 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.DISCONTINUED))

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `진열 설정이 노출이어도 Active가 아니면 노출되지 않는다`() {
            // 1단계에서 즉시 종료되므로 뒤 단계는 보지 않는다.
            val result =
                resolve(
                    product(status = ProductStatus.DISCONTINUED),
                    display = displaySetting(visibility = Visibility.VISIBLE),
                )

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("2단계 — 판매 범위")
    inner class StoreScopeStep {
        @Test
        fun `전체 판매 범위면 모든 매장에 노출된다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE, storeScope = StoreScope.All))

            assertIs<StoreVisibility.Visible>(result)
        }

        @Test
        fun `한정 판매 범위에 포함된 매장에는 노출된다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE, storeScope = StoreScope.Limited(setOf(storeId))))

            assertIs<StoreVisibility.Visible>(result)
        }

        @Test
        fun `한정 판매 범위에서 제외된 매장에는 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE, storeScope = StoreScope.Limited(setOf(StoreId(99)))))

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `대상 매장이 비어 있으면 어떤 매장에도 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE, storeScope = StoreScope.Limited(emptySet())))

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("3·4단계 — 진열 설정과 노출 여부")
    inner class DisplayStep {
        @Test
        fun `진열 설정이 없으면 기본값인 노출로 간주한다`() {
            // row 부재 자체가 "기본값으로 노출 중"을 의미한다(Lazy 생성).
            val result = resolve(product(status = ProductStatus.ACTIVE))

            assertIs<StoreVisibility.Visible>(result)
        }

        @Test
        fun `점주가 숨긴 상품은 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE), display = displaySetting(visibility = Visibility.HIDDEN))

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `숨김이면 품절 여부와 무관하게 비노출이다`() {
            val result =
                resolve(
                    product(status = ProductStatus.ACTIVE),
                    display = displaySetting(visibility = Visibility.HIDDEN),
                    availability = ownerAvailability(stockStatus = StockStatus.SOLD_OUT),
                )

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("4단계 품절 표시 — 재고 미추적 상품")
    inner class UntrackedStock {
        @Test
        fun `판매 가능 여부가 바뀐 적 없으면 판매중이다`() {
            val result = resolve(product(status = ProductStatus.ACTIVE, tracksInventory = false))

            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), result)
        }

        @Test
        fun `점주가 품절 처리했으면 품절로 표시되며 노출된다`() {
            // 노출 여부와 품절 상태는 독립적이다(요구사항 2.5).
            val result =
                resolve(
                    product(status = ProductStatus.ACTIVE, tracksInventory = false),
                    display = displaySetting(visibility = Visibility.VISIBLE),
                    availability = ownerAvailability(stockStatus = StockStatus.SOLD_OUT),
                )

            assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result)
        }
    }

    @Nested
    @DisplayName("4단계 품절 표시 — 재고 추적 상품")
    inner class TrackedStock {
        @Test
        fun `재고 정보를 받은 적 없으면 처음 재고 0이라 품절로 노출된다`() {
            // 재고 추적 상품도 활성화되면 곧바로 판매 목록에 나타나되 품절로 표시된다(요구사항 2.4).
            val result = resolve(product(status = ProductStatus.ACTIVE, tracksInventory = true))

            assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result)
        }

        @Test
        fun `입고로 재고가 생기면 판매중으로 노출된다`() {
            val result =
                resolve(
                    product(status = ProductStatus.ACTIVE, tracksInventory = true),
                    availability = inventoryAvailability(stockStatus = StockStatus.ON_SALE),
                )

            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), result)
        }

        @Test
        fun `재고가 있어도 점주가 숨겼으면 노출되지 않는다`() {
            val result =
                resolve(
                    product(status = ProductStatus.ACTIVE, tracksInventory = true),
                    display = displaySetting(visibility = Visibility.HIDDEN),
                    availability = inventoryAvailability(stockStatus = StockStatus.ON_SALE),
                )

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `판매 범위에서 빠졌다가 다시 포함되면 유지된 재고 상태를 따른다`() {
            // INVENTORY 출처 판매 가능 여부는 판매 범위에서 빠져도 지우지 않는다(요구사항 1.5).
            val product =
                product(
                    status = ProductStatus.ACTIVE,
                    tracksInventory = true,
                    storeScope = StoreScope.Limited(emptySet()),
                )
            val availability = inventoryAvailability(stockStatus = StockStatus.ON_SALE)
            assertEquals(StoreVisibility.NotVisible, resolve(product, availability = availability))

            product.changeStoreScope(StoreScope.Limited(setOf(storeId)))

            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), resolve(product, availability = availability))
        }
    }

    @Nested
    @DisplayName("단종 후 재활성화 (요구사항 2.6)")
    inner class DiscontinueAndReactivate {
        // 진열 설정에는 점주 의도만 남기고 단종 여부는 Product.status로만 판단한다.
        // 아래 경우들이 2.6이 요구하는 결과와 일치하는지가 이 설계의 근거다.

        @Test
        fun `진열 설정이 없던 매장은 재활성화되면 다시 기본 노출된다`() {
            val product = product(status = ProductStatus.ACTIVE)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product))

            product.activate()
            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), resolve(product))
        }

        @Test
        fun `노출 상태로 설정해둔 매장은 재활성화되면 그대로 노출된다`() {
            val product = product(status = ProductStatus.ACTIVE)
            val display = displaySetting(visibility = Visibility.VISIBLE)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product, display = display))

            product.activate()
            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), resolve(product, display = display))
        }

        @Test
        fun `점주가 직접 숨긴 매장은 재활성화돼도 숨김이 유지된다`() {
            // 점주 의도를 덮어쓰면 안 된다.
            val product = product(status = ProductStatus.ACTIVE)
            val display = displaySetting(visibility = Visibility.HIDDEN)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product, display = display))

            product.activate()
            assertEquals(StoreVisibility.NotVisible, resolve(product, display = display))
        }

        @Test
        fun `단종 중 재고가 소진됐으면 재활성화 후 품절로 노출된다`() {
            // 재고 이벤트는 단종 중에도 계속 반영된다(요구사항 2.4).
            val product = product(status = ProductStatus.ACTIVE, tracksInventory = true)
            val availability =
                inventoryAvailability(stockStatus = StockStatus.ON_SALE, occurredAt = Instant.parse("2026-09-18T00:00:00Z"))

            product.discontinue()
            availability.applyInventoryEvent(StockStatus.SOLD_OUT, Instant.parse("2026-09-18T01:00:00Z"))
            product.activate()

            assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), resolve(product, availability = availability))
        }
    }

    private fun resolve(
        product: Product,
        display: StoreDisplaySetting? = null,
        availability: StoreProductAvailability? = null,
    ) = ProductVisibilityPolicy.resolve(product, storeId, display, availability)
}
