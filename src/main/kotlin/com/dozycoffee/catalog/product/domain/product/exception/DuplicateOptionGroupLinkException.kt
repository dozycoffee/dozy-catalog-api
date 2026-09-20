package com.dozycoffee.catalog.product.domain.product.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId

class DuplicateOptionGroupLinkException(
    optionGroupId: OptionGroupId,
) : DomainException(
        errorCode = ProductErrorCode.DUPLICATE_OPTION_GROUP_LINK,
        message = "이미 연결된 옵션 그룹입니다: ${optionGroupId.value}",
    )
