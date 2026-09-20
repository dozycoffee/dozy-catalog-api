package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductStatus

class InvalidProductStatusTransitionException(
    productId: ProductId,
    from: ProductStatus,
    to: ProductStatus,
) : DomainException(
        errorCode = ProductErrorCode.INVALID_PRODUCT_STATUS_TRANSITION,
        message = "상품(${productId.value})을 $from 에서 $to 로 전환할 수 없습니다",
    )
