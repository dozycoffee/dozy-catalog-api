package com.dozycoffee.catalog.store.application.storeproduct

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import org.springframework.stereotype.Service

// 점주가 보는 매장의 상품 목록(요구사항 2.2, 3장 / 시나리오 S6 1단계).
// 상품·진열 설정·판매 가능 여부는 서로 다른 애그리거트라 각 Repository로 불러와 여기서 합치고,
// 노출 상태 판단은 ProductVisibilityPolicy에 맡긴다(ADR-0012).
@Service
class StoreProductQueryService(
    private val productRepository: ProductRepository,
    private val displaySettingRepository: StoreDisplaySettingRepository,
    private val availabilityRepository: StoreProductAvailabilityRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 대상은 Active이고 이 매장이 판매 범위에 든 상품이다. 점주가 숨긴 상품도 다시 노출로 되돌릴 수 있어야 하므로
    // 목록에서 빼지 않고 노출 상태(NotVisible)로 표시한다.
    // 진열 설정이나 판매 가능 여부가 없는 상품은 기본값으로 본다 — 각 Map에서 찾지 못한 null을 그대로 정책에 넘긴다.
    suspend fun listProducts(storeId: StoreId): List<StoreProductView> =
        transactionRunner.inTransaction {
            val products = productRepository.findAllSellableAt(storeId)
            val displaySettings = displaySettingRepository.findAllByStore(storeId).associateBy { it.productId }
            val availabilities = availabilityRepository.findAllByStore(storeId).associateBy { it.id.productId }

            products
                .map { product ->
                    val displaySetting = displaySettings[product.id]
                    StoreProductView(
                        product = product,
                        displayOrder = displaySetting?.displayOrder,
                        visibility =
                            ProductVisibilityPolicy.resolve(
                                product = product,
                                storeId = storeId,
                                displaySetting = displaySetting,
                                availability = availabilities[product.id],
                            ),
                    )
                }
                // 점주가 순서를 정한 상품을 앞에 오름차순으로 두고, 정하지 않은 상품은 뒤에 상품 등록 순으로 둔다.
                // 순서를 정하지 않은 상품을 0번으로 보면 새 상품이 점주가 배치한 상품들을 밀어내기 때문이다.
                // 상품 목록이 id 순이고 정렬이 안정적이라 같은 순서끼리는 등록 순이 유지된다.
                .sortedWith(compareBy(nullsLast()) { it.displayOrder })
        }
}

// 목록 한 줄. 상품 정보와 이 매장에서의 노출 상태를 함께 담는다.
data class StoreProductView(
    val product: Product,
    val displayOrder: Int?,
    val visibility: StoreVisibility,
)
