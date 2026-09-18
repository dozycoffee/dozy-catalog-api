package com.dozycoffee.catalog.domain.storeproductlisting.exception

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.shared.DomainException

class StockStatusNotManuallyEditableException(
    storeId: StoreId,
    productId: ProductId,
) : DomainException(
        errorCode = StoreProductListingErrorCode.STOCK_STATUS_NOT_MANUALLY_EDITABLE,
        message =
            "재고 추적 상품의 품절 상태는 점주가 직접 변경할 수 없습니다: " +
                "매장 ${storeId.value}, 상품 ${productId.value}",
    )
