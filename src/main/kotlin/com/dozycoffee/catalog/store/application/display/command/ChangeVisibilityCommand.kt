package com.dozycoffee.catalog.store.application.display.command

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.store.domain.display.Visibility

// 점주의 숨김·노출 전환 입력(요구사항 2.2). 숨김과 노출 되돌리기는 같은 값의 전환이라 하나의 유스케이스로 둔다.
data class ChangeVisibilityCommand(
    val storeId: StoreId,
    val productId: ProductId,
    val visibility: Visibility,
)
