package com.dozycoffee.catalog.product.domain.optiongroup.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId

class OptionGroupNotFoundException(
    optionGroupId: OptionGroupId,
) : DomainException(
        errorCode = OptionGroupErrorCode.OPTION_GROUP_NOT_FOUND,
        message = "옵션 그룹을 찾을 수 없습니다: ${optionGroupId.value}",
    )
