package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.shared.ErrorCode
import com.dozycoffee.catalog.domain.shared.ErrorType

enum class CategoryErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    CATEGORY_HAS_CHILDREN(ErrorType.CONFLICT),
    CATEGORY_STILL_REFERENCED(ErrorType.CONFLICT),
    INVALID_PARENT_CATEGORY(ErrorType.BUSINESS_RULE_VIOLATION),
    ;

    override val code: String get() = name
}
