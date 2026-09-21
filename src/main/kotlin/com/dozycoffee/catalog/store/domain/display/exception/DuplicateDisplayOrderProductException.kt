package com.dozycoffee.catalog.store.domain.display.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 진열 순서 목록에 같은 상품이 두 번 있는 경우. 한 상품이 두 자리를 차지할 수 없으므로 요청 값 자체가 잘못됐다.
class DuplicateDisplayOrderProductException(
    storeId: StoreId,
    productId: ProductId,
) : DomainException(
        errorCode = StoreDisplaySettingErrorCode.DUPLICATE_DISPLAY_ORDER_PRODUCT,
        message = "진열 순서에 같은 상품이 두 번 있습니다: 매장 ${storeId.value}, 상품 ${productId.value}",
    )
