package com.dozycoffee.catalog.store.application.policy

import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.store.domain.availability.AvailabilitySource
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailability
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingId

// 판매 범위가 바뀌어 대상에서 빠진 매장의 설정 중 무엇을 지울지 고른다(요구사항 1.5).
// 진열 설정은 모두 지우고, 판매 가능 여부는 OWNER 출처만 지운다 — INVENTORY 출처는
// 재고관리 서비스가 주인이라 범위와 무관하게 유지한다. StoreDisplaySetting과
// StoreProductAvailability를 함께 봐야 하고, 즉시 변경과 예약 적용이 같은 규칙을 써야 해서
// 이벤트 핸들러 안에 두지 않고 I/O 없는 정책으로 분리했다(ADR-0012). 실제 삭제는 호출 측이 한다.
object StoreScopeCleanupPolicy {
    // 입력은 모두 productId 한 상품의 설정이어야 한다. 섞여 들어오면 호출 측 조회가 잘못된
    // 것(프로그래밍 오류)이라 사용자 요청 오류로 매핑되는 DomainException이 아니라
    // IllegalArgumentException으로 거부한다.
    fun selectTargets(
        productId: ProductId,
        newScope: StoreScope,
        displaySettings: List<StoreDisplaySetting>,
        availabilities: List<StoreProductAvailability>,
    ): StoreScopeCleanupTargets {
        require(displaySettings.all { it.productId == productId }) {
            "다른 상품의 진열 설정이 섞여 있습니다: 상품 ${productId.value}"
        }
        require(availabilities.all { it.id.productId == productId }) {
            "다른 상품의 판매 가능 여부가 섞여 있습니다: 상품 ${productId.value}"
        }

        return StoreScopeCleanupTargets(
            displaySettingIds =
                displaySettings
                    .filterNot { newScope.covers(it.storeId) }
                    .map { it.id },
            availabilityIds =
                availabilities
                    .filter { it.source == AvailabilitySource.OWNER }
                    .filterNot { newScope.covers(it.id.storeId) }
                    .map { it.id },
        )
    }
}

data class StoreScopeCleanupTargets(
    val displaySettingIds: List<StoreDisplaySettingId>,
    val availabilityIds: List<StoreProductAvailabilityId>,
)
