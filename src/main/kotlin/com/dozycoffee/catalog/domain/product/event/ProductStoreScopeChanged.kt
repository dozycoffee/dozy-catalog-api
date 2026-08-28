package com.dozycoffee.catalog.domain.product.event

import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.shared.DomainEvent

// 어떤 매장이 새로 제외됐는지는 domain이 전체 매장 목록을 알지 못해 계산할 수
// 없다. 구독 측(application)이 기존 StoreProductListing과 newScope를 대조해
// 판단하도록 이벤트에는 새 판매범위만 담는다.
class ProductStoreScopeChanged(
    val productId: ProductId,
    val newScope: StoreScope,
) : DomainEvent()
