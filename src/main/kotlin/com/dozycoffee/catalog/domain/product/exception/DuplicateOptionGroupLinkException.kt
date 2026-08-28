package com.dozycoffee.catalog.domain.product.exception

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.shared.DomainException

class DuplicateOptionGroupLinkException(
    optionGroupId: OptionGroupId,
) : DomainException(
        code = "DUPLICATE_OPTION_GROUP_LINK",
        message = "이미 연결된 옵션 그룹입니다: ${optionGroupId.value}",
    )
