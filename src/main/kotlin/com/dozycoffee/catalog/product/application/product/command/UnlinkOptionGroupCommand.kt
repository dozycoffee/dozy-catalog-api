package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 상품에서 옵션 그룹 연결을 해제한다(요구사항 1.9). 그 연결에 달린 이 상품의 예외(가격·제외)도
// 함께 사라진다. 다시 연결해도 예외는 복원되지 않는다.
data class UnlinkOptionGroupCommand(
    val productId: ProductId,
    val version: Long,
    val optionGroupId: OptionGroupId,
)
