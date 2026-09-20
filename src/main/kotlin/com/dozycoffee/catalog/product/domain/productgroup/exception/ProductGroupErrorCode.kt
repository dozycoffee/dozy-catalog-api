package com.dozycoffee.catalog.product.domain.productgroup.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class ProductGroupErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    PRODUCT_GROUP_NOT_FOUND(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}
