package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.shared.DomainException

class ProductOptionGroupNotLinkedException(
    productId: ProductId,
    optionGroupId: OptionGroupId,
) : DomainException(
        errorCode = ProductErrorCode.PRODUCT_OPTION_GROUP_NOT_LINKED,
        message = "상품(${productId.value})에 연결되지 않은 옵션 그룹입니다: ${optionGroupId.value}",
    )
