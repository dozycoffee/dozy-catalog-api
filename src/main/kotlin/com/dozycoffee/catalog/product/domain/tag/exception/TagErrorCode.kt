package com.dozycoffee.catalog.product.domain.tag.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class TagErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    TAG_NOT_FOUND(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}
