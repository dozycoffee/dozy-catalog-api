package com.dozycoffee.catalog.product.domain.productgroup

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("ProductGroup")
class ProductGroupTest {
    @Test
    fun `이름 변경은 즉시 반영된다`() {
        val group = ProductGroup(ProductGroupId(1), "여름 시즌")

        group.rename("겨울 시즌")

        assertEquals("겨울 시즌", group.name)
    }
}
