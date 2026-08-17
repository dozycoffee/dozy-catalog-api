package com.dozycoffee.catalog.domain.shared

// 원(KRW) 단위 정수 금액. 전 매장 동일가 정책이라 통화 구분은 두지 않는다.
@JvmInline
value class Money(
    val amount: Long,
) {
    init {
        if (amount < 0) throw InvalidMoneyAmountException(amount)
    }

    operator fun plus(other: Money): Money = Money(amount + other.amount)

    companion object {
        val ZERO = Money(0)
    }
}
