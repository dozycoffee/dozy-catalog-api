package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.shared.DomainException

class LinkedOptionGroupNotFoundException(
    productId: ProductId,
    missing: Set<OptionGroupId>,
) : DomainException(
        errorCode = ProductErrorCode.LINKED_OPTION_GROUP_NOT_FOUND,
        message = "상품(${productId.value})에 연결된 옵션 그룹을 찾을 수 없습니다: ${missing.map { it.value }}",
    )
