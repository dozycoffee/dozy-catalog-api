package com.dozycoffee.catalog.scenario

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.application.product.command.ReplaceProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// 여러 유스케이스에 걸친 흐름을 순서대로 밟는다(docs/architecture/testing.md).
@DisplayName("S1. 상품 등록부터 매장 노출까지 / S3. 단종과 재활성화")
class ProductLifecycleScenarioTest : ApplicationTest() {
    @Autowired
    private lateinit var productService: ProductApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

    @Autowired
    private lateinit var availabilityRepository: StoreProductAvailabilityRepository

    private var coffeeId: CategoryId = CategoryId(0)

    private val gangnam = StoreId(1)
    private val hongdae = StoreId(2)

    @BeforeEach
    fun setUpCategory() =
        runTest {
            val beverage = categoryService.registerTopLevel("음료")
            coffeeId = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피")).id
        }

    @Test
    fun `S1 등록한 상품을 활성화하면 판매 범위에 든 매장에서 기본 노출된다`() =
        runTest {
            // 1~2단계: Draft로 생성되고 SKU가 부여된다. 이 시점에는 어디에도 노출되지 않는다.
            val registered = productService.register(command("아메리카노", tracksInventory = false))
            assertEquals(ProductStatus.DRAFT, registered.status)
            assertNotNull(registered.sku)
            assertIs<StoreVisibility.NotVisible>(visibilityAt(gangnam, registered.id))

            // 3~4단계: 활성화
            productService.activate(registered.id)

            // 5단계: 개별 설정 없이 노출되고, 재고 미추적 상품은 판매중으로 보인다.
            val visibility = assertIs<StoreVisibility.Visible>(visibilityAt(gangnam, registered.id))
            assertEquals(StockStatus.ON_SALE, visibility.stockStatus)
            assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
        }

    @Test
    fun `S1 재고 추적 상품은 활성화 직후 매장 재고가 없어 품절로 보인다`() =
        runTest {
            val registered = productService.register(command("텀블러", tracksInventory = true))

            productService.activate(registered.id)

            val visibility = assertIs<StoreVisibility.Visible>(visibilityAt(gangnam, registered.id))
            assertEquals(StockStatus.SOLD_OUT, visibility.stockStatus)
        }

    @Test
    fun `S3 단종하면 모든 매장에서 비노출되고 매장 설정은 보존되며 재활성화하면 그대로 이어진다`() =
        runTest {
            val product = productService.register(command("아메리카노", tracksInventory = false))
            productService.activate(product.id)

            // 강남점 점주가 숨긴다. 홍대점은 설정이 없다.
            hide(gangnam, product.id)
            assertIs<StoreVisibility.NotVisible>(visibilityAt(gangnam, product.id))
            assertIs<StoreVisibility.Visible>(visibilityAt(hongdae, product.id))

            // 1~3단계: 단종하면 모든 매장에서 비노출되고 개별 설정은 그대로 남는다.
            val discontinued = productService.discontinue(product.id)
            assertIs<StoreVisibility.NotVisible>(visibilityAt(gangnam, product.id))
            assertIs<StoreVisibility.NotVisible>(visibilityAt(hongdae, product.id))
            assertEquals(1, count("SELECT count(*) FROM store_display_settings"))

            // 4단계: 단종 중에도 상태 외 정보는 수정할 수 있다.
            productService.replace(
                ReplaceProductCommand(
                    productId = product.id,
                    version = discontinued.version,
                    name = "아메리카노(리뉴얼)",
                    categoryId = coffeeId,
                    basePrice = Money(5000),
                ),
            )

            // 5~7단계: 재활성화하면 보존된 설정을 그대로 이어 쓴다.
            productService.activate(product.id)

            assertIs<StoreVisibility.NotVisible>(visibilityAt(gangnam, product.id))
            assertIs<StoreVisibility.Visible>(visibilityAt(hongdae, product.id))
            assertEquals("아메리카노(리뉴얼)", tx { productRepository.findById(product.id) }?.name)
        }

    private fun command(
        name: String,
        tracksInventory: Boolean,
    ) = RegisterProductCommand(
        name = name,
        categoryId = coffeeId,
        basePrice = Money(4500),
        tracksInventory = tracksInventory,
    )

    private suspend fun hide(
        storeId: StoreId,
        productId: ProductId,
    ) = tx {
        val setting = displaySettingRepository.findOrCreate(storeId, productId)
        setting.hide()
        displaySettingRepository.saveVisibility(setting)
    }

    // 요구사항 3장의 노출 판단. 매장별 설정과 판매 가능 여부를 불러와 정책에 넘긴다.
    private suspend fun visibilityAt(
        storeId: StoreId,
        productId: ProductId,
    ): StoreVisibility =
        tx {
            val product: Product = assertNotNull(productRepository.findById(productId))
            ProductVisibilityPolicy.resolve(
                product = product,
                storeId = storeId,
                displaySetting = displaySettingRepository.findByStoreAndProduct(storeId, productId),
                availability = availabilityRepository.findById(StoreProductAvailabilityId(storeId, productId)),
            )
        }
}
