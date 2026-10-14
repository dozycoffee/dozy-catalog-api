package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.StoreId

// 판매 범위의 대상 매장 중 폐점한 매장이 있어 변경을 거부한다(요구사항 1.5).
class TargetStoreClosedException(
    closedStoreIds: Set<StoreId>,
) : DomainException(
        errorCode = ProductErrorCode.TARGET_STORE_CLOSED,
        message = "폐점한 매장은 판매 범위에 지정할 수 없습니다: ${closedStoreIds.joinToString { it.value.toString() }}",
    )
