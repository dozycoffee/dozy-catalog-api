package com.dozycoffee.catalog.domain.storedisplay.model

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("StoreDisplaySetting")
class StoreDisplaySettingTest {
    @Test
    fun `진열 설정을 처음 만들면 노출 상태로 시작한다`() {
        val setting = setting()

        assertEquals(Visibility.VISIBLE, setting.visibility)
        assertNull(setting.displayOrder)
    }

    @Test
    fun `상품을 숨겼다가 다시 노출할 수 있다`() {
        val setting = setting()

        setting.hide()
        assertEquals(Visibility.HIDDEN, setting.visibility)

        setting.show()
        assertEquals(Visibility.VISIBLE, setting.visibility)
    }

    @Test
    fun `진열 순서를 조정한다`() {
        val setting = setting()

        setting.changeDisplayOrder(3)

        assertEquals(3, setting.displayOrder)
    }

    private fun setting() =
        StoreDisplaySetting(
            id = StoreDisplaySettingId(1),
            storeId = StoreId(1),
            productId = ProductId(1),
        )
}
