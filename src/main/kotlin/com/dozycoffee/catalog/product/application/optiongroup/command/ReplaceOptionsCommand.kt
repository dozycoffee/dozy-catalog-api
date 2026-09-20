package com.dozycoffee.catalog.product.application.optiongroup.command

import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId

// 옵션 목록 즉시 교체(요구사항 1.9 / 시나리오 S5). 입력한 목록 전체로 그 자리에서 교체하며,
// 목록 순서가 곧 노출 순서다. 교체로 사라지는 옵션 키의 상품별 예외는 함께 삭제된다.
data class ReplaceOptionsCommand(
    val optionGroupId: OptionGroupId,
    val version: Long,
    val options: List<Option>,
)
