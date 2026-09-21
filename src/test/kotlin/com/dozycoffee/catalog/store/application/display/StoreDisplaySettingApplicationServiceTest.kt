package com.dozycoffee.catalog.store.application.display

import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.display.command.ReplaceDisplayOrderCommand
import com.dozycoffee.catalog.store.application.storeproduct.StoreProductQueryService
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.store.domain.display.Visibility
import com.dozycoffee.catalog.store.domain.display.exception.DuplicateDisplayOrderProductException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("S6. 점주의 진열 설정 변경 (요구사항 2.2, 2.3)")
class StoreDisplaySettingApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: StoreDisplaySettingApplicationService

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

    @Autowired
    private lateinit var storeProductQueryService: StoreProductQueryService

    private val gangnam = StoreId(10)
    private val americano = ProductId(1)

    @BeforeEach
    fun prepareProducts() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute(
                "INSERT INTO products (name, category_id, base_price, status, tracks_inventory) " +
                    "VALUES ('아메리카노', 2, 4500, 'ACTIVE', false)",
            )
        }

    @Nested
    @DisplayName("기본 흐름")
    inner class BasicFlow {
        @Test
        fun `숨기면 숨김으로 저장되고 다시 노출로 되돌릴 수 있다`() =
            runTest {
                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))
                assertEquals(Visibility.HIDDEN, storedSetting()?.visibility)

                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.VISIBLE))

                assertEquals(Visibility.VISIBLE, storedSetting()?.visibility)
            }

        @Test
        fun `아무 설정도 하지 않은 상품에는 개별 설정이 없고 첫 변경 시점에 만들어진다`() =
            runTest {
                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))

                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))

                assertEquals(1, count("SELECT count(*) FROM store_display_settings"))
                val setting = assertNotNull(storedSetting())
                assertEquals(gangnam, setting.storeId)
                assertEquals(americano, setting.productId)
            }

        @Test
        fun `동시에 첫 변경 요청이 들어와도 개별 설정은 매장·상품당 하나만 생긴다`() =
            runTest {
                val settings =
                    coroutineScope {
                        (1..10)
                            .map {
                                async(Dispatchers.IO) {
                                    service.changeVisibility(
                                        ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN),
                                    )
                                }
                            }.awaitAll()
                    }

                assertEquals(1, settings.map { it.id }.distinct().size)
                assertEquals(1, count("SELECT count(*) FROM store_display_settings"))
            }
    }

    @Nested
    @DisplayName("예외 흐름")
    inner class Rejection {
        @Test
        fun `없는 상품의 노출을 바꾸려 하면 거부하고 개별 설정을 만들지 않는다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.changeVisibility(ChangeVisibilityCommand(gangnam, ProductId(999), Visibility.HIDDEN))
                }

                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }
    }

    @Nested
    @DisplayName("판매 범위 확인 (요구사항 2.2)")
    inner class StoreScopeCheck {
        @Test
        fun `판매 범위 밖 상품의 노출을 바꾸려 하면 거부하고 개별 설정을 만들지 않는다`() =
            runTest {
                limitTo(listOf(StoreId(20)))

                assertFailsWith<ProductNotFoundException> {
                    service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))
                }

                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `대상 매장이 빈 LIMITED 상품이면 거부한다`() =
            runTest {
                limitTo(emptyList())

                assertFailsWith<ProductNotFoundException> {
                    service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))
                }

                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `LIMITED의 대상 매장이면 바꿀 수 있다`() =
            runTest {
                limitTo(listOf(gangnam))

                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano)))

                val setting = assertNotNull(storedSetting())
                assertEquals(Visibility.HIDDEN, setting.visibility)
                assertEquals(1, setting.displayOrder)
            }

        @Test
        fun `ALL이면 어느 매장이든 바꿀 수 있다`() =
            runTest {
                execute("UPDATE products SET store_scope = 'ALL' WHERE id = ${americano.value}")

                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))

                assertEquals(Visibility.HIDDEN, storedSetting()?.visibility)
            }

        @Test
        fun `상품 상태는 보지 않아 판매 범위에 든 DRAFT 상품도 바꿀 수 있다`() =
            runTest {
                execute("UPDATE products SET status = 'DRAFT', store_scope = 'ALL' WHERE id = ${americano.value}")

                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))

                assertEquals(Visibility.HIDDEN, storedSetting()?.visibility)
            }
    }

    @Nested
    @DisplayName("진열 순서 일괄 변경 (요구사항 2.3)")
    inner class ReplaceDisplayOrder {
        private val latte = ProductId(2)
        private val mocha = ProductId(3)

        @BeforeEach
        fun prepareMoreProducts() =
            runTest {
                insertActiveProduct("카페라떼")
                insertActiveProduct("카페모카")
            }

        @Test
        fun `목록 순서대로 1부터 번호를 매기고 설정이 없던 상품은 설정을 만든다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(mocha, americano, latte)))

                assertEquals(mapOf(mocha to 1, americano to 2, latte to 3), storedOrders())
                assertEquals(3, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `목록에 없는 상품은 순서를 정하지 않은 상품이 된다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano, latte, mocha)))

                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(latte)))

                assertEquals(mapOf(latte to 1), storedOrders())
            }

        @Test
        fun `노출 여부는 건드리지 않는다`() =
            runTest {
                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))

                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(latte, americano)))

                val setting = assertNotNull(storedSetting())
                assertEquals(Visibility.HIDDEN, setting.visibility)
                assertEquals(2, setting.displayOrder)
                val latteSetting = assertNotNull(tx { displaySettingRepository.findByStoreAndProduct(gangnam, latte) })
                assertEquals(Visibility.VISIBLE, latteSetting.visibility)
            }

        @Test
        fun `빈 목록이면 이 매장의 모든 순서를 비운다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano, latte)))

                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, emptyList()))

                assertEquals(emptyMap(), storedOrders())
                assertEquals(2, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `다른 매장의 진열 순서는 바뀌지 않는다`() =
            runTest {
                val hongdae = StoreId(20)
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(hongdae, listOf(latte)))

                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano)))

                assertEquals(mapOf(latte to 1), storedOrders(hongdae))
            }

        @Test
        fun `매장 상품 목록이 바꾼 순서대로 정렬된다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(mocha, americano)))

                val products = storeProductQueryService.listProducts(gangnam)

                assertEquals(listOf(mocha, americano, latte), products.map { it.product.id })
                assertEquals(listOf(1, 2, null), products.map { it.displayOrder })
            }

        @Test
        fun `없는 상품이 있으면 전체를 거부하고 기존 순서는 그대로다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano, latte)))

                assertFailsWith<ProductNotFoundException> {
                    service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(mocha, ProductId(999))))
                }

                assertEquals(mapOf(americano to 1, latte to 2), storedOrders())
                assertEquals(2, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `판매 범위 밖 상품이 있으면 전체를 거부하고 기존 순서는 그대로다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano, latte)))
                execute("UPDATE products SET store_scope = 'LIMITED' WHERE id = ${mocha.value}")
                execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (${mocha.value}, 20)")

                assertFailsWith<ProductNotFoundException> {
                    service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(mocha, latte)))
                }

                assertEquals(mapOf(americano to 1, latte to 2), storedOrders())
                assertEquals(2, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `같은 상품이 두 번 있으면 거부하고 기존 순서는 그대로다`() =
            runTest {
                service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(americano, latte)))

                val exception =
                    assertFailsWith<DuplicateDisplayOrderProductException> {
                        service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, listOf(mocha, latte, mocha)))
                    }

                assertEquals(ErrorType.INVALID_INPUT, exception.errorCode.type)
                assertEquals(mapOf(americano to 1, latte to 2), storedOrders())
            }

        // 잠금이 없으면 두 요청이 각자 상대 상품의 순서를 비운 뒤(아직 커밋 전이라 비울 것이 없음) 자기 상품에 번호를 매겨
        // 두 순서가 섞인다. 두 목록이 상품을 공유하면 행 잠금 교착이 나고 Exposed가 진 쪽을 다시 실행해 섞임이 가려지므로,
        // 겹치지 않는 상품으로 목록을 만든다. 타이밍에 기대므로 여러 번 반복한다.
        @Test
        fun `같은 매장에 일괄 변경이 동시에 들어와도 결과는 둘 중 하나의 순서와 정확히 같다`() =
            runTest {
                insertActiveProduct("바닐라라떼")
                insertActiveProduct("콜드브루")
                val vanilla = ProductId(4)
                val coldBrew = ProductId(5)
                val first = listOf(americano, latte)
                val second = listOf(coldBrew, mocha, vanilla)

                repeat(20) {
                    execute("UPDATE store_display_settings SET display_order = NULL")

                    coroutineScope {
                        listOf(first, second)
                            .map { productIds ->
                                async(Dispatchers.IO) {
                                    service.replaceDisplayOrder(ReplaceDisplayOrderCommand(gangnam, productIds))
                                }
                            }.awaitAll()
                    }

                    val orders = storedOrders()
                    assertTrue(orders == numbered(first) || orders == numbered(second), "두 순서가 섞였다: $orders")
                }
            }

        private fun numbered(productIds: List<ProductId>) = productIds.mapIndexed { index, id -> id to index + 1 }.toMap()
    }

    private suspend fun insertActiveProduct(name: String) =
        execute(
            "INSERT INTO products (name, category_id, base_price, status, tracks_inventory) " +
                "VALUES ('$name', 2, 4500, 'ACTIVE', false)",
        )

    // 이 매장에서 순서가 정해진 상품과 그 순서.
    private suspend fun storedOrders(storeId: StoreId = gangnam): Map<ProductId, Int> =
        tx { displaySettingRepository.findAllByStore(storeId) }
            .mapNotNull { setting -> setting.displayOrder?.let { setting.productId to it } }
            .toMap()

    private suspend fun limitTo(storeIds: List<StoreId>) {
        execute("UPDATE products SET store_scope = 'LIMITED' WHERE id = ${americano.value}")
        storeIds.forEach {
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (${americano.value}, ${it.value})")
        }
    }

    private suspend fun storedSetting() = tx { displaySettingRepository.findByStoreAndProduct(gangnam, americano) }
}
