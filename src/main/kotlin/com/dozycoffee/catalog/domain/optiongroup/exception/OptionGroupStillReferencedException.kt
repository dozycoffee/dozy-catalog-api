package com.dozycoffee.catalog.domain.optiongroup.exception

import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.shared.DomainException

class OptionGroupStillReferencedException(
    optionGroupId: OptionGroupId,
) : DomainException(
        code = "OPTION_GROUP_STILL_REFERENCED",
        message = "다른 상품이 참조 중인 옵션 그룹은 삭제할 수 없습니다: ${optionGroupId.value}",
    )
