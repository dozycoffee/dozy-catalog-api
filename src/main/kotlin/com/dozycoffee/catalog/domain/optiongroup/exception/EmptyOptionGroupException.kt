package com.dozycoffee.catalog.domain.optiongroup.exception

import com.dozycoffee.catalog.domain.shared.DomainException

class EmptyOptionGroupException :
    DomainException(
        code = "EMPTY_OPTION_GROUP",
        message = "옵션 그룹은 최소 1개의 옵션을 가져야 합니다",
    )
