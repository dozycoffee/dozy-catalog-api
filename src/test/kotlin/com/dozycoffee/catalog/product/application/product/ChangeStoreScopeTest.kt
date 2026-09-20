package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.port.ValidateStoreExistsPort
import com.dozycoffee.catalog.product.application.product.command.ChangeStoreScopeCommand
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.product.exception.TargetStoreNotFoundException
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.store.domain.display.Visibility
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.FakeStoreExistenceValidator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("S4 판매 범위 변경과 매장 설정 정리 (요구사항 1.5)")
class ChangeStoreScopeTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

    @Autowired
    private lateinit var availabilityRepository: StoreProductAvailabilityRepository

    @Autowired
    private lateinit var storeValidator: ValidateStoreExistsPort

    private lateinit var coffee: ChildCategory

    @BeforeEach
    fun setUp() =
        runTest {
            validator().reset()
            val beverage = categoryService.registerTopLevel("음료")
            coffee = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
        }

    @Nested
    @DisplayName("S4 기본 흐름")
    inner class BasicFlow {
        @Test
        fun `한정으로 바꾸면 대상 매장이 모두 존재하는지 확인한다`() =
            runTest {
                val product = registerProduct()

                val changed = service.changeStoreScope(command(product, limited(1, 2)))

                assertEquals(listOf(setOf(StoreId(1), StoreId(2))), validator().checked)
                val scope = assertIs<StoreScope.Limited>(changed.storeScope)
                assertEquals(setOf(StoreId(1), StoreId(2)), scope.targetStoreIds)
                assertEquals(2, count("SELECT count(*) FROM product_target_stores"))
            }

        @Test
        fun `전체로 바꾸면 매장 존재를 확인하지 않는다`() =
            runTest {
                val product = registerProduct()
                val limited = service.changeStoreScope(command(product, limited(1)))

                val changed = service.changeStoreScope(command(limited, StoreScope.All))

                assertEquals(StoreScope.All, changed.storeScope)
                assertEquals(listOf(setOf(StoreId(1))), validator().checked)
                assertEquals(0, count("SELECT count(*) FROM product_target_stores"))
            }

        @Test
        fun `대상에서 빠진 매장의 진열 설정과 점주 수동 품절이 삭제된다`() =
            runTest {
                val product = registerProduct(tracksInventory = false)
                insertDisplaySetting(storeId = 1, product = product, visibility = Visibility.HIDDEN)
                insertDisplaySetting(storeId = 2, product = product, visibility = Visibility.HIDDEN)
                insertOwnerAvailability(storeId = 1, product = product, stockStatus = StockStatus.SOLD_OUT)
                insertOwnerAvailability(storeId = 2, product = product, stockStatus = StockStatus.SOLD_OUT)

                service.changeStoreScope(command(product, limited(1)))

                assertEquals(listOf(StoreId(1)), displaySettingStores(product))
                assertEquals(listOf(StoreId(1)), availabilityStores(product))
                // 남은 매장의 설정은 그대로다.
                val kept = assertNotNull(tx { displaySettingRepository.findByStoreAndProduct(StoreId(1), product.id) })
                assertEquals(Visibility.HIDDEN, kept.visibility)
            }

        @Test
        fun `재고 추적 상품의 매장 재고 상태는 삭제하지 않는다`() =
            runTest {
                val product = registerProduct(tracksInventory = true)
                insertDisplaySetting(storeId = 2, product = product, visibility = Visibility.HIDDEN)
                insertInventoryAvailability(storeId = 2, product = product, stockStatus = StockStatus.ON_SALE)

                service.changeStoreScope(command(product, limited(1)))

                assertTrue(displaySettingStores(product).isEmpty())
                assertEquals(listOf(StoreId(2)), availabilityStores(product))
                val kept =
                    assertNotNull(
                        tx { availabilityRepository.findAllByProduct(product.id) }.singleOrNull(),
                    )
                assertEquals(StockStatus.ON_SALE, kept.stockStatus)
            }

        @Test
        fun `대상 매장이 빈 한정이면 모든 매장의 진열 설정과 점주 판매 가능 여부가 삭제된다`() =
            runTest {
                val product = registerProduct(tracksInventory = false)
                insertDisplaySetting(storeId = 1, product = product, visibility = Visibility.HIDDEN)
                insertDisplaySetting(storeId = 2, product = product, visibility = Visibility.VISIBLE)
                insertOwnerAvailability(storeId = 1, product = product, stockStatus = StockStatus.SOLD_OUT)

                val changed = service.changeStoreScope(command(product, StoreScope.Limited(emptySet())))

                assertEquals(StoreScope.Limited(emptySet()), changed.storeScope)
                assertTrue(displaySettingStores(product).isEmpty())
                assertTrue(availabilityStores(product).isEmpty())
            }

        @Test
        fun `다시 대상에 포함돼도 이전 설정은 복원되지 않고 기본값으로 시작한다`() =
            runTest {
                val product = registerProduct(tracksInventory = false)
                insertDisplaySetting(storeId = 2, product = product, visibility = Visibility.HIDDEN)
                insertOwnerAvailability(storeId = 2, product = product, stockStatus = StockStatus.SOLD_OUT)

                val limited = service.changeStoreScope(command(product, limited(1)))
                service.changeStoreScope(command(limited, limited(1, 2)))

                assertTrue(displaySettingStores(product).isEmpty())
                assertTrue(availabilityStores(product).isEmpty())
            }

        @Test
        fun `다른 상품의 매장 설정은 건드리지 않는다`() =
            runTest {
                val product = registerProduct()
                val other = registerProduct(name = "라떼")
                insertDisplaySetting(storeId = 2, product = other, visibility = Visibility.HIDDEN)

                service.changeStoreScope(command(product, limited(1)))

                assertEquals(listOf(StoreId(2)), displaySettingStores(other))
            }
    }

    @Nested
    @DisplayName("S4 예외 흐름")
    inner class ExceptionFlow {
        @Test
        fun `존재하지 않는 매장 ID가 있으면 거부하고 판매 범위와 매장 설정이 그대로다`() =
            runTest {
                val product = registerProduct(tracksInventory = false)
                insertDisplaySetting(storeId = 2, product = product, visibility = Visibility.HIDDEN)
                insertOwnerAvailability(storeId = 2, product = product, stockStatus = StockStatus.SOLD_OUT)
                validator().markMissing(StoreId(3))

                assertFailsWith<TargetStoreNotFoundException> {
                    service.changeStoreScope(command(product, limited(1, 3)))
                }

                assertEquals(StoreScope.All, tx { productRepository.findById(product.id) }?.storeScope)
                assertEquals(listOf(StoreId(2)), displaySettingStores(product))
                assertEquals(listOf(StoreId(2)), availabilityStores(product))
            }

        @Test
        fun `오래된 버전으로 바꾸면 거부하고 판매 범위가 그대로다`() =
            runTest {
                val product = registerProduct()
                service.changeStoreScope(command(product, limited(1)))

                assertFailsWith<VersionConflictException> {
                    service.changeStoreScope(ChangeStoreScopeCommand(product.id, version = 0, scope = limited(2)))
                }

                val found = assertNotNull(tx { productRepository.findById(product.id) })
                assertEquals(setOf(StoreId(1)), assertIs<StoreScope.Limited>(found.storeScope).targetStoreIds)
            }

        @Test
        fun `없는 상품의 판매 범위를 바꾸면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.changeStoreScope(ChangeStoreScopeCommand(ProductId(999), version = 0, scope = limited(1)))
                }
            }
    }

    private fun validator() = storeValidator as FakeStoreExistenceValidator

    private fun limited(vararg storeIds: Long) = StoreScope.Limited(storeIds.map { StoreId(it) }.toSet())

    private fun command(
        product: Product,
        scope: StoreScope,
    ) = ChangeStoreScopeCommand(productId = product.id, version = product.version, scope = scope)

    private suspend fun registerProduct(
        name: String = "아메리카노",
        tracksInventory: Boolean = false,
    ): Product =
        service.register(
            RegisterProductCommand(
                name = name,
                categoryId = coffee.id,
                basePrice = Money(4500),
                tracksInventory = tracksInventory,
            ),
        )

    private suspend fun displaySettingStores(product: Product): List<StoreId> =
        tx { displaySettingRepository.findAllByProduct(product.id) }.map { it.storeId }

    private suspend fun availabilityStores(product: Product): List<StoreId> =
        tx { availabilityRepository.findAllByProduct(product.id) }.map { it.id.storeId }

    // 검증 대상이 아닌 매장 설정은 다른 유스케이스를 거치지 않고 직접 넣는다(docs/architecture/testing.md).
    private suspend fun insertDisplaySetting(
        storeId: Long,
        product: Product,
        visibility: Visibility,
    ) = execute(
        "INSERT INTO store_display_settings (store_id, product_id, visibility) " +
            "VALUES ($storeId, ${product.id.value}, '$visibility')",
    )

    private suspend fun insertOwnerAvailability(
        storeId: Long,
        product: Product,
        stockStatus: StockStatus,
    ) = execute(
        "INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status) " +
            "VALUES ($storeId, ${product.id.value}, 'OWNER', '$stockStatus')",
    )

    private suspend fun insertInventoryAvailability(
        storeId: Long,
        product: Product,
        stockStatus: StockStatus,
    ) = execute(
        "INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at) " +
            "VALUES ($storeId, ${product.id.value}, 'INVENTORY', '$stockStatus', now())",
    )
}
