package com.dozycoffee.catalog.domain.storeproductlisting.service

import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.product.model.StoreId
import com.dozycoffee.catalog.domain.storeproductlisting.model.StockStatus
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreProductListing
import com.dozycoffee.catalog.domain.storeproductlisting.model.StoreVisibility
import com.dozycoffee.catalog.domain.storeproductlisting.model.Visibility

// 시나리오 3장의 매장별 노출 판단 로직. 어느 단계든 조건에 맞지 않으면 그 즉시
// 비노출로 종료한다. Product와 StoreProductListing 양쪽을 봐야 하는 판단이라
// 어느 한 애그리거트에 두지 않고 도메인 서비스로 분리했다.
object ProductVisibilityPolicy {
    // listing이 null이면 해당 매장에 개별 설정이 없다는 뜻 — 기본값(노출, 판매중)으로
    // 간주한다(Lazy 생성).
    fun resolve(
        product: Product,
        storeId: StoreId,
        listing: StoreProductListing?,
    ): StoreVisibility {
        if (product.status != ProductStatus.ACTIVE) {
            return StoreVisibility.NotVisible
        }
        if (!product.storeScope.covers(storeId)) {
            return StoreVisibility.NotVisible
        }
        if (listing == null) {
            return StoreVisibility.Visible(StockStatus.ON_SALE)
        }
        if (listing.visibility == Visibility.HIDDEN) {
            return StoreVisibility.NotVisible
        }
        return StoreVisibility.Visible(listing.stockStatus)
    }
}
