package com.dozycoffee.catalog.core

enum class SharedErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    INVALID_MONEY_AMOUNT(ErrorType.INVALID_INPUT),
    VERSION_CONFLICT(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
