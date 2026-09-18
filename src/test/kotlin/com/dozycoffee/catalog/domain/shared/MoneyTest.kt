package com.dozycoffee.catalog.domain.shared

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("Money")
class MoneyTest {
    @Test
    fun `음수 금액은 생성할 수 없다`() {
        assertFailsWith<InvalidMoneyAmountException> { Money(-1) }
    }

    @Test
    fun `0원은 유효한 금액이다`() {
        assertEquals(0, Money(0).amount)
        assertEquals(Money(0), Money.ZERO)
    }

    @Test
    fun `두 금액을 더한다`() {
        assertEquals(Money(4500), Money(4000) + Money(500))
    }

    @Test
    fun `덧셈 결과도 Money라 음수가 될 수 없다`() {
        // plus는 양수끼리만 더하므로 음수가 나올 수 없고, 애초에 음수 Money를
        // 만들 수 없어 피연산자로도 들어올 수 없다.
        assertEquals(Money(0), Money.ZERO + Money.ZERO)
    }
}
