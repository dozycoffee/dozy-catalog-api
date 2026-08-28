package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.shared.DomainException

class ProductNotDeletableException(
    productId: ProductId,
    status: ProductStatus,
) : DomainException(
        code = "PRODUCT_NOT_DELETABLE",
        message = "Draft 상태가 아닌 상품은 삭제할 수 없습니다: ${productId.value} (현재 상태: $status)",
    )
