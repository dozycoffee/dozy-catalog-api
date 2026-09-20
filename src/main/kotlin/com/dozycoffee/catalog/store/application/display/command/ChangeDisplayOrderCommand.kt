package com.dozycoffee.catalog.store.application.display.command

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId

// 점주의 진열 순서 변경 입력(요구사항 2.3). displayOrder가 null이면 순서를 지정하지 않은 상태로 되돌린다.
data class ChangeDisplayOrderCommand(
    val storeId: StoreId,
    val productId: ProductId,
    val displayOrder: Int?,
)
