package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey

class OptionKeyNotFoundException(
    optionGroupId: OptionGroupId,
    optionKey: OptionKey,
) : DomainException(
        errorCode = ProductErrorCode.OPTION_KEY_NOT_FOUND,
        message = "옵션 그룹(${optionGroupId.value})에 없는 옵션 키입니다: ${optionKey.value}",
    )
