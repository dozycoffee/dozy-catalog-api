package com.dozycoffee.catalog.domain.shared

class InvalidMoneyAmountException(
    amount: Long,
) : DomainException(
        code = "INVALID_MONEY_AMOUNT",
        message = "금액은 0 이상이어야 합니다: $amount",
    )
