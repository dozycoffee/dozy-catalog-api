package com.dozycoffee.catalog.store.domain.display.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class StoreDisplaySettingErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    DUPLICATE_DISPLAY_ORDER_PRODUCT(ErrorType.INVALID_INPUT),
    ;

    override val code: String get() = name
}
