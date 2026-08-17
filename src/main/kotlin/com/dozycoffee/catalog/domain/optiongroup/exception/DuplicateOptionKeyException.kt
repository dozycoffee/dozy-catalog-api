package com.dozycoffee.catalog.domain.optiongroup.exception

import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.shared.DomainException

class DuplicateOptionKeyException(
    optionKey: OptionKey,
) : DomainException(
        code = "DUPLICATE_OPTION_KEY",
        message = "옵션 그룹 내에서 optionKey는 유일해야 합니다: ${optionKey.value}",
    )
