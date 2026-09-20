package com.dozycoffee.catalog.domain.storeavailability.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.domain.product.model.ProductId

// 재고 미추적 상품에 재고 이벤트가 도착한 경우. 사용자 요청이 아니라 연동 데이터 불일치라
// 이벤트 구독 측이 잡아서 기록하고 넘긴다.
class InventoryEventNotApplicableException(
    storeId: StoreId,
    productId: ProductId,
) : DomainException(
        errorCode = StoreProductAvailabilityErrorCode.INVENTORY_EVENT_NOT_APPLICABLE,
        message =
            "재고 미추적 상품에는 재고 이벤트를 반영할 수 없습니다: " +
                "매장 ${storeId.value}, 상품 ${productId.value}",
    )
