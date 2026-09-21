package com.dozycoffee.catalog.product.application.optiongroup.query

import com.dozycoffee.catalog.common.paging.requireIdsWithinLimit
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId

// 옵션 그룹 목록 조회 조건(요구사항 1.9). 지정하지 않은 조건은 거르지 않고, 여러 조건을 함께 주면 모두 만족하는 것만 남는다.
// keyword는 이름의 대소문자 무시 부분 일치다.
data class OptionGroupFilter(
    val ids: Set<OptionGroupId>? = null,
    val keyword: String? = null,
) {
    init {
        requireIdsWithinLimit(ids)
    }
}
