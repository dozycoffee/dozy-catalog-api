package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.shared.DomainException

class InvalidProductStatusTransitionException(
    productId: ProductId,
    from: ProductStatus,
    to: ProductStatus,
) : DomainException(
        code = "INVALID_PRODUCT_STATUS_TRANSITION",
        message = "상품(${productId.value})을 $from 에서 $to 로 전환할 수 없습니다",
    )
