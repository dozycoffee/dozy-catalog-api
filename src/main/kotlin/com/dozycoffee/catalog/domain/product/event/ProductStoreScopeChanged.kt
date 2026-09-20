package com.dozycoffee.catalog.domain.product.event

import com.dozycoffee.catalog.core.DomainEvent
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.StoreScope

// 어떤 매장이 새로 제외됐는지는 domain이 전체 매장 목록을 알지 못해 계산할 수
// 없다. 구독 측(application)이 기존 StoreDisplaySetting·StoreProductAvailability(OWNER 출처)와
// newScope를 대조해 판단하도록 이벤트에는 새 판매범위만 담는다. INVENTORY 출처 판매 가능 여부는
// 판매 범위와 무관하게 유지한다(요구사항 1.5).
class ProductStoreScopeChanged(
    val productId: ProductId,
    val newScope: StoreScope,
) : DomainEvent()
