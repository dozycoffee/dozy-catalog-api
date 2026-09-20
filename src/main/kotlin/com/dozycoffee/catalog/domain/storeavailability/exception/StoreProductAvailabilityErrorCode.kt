package com.dozycoffee.catalog.domain.storeavailability.exception

import com.dozycoffee.catalog.core.ErrorCode
import com.dozycoffee.catalog.core.ErrorType

enum class StoreProductAvailabilityErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    STOCK_STATUS_NOT_MANUALLY_EDITABLE(ErrorType.BUSINESS_RULE_VIOLATION),
    INVENTORY_EVENT_NOT_APPLICABLE(ErrorType.BUSINESS_RULE_VIOLATION),
    ;

    override val code: String get() = name
}
