package com.dozycoffee.catalog.domain.storeproductlisting.service

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.shared.Money
import com.dozycoffee.catalog.domain.storeproductlisting.model.StockStatus
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreProductListing
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreProductListingId
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreVisibility
import com.dozycoffee.catalog.domain.storeproductlisting.model.Visibility
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
            val result = resolve(product(status = ProductStatus.DRAFT), listing = null)

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `단종 상품은 노출되지 않는다`() {
            val result = resolve(product(status = ProductStatus.DISCONTINUED), listing = null)

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `개별 설정이 노출이어도 Active가 아니면 노출되지 않는다`() {
            // 1단계에서 즉시 종료되므로 뒤 단계는 보지 않는다.
            val result =
                resolve(
                    product(status = ProductStatus.DISCONTINUED),
                    listing = listing(visibility = Visibility.VISIBLE),
                )

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("2단계 — 판매 범위")
    inner class StoreScopeStep {
        @Test
        fun `전체 판매 범위면 모든 매장에 노출된다`() {
            val result = resolve(product(storeScope = StoreScope.All), listing = null)

            assertIs<StoreVisibility.Visible>(result)
        }

        @Test
        fun `한정 판매 범위에 포함된 매장에는 노출된다`() {
            val result =
                resolve(product(storeScope = StoreScope.Limited(setOf(storeId))), listing = null)

            assertIs<StoreVisibility.Visible>(result)
        }

        @Test
        fun `한정 판매 범위에서 제외된 매장에는 노출되지 않는다`() {
            val result =
                resolve(product(storeScope = StoreScope.Limited(setOf(StoreId(99)))), listing = null)

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `대상 매장이 비어 있으면 어떤 매장에도 노출되지 않는다`() {
            val result = resolve(product(storeScope = StoreScope.Limited(emptySet())), listing = null)

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("3단계 — 개별 설정 유무")
    inner class ListingStep {
        @Test
        fun `개별 설정이 없으면 기본값인 노출 판매중으로 간주한다`() {
            // row 부재 자체가 "기본값으로 노출 중"을 의미한다(Lazy 생성).
            val result = resolve(product(), listing = null)

            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), result)
        }

        @Test
        fun `개별 설정이 있으면 저장된 값을 그대로 사용한다`() {
            val result =
                resolve(product(), listing = listing(stockStatus = StockStatus.SOLD_OUT))

            assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result)
        }
    }

    @Nested
    @DisplayName("4단계 — 노출 여부와 품절 표시")
    inner class FinalStep {
        @Test
        fun `점주가 숨긴 상품은 노출되지 않는다`() {
            val result = resolve(product(), listing = listing(visibility = Visibility.HIDDEN))

            assertEquals(StoreVisibility.NotVisible, result)
        }

        @Test
        fun `품절이어도 숨기지 않았다면 품절로 표시되며 노출된다`() {
            // 노출 여부와 품절 상태는 독립적이다(2.5).
            val result =
                resolve(
                    product(),
                    listing = listing(visibility = Visibility.VISIBLE, stockStatus = StockStatus.SOLD_OUT),
                )

            assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result)
        }

        @Test
        fun `숨김이면 품절 상태와 무관하게 비노출이다`() {
            val result =
                resolve(
                    product(),
                    listing = listing(visibility = Visibility.HIDDEN, stockStatus = StockStatus.SOLD_OUT),
                )

            assertEquals(StoreVisibility.NotVisible, result)
        }
    }

    @Nested
    @DisplayName("단종 후 재활성화 (2.6)")
    inner class DiscontinueAndReactivate {
        // visibility에는 점주 의도만 남기고 단종 여부는 Product.status로만 판단한다.
        // 아래 세 경우가 2.6이 요구하는 결과와 일치하는지가 이 설계의 근거다.

        @Test
        fun `개별 설정이 없던 매장은 재활성화되면 다시 기본 노출된다`() {
            val product = product(status = ProductStatus.ACTIVE)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product, listing = null))

            product.activate()
            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), resolve(product, listing = null))
        }

        @Test
        fun `노출 상태로 설정해둔 매장은 재활성화되면 그대로 복원된다`() {
            val product = product(status = ProductStatus.ACTIVE)
            val listing = listing(visibility = Visibility.VISIBLE)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product, listing))

            product.activate()
            assertEquals(StoreVisibility.Visible(StockStatus.ON_SALE), resolve(product, listing))
        }

        @Test
        fun `점주가 직접 숨긴 매장은 재활성화돼도 숨김이 유지된다`() {
            // 복원 대상은 "단종 때문에 숨겨진 것"뿐이므로, 점주 의도를 덮어쓰면 안 된다.
            val product = product(status = ProductStatus.ACTIVE)
            val listing = listing(visibility = Visibility.HIDDEN)

            product.discontinue()
            assertEquals(StoreVisibility.NotVisible, resolve(product, listing))

            product.activate()
            assertEquals(StoreVisibility.NotVisible, resolve(product, listing))
        }

        @Test
        fun `단종과 재활성화는 개별 설정을 건드리지 않는다`() {
            val product = product(status = ProductStatus.ACTIVE)
            val listing = listing(visibility = Visibility.HIDDEN, stockStatus = StockStatus.SOLD_OUT)

            product.discontinue()
            product.activate()

            assertEquals(Visibility.HIDDEN, listing.visibility)
            assertEquals(StockStatus.SOLD_OUT, listing.stockStatus)
        }
    }

    private fun resolve(
        product: Product,
        listing: StoreProductListing?,
    ) = ProductVisibilityPolicy.resolve(product, storeId, listing)

    private fun product(
        status: ProductStatus = ProductStatus.ACTIVE,
        storeScope: StoreScope = StoreScope.All,
    ) = Product(
        id = ProductId(1),
        sku = null,
        name = "아메리카노",
        categoryId = CategoryId(10),
        description = null,
        imageUrl = null,
        basePrice = Money(4500),
        tracksInventory = false,
        status = status,
        storeScope = storeScope,
    )

    private fun listing(
        visibility: Visibility = Visibility.VISIBLE,
        stockStatus: StockStatus = StockStatus.ON_SALE,
    ) = StoreProductListing(
        id = StoreProductListingId(1),
        storeId = storeId,
        productId = ProductId(1),
        visibility = visibility,
        stockStatus = stockStatus,
    )
}
