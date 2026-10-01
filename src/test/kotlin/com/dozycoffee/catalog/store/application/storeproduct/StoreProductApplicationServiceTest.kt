package com.dozycoffee.catalog.store.application.storeproduct

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StockStatus
import com.dozycoffee.catalog.store.domain.availability.exception.StockStatusNotManuallyEditableException
import com.dozycoffee.catalog.store.domain.display.Visibility
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 설정 변경과 그 결과 조회. 변경 규칙 자체는 진열 설정·판매 가능 여부의 유스케이스 테스트가 덮으므로,
// 여기서는 결과가 상품 상태와 무관하게 점주의 설정으로 나오는지와 거부되면 아무것도 남지 않는지를 본다.
@DisplayName("S6. 점주의 설정 변경과 그 결과 (요구사항 2.2, 2.5)")
class StoreProductApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: StoreProductApplicationService

    private val gangnam = StoreId(10)
    private val hongdae = StoreId(20)

    @BeforeEach
    fun prepareCategory() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        }

    @Nested
    @DisplayName("숨김·노출 전환")
    inner class ChangeVisibility {
        @ParameterizedTest
        @ValueSource(strings = ["ACTIVE", "DRAFT", "DISCONTINUED"])
        fun `판매 범위에 든 상품이면 상태와 무관하게 바꾸고 바꾼 설정을 돌려준다`(status: String) =
            runTest {
                insertProduct(status = status, tracksInventory = false)

                val result = service.changeVisibility(ChangeVisibilityCommand(gangnam, ProductId(1), Visibility.HIDDEN))

                assertEquals(StoreVisibility.NotVisible, result.visibility)
                assertEquals(1, count("SELECT count(*) FROM store_display_settings WHERE visibility = 'HIDDEN'"))
            }

        @Test
        fun `노출로 두면 판매 가능 여부는 출처별 기본값이다`() =
            runTest {
                insertProduct(status = "DISCONTINUED", tracksInventory = true)

                val result = service.changeVisibility(ChangeVisibilityCommand(gangnam, ProductId(1), Visibility.VISIBLE))

                assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result.visibility)
            }

        @Test
        fun `판매 범위 밖 상품이면 거부하고 아무것도 저장하지 않는다`() =
            runTest {
                insertProduct(status = "ACTIVE", tracksInventory = false, storeScope = "LIMITED")
                execute("INSERT INTO product_target_stores (product_id, store_id) VALUES (1, ${hongdae.value})")

                assertFailsWith<ProductNotFoundException> {
                    service.changeVisibility(ChangeVisibilityCommand(gangnam, ProductId(1), Visibility.HIDDEN))
                }
                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `없는 상품이면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.changeVisibility(ChangeVisibilityCommand(gangnam, ProductId(99), Visibility.HIDDEN))
                }
            }
    }

    @Nested
    @DisplayName("수동 품절")
    inner class ChangeStockStatus {
        @Test
        fun `단종 상품이어도 바꾸고 바꾼 판매 가능 여부를 돌려준다`() =
            runTest {
                insertProduct(status = "DISCONTINUED", tracksInventory = false)

                val result = service.changeStockStatus(ChangeStockStatusByOwnerCommand(gangnam, ProductId(1), StockStatus.SOLD_OUT))

                assertEquals(StoreVisibility.Visible(StockStatus.SOLD_OUT), result.visibility)
                assertEquals(1, count("SELECT count(*) FROM store_product_availabilities WHERE stock_status = 'SOLD_OUT'"))
            }

        @Test
        fun `숨긴 상품이면 품절로 바꿔도 비노출로 나온다`() =
            runTest {
                insertProduct(status = "ACTIVE", tracksInventory = false)
                execute("INSERT INTO store_display_settings (store_id, product_id, visibility) VALUES (${gangnam.value}, 1, 'HIDDEN')")

                val result = service.changeStockStatus(ChangeStockStatusByOwnerCommand(gangnam, ProductId(1), StockStatus.SOLD_OUT))

                assertEquals(StoreVisibility.NotVisible, result.visibility)
            }

        @Test
        fun `재고 추적 상품이면 거부하고 아무것도 저장하지 않는다`() =
            runTest {
                insertProduct(status = "ACTIVE", tracksInventory = true)

                assertFailsWith<StockStatusNotManuallyEditableException> {
                    service.changeStockStatus(ChangeStockStatusByOwnerCommand(gangnam, ProductId(1), StockStatus.ON_SALE))
                }
                assertEquals(0, count("SELECT count(*) FROM store_product_availabilities"))
            }
    }

    private suspend fun insertProduct(
        status: String,
        tracksInventory: Boolean,
        storeScope: String = "ALL",
    ) = execute(
        """
        INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
        VALUES ('아메리카노', 2, 4500, '$status', '$storeScope', $tracksInventory)
        """.trimIndent(),
    )
}
