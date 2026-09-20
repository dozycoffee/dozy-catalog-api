package com.dozycoffee.catalog.product.domain.optiongroup.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId

class OptionGroupStillReferencedException(
    optionGroupId: OptionGroupId,
) : DomainException(
        errorCode = OptionGroupErrorCode.OPTION_GROUP_STILL_REFERENCED,
        message = "다른 상품이 참조 중인 옵션 그룹은 삭제할 수 없습니다: ${optionGroupId.value}",
    )
