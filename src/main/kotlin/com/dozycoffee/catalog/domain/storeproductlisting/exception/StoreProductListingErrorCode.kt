package com.dozycoffee.catalog.domain.storeproductlisting.exception

import com.dozycoffee.catalog.domain.shared.ErrorCode
import com.dozycoffee.catalog.domain.shared.ErrorType

enum class StoreProductListingErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    STOCK_STATUS_NOT_MANUALLY_EDITABLE(ErrorType.BUSINESS_RULE_VIOLATION),
    ;

    override val code: String get() = name
}
