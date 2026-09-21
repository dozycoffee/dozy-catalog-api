package com.dozycoffee.catalog.store.application.storeproduct

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// 검증 대상은 목록 조회뿐이라, 진열 설정·판매 가능 여부 같은 준비 데이터는 다른 유스케이스를 거치지 않고
// SQL로 직접 넣는다(docs/architecture/testing.md).
@DisplayName("S6. 점주의 매장 상품 목록 조회 (요구사항 2.2, 3장)")
class StoreProductQueryServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: StoreProductQueryService

    private val gangnam = StoreId(10)
    private val hongdae = StoreId(20)

    private val americano = ProductId(1)
    private val latte = ProductId(2)
    private val tumbler = ProductId(3)

    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            insertProduct("아메리카노", status = "ACTIVE", tracksInventory = false)
            insertProduct("카페라떼", status = "ACTIVE", tracksInventory = false)
            insertProduct("텀블러", status = "ACTIVE", tracksInventory = true)
        }

    @Nested
    @DisplayName("기본 흐름")
    inner class BasicFlow {
        @Test
        fun `개별 설정이 있는 상품과 없는 상품이 섞여도 각각 저장된 값과 기본값으로 나온다`() =
            runTest {
                hide(gangnam, americano)

                val products = service.listProducts(gangnam)

                assertEquals(listOf(americano, latte, tumbler), products.map { it.product.id })
                assertIs<StoreVisibility.NotVisible>(visibilityOf(products, americano))
                // 설정이 없는 재고 미추적 상품은 기본값(노출, 판매중)이다.
                assertEquals(StockStatus.ON_SALE, visibleStockStatus(products, latte))
            }

        @Test
        fun `재고 추적 상품은 재고 정보를 받은 적이 없으면 품절로 보인다`() =
            runTest {
                val products = service.listProducts(gangnam)

                assertEquals(StockStatus.SOLD_OUT, visibleStockStatus(products, tumbler))
            }

        @Test
        fun `품절이어도 숨기지 않았으면 노출되고 구매 불가로 표시된다`() =
            runTest {
                markSoldOutByOwner(gangnam, latte)

                val products = service.listProducts(gangnam)

                assertEquals(StockStatus.SOLD_OUT, visibleStockStatus(products, latte))
            }

        @Test
        fun `진열 순서를 정한 상품이 앞에 오고 정하지 않은 상품은 뒤에 등록 순으로 온다`() =
            runTest {
                changeDisplayOrder(gangnam, tumbler, 1)
                changeDisplayOrder(gangnam, latte, 0)

                val products = service.listProducts(gangnam)

                assertEquals(listOf(latte, tumbler, americano), products.map { it.product.id })
                assertEquals(listOf(0, 1, null), products.map { it.displayOrder })
            }

        @Test
        fun `진열 설정은 매장마다 독립적이다`() =
            runTest {
                hide(gangnam, americano)

                val hongdaeProducts = service.listProducts(hongdae)

                assertIs<StoreVisibility.Visible>(visibilityOf(hongdaeProducts, americano))
            }
    }

    @Nested
    @DisplayName("목록에서 빠지는 상품")
    inner class Excluded {
        @Test
        fun `Active가 아닌 상품은 목록에 없다`() =
            runTest {
                insertProduct("신메뉴", status = "DRAFT", tracksInventory = false)
                insertProduct("단종된 상품", status = "DISCONTINUED", tracksInventory = false)

                val products = service.listProducts(gangnam)

                assertEquals(listOf(americano, latte, tumbler), products.map { it.product.id })
            }

        @Test
        fun `판매 범위 밖인 매장에서는 한정 판매 상품이 목록에 없다`() =
            runTest {
                insertProduct("지역 한정 상품", status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")
                val limited = ProductId(4)
                execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (4, ${gangnam.value})")

                assertTrue(service.listProducts(gangnam).any { it.product.id == limited })
                assertTrue(service.listProducts(hongdae).none { it.product.id == limited })
            }

        @Test
        fun `대상 매장이 비어 있는 한정 판매 상품은 어느 매장에도 나오지 않는다`() =
            runTest {
                insertProduct("미배정 상품", status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")

                assertEquals(listOf(americano, latte, tumbler), service.listProducts(gangnam).map { it.product.id })
                assertEquals(listOf(americano, latte, tumbler), service.listProducts(hongdae).map { it.product.id })
            }
    }

    @Nested
    @DisplayName("ids로 거르기")
    inner class Ids {
        @Test
        fun `주어진 ID의 상품만 목록과 같은 순서로 돌려준다`() =
            runTest {
                changeDisplayOrder(gangnam, tumbler, 0)

                val products = service.listProducts(gangnam, ids = setOf(americano, tumbler))

                assertEquals(listOf(tumbler, americano), products.map { it.product.id })
            }

        @Test
        fun `판매할 수 없는 상품과 없는 ID는 오류 없이 빠진다`() =
            runTest {
                insertProduct("신메뉴", status = "DRAFT", tracksInventory = false)

                val products = service.listProducts(gangnam, ids = setOf(latte, ProductId(4), ProductId(99)))

                assertEquals(listOf(latte), products.map { it.product.id })
            }
    }

    private suspend fun insertProduct(
        name: String,
        status: String,
        tracksInventory: Boolean,
        storeScope: String = "ALL",
    ) = execute(
        """
        INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('$name', 2, 4500, '$status', '$storeScope', $tracksInventory)
        """.trimIndent(),
    )

    private suspend fun hide(
        storeId: StoreId,
        productId: ProductId,
    ) = execute(
        """
        INSERT INTO store_display_settings (store_id, product_id, visibility)
        VALUES (${storeId.value}, ${productId.value}, 'HIDDEN')
        """.trimIndent(),
    )

    private suspend fun changeDisplayOrder(
        storeId: StoreId,
        productId: ProductId,
        displayOrder: Int,
    ) = execute(
        """
        INSERT INTO store_display_settings (store_id, product_id, display_order)
        VALUES (${storeId.value}, ${productId.value}, $displayOrder)
        """.trimIndent(),
    )

    private suspend fun markSoldOutByOwner(
        storeId: StoreId,
        productId: ProductId,
    ) = execute(
        """
        INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status)
        VALUES (${storeId.value}, ${productId.value}, 'OWNER', 'SOLD_OUT')
        """.trimIndent(),
    )

    private fun visibilityOf(
        products: List<StoreProductView>,
        productId: ProductId,
    ): StoreVisibility = products.single { it.product.id == productId }.visibility

    private fun visibleStockStatus(
        products: List<StoreProductView>,
        productId: ProductId,
    ): StockStatus = assertIs<StoreVisibility.Visible>(visibilityOf(products, productId)).stockStatus
}
