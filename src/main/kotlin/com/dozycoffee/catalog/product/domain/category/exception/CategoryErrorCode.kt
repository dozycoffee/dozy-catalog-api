package com.dozycoffee.catalog.product.domain.category.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class CategoryErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    CATEGORY_HAS_CHILDREN(ErrorType.CONFLICT),
    CATEGORY_STILL_REFERENCED(ErrorType.CONFLICT),
    INVALID_PARENT_CATEGORY(ErrorType.INVALID_INPUT),
    CATEGORY_WITH_CHILDREN_NOT_DEMOTABLE(ErrorType.CONFLICT),
    CATEGORY_NOT_ASSIGNABLE(ErrorType.INVALID_INPUT),
    REFERENCED_CATEGORY_NOT_PROMOTABLE(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
