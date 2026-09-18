package com.dozycoffee.catalog.domain.category.exception

import com.dozycoffee.catalog.domain.shared.ErrorCode
import com.dozycoffee.catalog.domain.shared.ErrorType

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
