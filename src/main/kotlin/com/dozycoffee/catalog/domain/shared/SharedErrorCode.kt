package com.dozycoffee.catalog.domain.shared

enum class SharedErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    INVALID_MONEY_AMOUNT(ErrorType.INVALID_INPUT),
    VERSION_CONFLICT(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
