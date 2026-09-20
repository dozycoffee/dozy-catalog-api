package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class ProductErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    INVALID_PRODUCT_STATUS_TRANSITION(ErrorType.CONFLICT),
    PRODUCT_NOT_DELETABLE(ErrorType.CONFLICT),
    DUPLICATE_OPTION_GROUP_LINK(ErrorType.CONFLICT),
    NO_SELECTABLE_OPTION(ErrorType.BUSINESS_RULE_VIOLATION),
    PRODUCT_OPTION_GROUP_NOT_LINKED(ErrorType.NOT_FOUND),
    INVALID_OPTION_GROUP_ORDER(ErrorType.INVALID_INPUT),
    OPTION_KEY_NOT_FOUND(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}
