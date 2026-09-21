package com.dozycoffee.catalog.store.domain.display

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.fixture.displaySetting
import com.dozycoffee.catalog.product.domain.product.ProductId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("StoreDisplaySetting")
class StoreDisplaySettingTest {
    @Test
    fun `진열 설정을 처음 만들면 노출 상태로 시작한다`() {
        // 생성자의 기본값을 검증하므로 픽스처 대신 생성자를 직접 호출한다.
        val setting = StoreDisplaySetting(StoreDisplaySettingId(1), StoreId(1), ProductId(1))

        assertEquals(Visibility.VISIBLE, setting.visibility)
        assertNull(setting.displayOrder)
    }

    @Test
    fun `상품을 숨겼다가 다시 노출할 수 있다`() {
        val setting = displaySetting(visibility = Visibility.VISIBLE)

        setting.hide()
        assertEquals(Visibility.HIDDEN, setting.visibility)

        setting.show()
        assertEquals(Visibility.VISIBLE, setting.visibility)
    }
}
