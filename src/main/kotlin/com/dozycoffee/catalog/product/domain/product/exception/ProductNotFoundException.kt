package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.product.ProductId

class ProductNotFoundException(
    productId: ProductId,
) : DomainException(
        errorCode = ProductErrorCode.PRODUCT_NOT_FOUND,
        message = "상품을 찾을 수 없습니다: ${productId.value}",
    )
