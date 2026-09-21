package com.dozycoffee.catalog.store.application.display.command

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 점주의 진열 순서 일괄 변경 입력(요구사항 2.3). productIds는 화면에서 완성한 최종 순서 전체다.
data class ReplaceDisplayOrderCommand(
    val storeId: StoreId,
    val productIds: List<ProductId>,
)
