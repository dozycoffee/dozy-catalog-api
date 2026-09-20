package com.dozycoffee.catalog.product.application.product.command

import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.StoreScope

// 판매 범위 변경(요구사항 1.5, 시나리오 S4). 대상 매장을 비운 Limited도 허용한다 —
// 어떤 매장에도 노출되지 않고 모든 매장의 설정이 정리된다.
// version은 화면이 보고 있던 버전이다. 그 사이 다른 변경이 반영됐으면 거부한다(ADR-0013).
data class ChangeStoreScopeCommand(
    val productId: ProductId,
    val version: Long,
    val scope: StoreScope,
)
