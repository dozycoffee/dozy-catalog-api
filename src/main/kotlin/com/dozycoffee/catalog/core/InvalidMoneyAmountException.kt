package com.dozycoffee.catalog.core

class InvalidMoneyAmountException(
    amount: Long,
) : DomainException(
        errorCode = SharedErrorCode.INVALID_MONEY_AMOUNT,
        message = "금액은 0 이상이어야 합니다: $amount",
    )
