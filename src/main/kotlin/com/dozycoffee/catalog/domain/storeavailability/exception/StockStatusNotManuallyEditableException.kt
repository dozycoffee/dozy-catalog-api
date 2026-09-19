package com.dozycoffee.catalog.domain.storeavailability.exception

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.shared.DomainException
import com.dozycoffee.catalog.domain.shared.StoreId

class StockStatusNotManuallyEditableException(
    storeId: StoreId,
    productId: ProductId,
) : DomainException(
        errorCode = StoreProductAvailabilityErrorCode.STOCK_STATUS_NOT_MANUALLY_EDITABLE,
        message =
            "재고 추적 상품의 품절 상태는 점주가 직접 변경할 수 없습니다: " +
                "매장 ${storeId.value}, 상품 ${productId.value}",
    )
