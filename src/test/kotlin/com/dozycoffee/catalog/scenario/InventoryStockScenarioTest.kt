package com.dozycoffee.catalog.scenario

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.application.availability.StoreProductAvailabilityApplicationService
import com.dozycoffee.catalog.store.application.availability.command.ApplyInventoryEventCommand
import com.dozycoffee.catalog.store.application.display.StoreDisplaySettingApplicationService
import com.dozycoffee.catalog.store.application.display.command.ChangeDisplayOrderCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.application.storeproduct.StoreProductQueryService
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.display.Visibility
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// 재고관리 서비스 이벤트가 매장의 품절 여부로 이어지는 흐름을 순서대로 밟는다(docs/architecture/testing.md).
@DisplayName("S7. 재고 추적 상품의 품절과 해제")
class InventoryStockScenarioTest : ApplicationTest() {
    @Autowired
    private lateinit var productService: ProductApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var availabilityService: StoreProductAvailabilityApplicationService

    @Autowired
    private lateinit var displaySettingService: StoreDisplaySettingApplicationService

    @Autowired
    private lateinit var storeProductQueryService: StoreProductQueryService

    private var mdCategoryId: CategoryId = CategoryId(0)

    private val gangnam = StoreId(1)
    private val stockedAt = Instant.parse("2026-09-18T01:00:00Z")

    @BeforeEach
    fun setUpCategory() =
        runTest {
            val md = categoryService.registerTopLevel("MD")
            mdCategoryId = categoryService.registerChild(RegisterChildCategoryCommand(md.id, "텀블러")).id
        }

    @Test
    fun `S7 활성화 직후 품절로 보이다가 입고·소진·재입고에 따라 품절이 바뀐다`() =
        runTest {
            // 1단계: 활성화하면 곧바로 판매 목록에 나타나고, 매장 재고가 0이라 품절로 보인다.
            val tumbler = registerTrackedProduct()
            productService.activate(tumbler)
            assertEquals(StockStatus.SOLD_OUT, visibleStockStatus(tumbler))

            // 2~4단계: 입고되면 재고관리 서비스가 "매장 재고 생김"을 알리고 품절이 풀린다.
            assertTrue(applyEvent(tumbler, StockStatus.ON_SALE, stockedAt))
            assertEquals(StockStatus.ON_SALE, visibleStockStatus(tumbler))

            // 5단계: 소진되면 다시 품절, 재입고되면 다시 판매중이다.
            assertTrue(applyEvent(tumbler, StockStatus.SOLD_OUT, stockedAt.plusSeconds(3600)))
            assertEquals(StockStatus.SOLD_OUT, visibleStockStatus(tumbler))

            assertTrue(applyEvent(tumbler, StockStatus.ON_SALE, stockedAt.plusSeconds(7200)))
            assertEquals(StockStatus.ON_SALE, visibleStockStatus(tumbler))
        }

    @Test
    fun `S7 1a 입고 전에 숨기거나 진열 순서를 바꿔도 품절 여부에는 영향이 없다`() =
        runTest {
            val tumbler = registerTrackedProduct()
            productService.activate(tumbler)

            displaySettingService.changeVisibility(ChangeVisibilityCommand(gangnam, tumbler, Visibility.HIDDEN))
            displaySettingService.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, tumbler, 0))
            assertIs<StoreVisibility.NotVisible>(visibilityOf(tumbler))

            // 숨김은 노출 의도일 뿐이라 재고 이벤트는 그대로 반영된다.
            assertTrue(applyEvent(tumbler, StockStatus.ON_SALE, stockedAt))
            assertIs<StoreVisibility.NotVisible>(visibilityOf(tumbler))

            displaySettingService.changeVisibility(ChangeVisibilityCommand(gangnam, tumbler, Visibility.VISIBLE))

            assertEquals(StockStatus.ON_SALE, visibleStockStatus(tumbler))
        }

    @Test
    fun `S7 3a 단종 중에 도착한 재고 이벤트도 반영되어 재활성화 시점에 그대로 보인다`() =
        runTest {
            val tumbler = registerTrackedProduct()
            productService.activate(tumbler)
            applyEvent(tumbler, StockStatus.ON_SALE, stockedAt)

            // 단종하면 목록에서 빠지지만, 그 사이 폐기된 재고도 이벤트로 반영된다.
            productService.discontinue(tumbler)
            assertTrue(storeProductQueryService.listProducts(gangnam).none { it.product.id == tumbler })
            assertTrue(applyEvent(tumbler, StockStatus.SOLD_OUT, stockedAt.plusSeconds(3600)))

            productService.activate(tumbler)

            assertEquals(StockStatus.SOLD_OUT, visibleStockStatus(tumbler))
        }

    @Test
    fun `S7 3b 오래된 이벤트와 중복 이벤트는 무시한다`() =
        runTest {
            val tumbler = registerTrackedProduct()
            productService.activate(tumbler)
            applyEvent(tumbler, StockStatus.ON_SALE, stockedAt)

            assertFalse(applyEvent(tumbler, StockStatus.SOLD_OUT, stockedAt.minusSeconds(3600)))
            assertFalse(applyEvent(tumbler, StockStatus.ON_SALE, stockedAt))

            assertEquals(StockStatus.ON_SALE, visibleStockStatus(tumbler))
        }

    private suspend fun registerTrackedProduct(): ProductId =
        productService
            .register(
                RegisterProductCommand(
                    name = "텀블러",
                    categoryId = mdCategoryId,
                    basePrice = Money(30000),
                    tracksInventory = true,
                ),
            ).id

    private suspend fun applyEvent(
        productId: ProductId,
        stockStatus: StockStatus,
        occurredAt: Instant,
    ): Boolean = availabilityService.applyInventoryEvent(ApplyInventoryEventCommand(gangnam, productId, stockStatus, occurredAt))

    private suspend fun visibilityOf(productId: ProductId): StoreVisibility =
        storeProductQueryService.listProducts(gangnam).single { it.product.id == productId }.visibility

    private suspend fun visibleStockStatus(productId: ProductId): StockStatus =
        assertIs<StoreVisibility.Visible>(visibilityOf(productId)).stockStatus
}
