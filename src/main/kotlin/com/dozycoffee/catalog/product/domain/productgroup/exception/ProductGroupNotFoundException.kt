package com.dozycoffee.catalog.product.domain.productgroup.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId

class ProductGroupNotFoundException(
    productGroupId: ProductGroupId,
) : DomainException(
        errorCode = ProductGroupErrorCode.PRODUCT_GROUP_NOT_FOUND,
        message = "상품 그룹을 찾을 수 없습니다: ${productGroupId.value}",
    )
