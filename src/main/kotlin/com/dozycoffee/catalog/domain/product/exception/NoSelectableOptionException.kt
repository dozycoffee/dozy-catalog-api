package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.product.model.ProductId

class NoSelectableOptionException(
    productId: ProductId,
    optionGroupId: OptionGroupId,
) : DomainException(
        errorCode = ProductErrorCode.NO_SELECTABLE_OPTION,
        message = "상품(${productId.value})에서 옵션 그룹(${optionGroupId.value})의 선택 가능한 옵션이 0개가 됩니다",
    )
