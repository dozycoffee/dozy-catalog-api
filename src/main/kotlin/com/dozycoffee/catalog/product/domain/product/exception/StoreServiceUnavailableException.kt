package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException

// 대상 매장을 Store 서비스에 확인하지 못해(장애, 시간 초과, 계약 불일치) 판매 범위 변경을 거부한다(요구사항 1.5).
// 확인 없이 통과시키면 없는 매장이 판매 범위에 들어가므로, 확인하지 못하면 거부한다.
class StoreServiceUnavailableException(
    cause: Throwable? = null,
) : DomainException(
        errorCode = ProductErrorCode.STORE_SERVICE_UNAVAILABLE,
        message = "매장을 확인하는 Store 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요",
        cause = cause,
    )
