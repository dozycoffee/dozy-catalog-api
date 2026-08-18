package com.dozycoffee.catalog.domain.productgroup

import com.dozycoffee.catalog.domain.productgroup.event.ProductGroupDeleted
import com.dozycoffee.catalog.domain.shared.AggregateRoot

class ProductGroup internal constructor(
    id: ProductGroupId,
    name: String,
) : AggregateRoot<ProductGroupId>(id) {
    var name: String = name
        private set

    fun rename(newName: String) {
        this.name = newName
    }

    // 삭제는 항상 허용된다(참조 중이어도 거부하지 않음) — 참조하던 상품에서
    // 참조를 제거하는 건 이 이벤트를 구독하는 쪽(application 레이어)의 책임.
    fun delete() {
        registerEvent(ProductGroupDeleted(id))
    }
}
