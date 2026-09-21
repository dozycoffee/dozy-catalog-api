package com.dozycoffee.catalog.store.application.display

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.display.command.ChangeDisplayOrderCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.store.domain.display.Visibility
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
import kotlin.test.assertNull

@DisplayName("S6. 점주의 진열 설정 변경 (요구사항 2.2, 2.3)")
class StoreDisplaySettingApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: StoreDisplaySettingApplicationService

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

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
        fun `진열 순서를 바꾸면 저장되고 비우면 순서 없음으로 돌아간다`() =
            runTest {
                service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, americano, 3))
                assertEquals(3, storedSetting()?.displayOrder)

                service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, americano, null))

                assertNull(storedSetting()?.displayOrder)
            }

        @Test
        fun `노출 여부와 진열 순서는 서로 덮어쓰지 않는다`() =
            runTest {
                service.changeVisibility(ChangeVisibilityCommand(gangnam, americano, Visibility.HIDDEN))

                service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, americano, 2))

                val setting = assertNotNull(storedSetting())
                assertEquals(Visibility.HIDDEN, setting.visibility)
                assertEquals(2, setting.displayOrder)
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

        @Test
        fun `없는 상품의 진열 순서를 바꾸려 하면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, ProductId(999), 1))
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
        fun `판매 범위 밖 상품의 진열 순서를 바꾸려 하면 거부하고 개별 설정을 만들지 않는다`() =
            runTest {
                limitTo(listOf(StoreId(20)))

                assertFailsWith<ProductNotFoundException> {
                    service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, americano, 1))
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
                service.changeDisplayOrder(ChangeDisplayOrderCommand(gangnam, americano, 1))

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

    private suspend fun limitTo(storeIds: List<StoreId>) {
        execute("UPDATE products SET store_scope = 'LIMITED' WHERE id = ${americano.value}")
        storeIds.forEach {
            execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (${americano.value}, ${it.value})")
        }
    }

    private suspend fun storedSetting() = tx { displaySettingRepository.findByStoreAndProduct(gangnam, americano) }
}
