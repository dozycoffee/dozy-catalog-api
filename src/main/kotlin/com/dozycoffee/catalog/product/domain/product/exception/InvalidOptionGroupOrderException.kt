package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId

class InvalidOptionGroupOrderException(
    productId: ProductId,
    linked: List<OptionGroupId>,
    requested: List<OptionGroupId>,
) : DomainException(
        errorCode = ProductErrorCode.INVALID_OPTION_GROUP_ORDER,
        message =
            "상품(${productId.value})의 옵션 그룹 순서는 연결된 옵션 그룹 전체를 한 번씩 포함해야 합니다: " +
                "연결 ${linked.map { it.value }}, 요청 ${requested.map { it.value }}",
    )
