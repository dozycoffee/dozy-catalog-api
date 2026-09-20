package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.StoreId

// 판매 범위의 대상 매장 중 Store BC에 없는 매장이 있어 변경을 거부한다(요구사항 1.5).
class TargetStoreNotFoundException(
    missingStoreIds: Set<StoreId>,
) : DomainException(
        errorCode = ProductErrorCode.TARGET_STORE_NOT_FOUND,
        message = "존재하지 않는 매장입니다: ${missingStoreIds.joinToString { it.value.toString() }}",
    )
