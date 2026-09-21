package com.dozycoffee.catalog.store.domain.display

import com.dozycoffee.catalog.core.ErrorType
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.display.exception.DuplicateDisplayOrderProductException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("StoreDisplayOrder")
class StoreDisplayOrderTest {
    private val store = StoreId(1)

    @Test
    fun `목록 순서대로 1부터 번호를 매긴다`() {
        val order = StoreDisplayOrder(store, listOf(ProductId(15), ProductId(12), ProductId(20)))

        assertEquals(
            listOf(ProductId(15) to 1, ProductId(12) to 2, ProductId(20) to 3),
            order.numbered,
        )
    }

    @Test
    fun `빈 목록도 허용한다`() {
        val order = StoreDisplayOrder(store, emptyList())

        assertTrue(order.numbered.isEmpty())
    }

    @Test
    fun `같은 상품이 두 번 있으면 거부한다`() {
        val exception =
            assertFailsWith<DuplicateDisplayOrderProductException> {
                StoreDisplayOrder(store, listOf(ProductId(1), ProductId(2), ProductId(1)))
            }

        assertEquals(ErrorType.INVALID_INPUT, exception.errorCode.type)
    }
}
