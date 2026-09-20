package com.dozycoffee.catalog.product.domain.optiongroup.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class OptionGroupErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    DUPLICATE_OPTION_KEY(ErrorType.INVALID_INPUT),
    EMPTY_OPTION_GROUP(ErrorType.BUSINESS_RULE_VIOLATION),
    OPTION_GROUP_STILL_REFERENCED(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
