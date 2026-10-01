package com.dozycoffee.catalog.store.application.storeproduct

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.product.query.SellableProductQueryService
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.application.policy.StoreVisibility
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailability
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import org.springframework.stereotype.Service

// 점주가 보는 매장의 상품(요구사항 2.2, 3장 / 시나리오 S6 1단계).
// 상품·진열 설정·판매 가능 여부는 서로 다른 애그리거트라 각 Repository로 불러와 여기서 합치고,
// 노출 상태 판단은 ProductVisibilityPolicy에 맡긴다(ADR-0012).
// 대상은 Active이고 이 매장이 판매 범위에 든 상품이다. 목록과 단건이 같은 기준을 써야 목록에 있는 상품을 단건으로 볼 수 있다.
@Service
class StoreProductQueryService(
    private val productRepository: ProductRepository,
    private val sellableProductQueryService: SellableProductQueryService,
    private val displaySettingRepository: StoreDisplaySettingRepository,
    private val availabilityRepository: StoreProductAvailabilityRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 점주가 숨긴 상품도 다시 노출로 되돌릴 수 있어야 하므로 목록에서 빼지 않고 노출 상태(NotVisible)로 표시한다.
    // 진열 설정이나 판매 가능 여부가 없는 상품은 기본값으로 본다 — 각 Map에서 찾지 못한 null을 그대로 정책에 넘긴다.
    // ids를 주면 그 상품만 남긴다(docs/api/README.md 목록 조회). 대상이 아니거나 없는 ID는 오류 없이 빠진다.
    suspend fun listProducts(
        storeId: StoreId,
        ids: Set<ProductId>? = null,
    ): List<StoreProductView> =
        transactionRunner.inTransaction {
            // 매장 상품 목록은 페이징하지 않아 판매 가능한 상품 전체를 불러오므로, ids도 불러온 뒤 거른다.
            val products =
                productRepository
                    .findAllSellableAt(storeId)
                    .filter { ids == null || it.id in ids }
            val displaySettings = displaySettingRepository.findAllByStore(storeId).associateBy { it.productId }
            val availabilities = availabilityRepository.findAllByStore(storeId).associateBy { it.id.productId }

            products
                .map { product -> viewOf(product, storeId, displaySettings[product.id], availabilities[product.id]) }
                // 점주가 순서를 정한 상품을 앞에 오름차순으로 두고, 정하지 않은 상품은 뒤에 상품 등록 순으로 둔다.
                // 순서를 정하지 않은 상품을 0번으로 보면 새 상품이 점주가 배치한 상품들을 밀어내기 때문이다.
                // 상품 목록이 id 순이고 정렬이 안정적이라 같은 순서끼리는 등록 순이 유지된다.
                .sortedWith(compareBy(nullsLast()) { it.displayOrder })
        }

    // 상품 하나의 매장 상태. 숨김 전환·수동 품절의 결과를 노출 판단까지 반영해 돌려줄 때 쓴다.
    // 이 매장에서 판매할 수 없는 상품(없음, Active 아님, 판매 범위 밖)은 존재를 드러내지 않도록 모두 ProductNotFoundException이다.
    // 판매 가능 기준은 점주의 상품 조회(SellableProductQueryService)와 같은 것을 쓴다. 이어 쓰는 트랜잭션 안에서 부른다.
    suspend fun getProduct(
        storeId: StoreId,
        productId: ProductId,
    ): StoreProductView =
        transactionRunner.inTransaction {
            val product = sellableProductQueryService.get(productId, setOf(storeId))
            viewOf(
                product = product,
                storeId = storeId,
                displaySetting = displaySettingRepository.findByStoreAndProduct(storeId, productId),
                availability = availabilityRepository.findById(StoreProductAvailabilityId(storeId, productId)),
            )
        }

    // 이 매장에서 본 상품의 유효 옵션 구성(요구사항 1.9). 계산과 판매 가능 확인은 product 모듈의 유스케이스가 한다.
    suspend fun getEffectiveOptions(
        storeId: StoreId,
        productId: ProductId,
    ): EffectiveOptionConfig = sellableProductQueryService.getEffectiveOptions(productId, setOf(storeId))

    private fun viewOf(
        product: Product,
        storeId: StoreId,
        displaySetting: StoreDisplaySetting?,
        availability: StoreProductAvailability?,
    ): StoreProductView =
        StoreProductView(
            product = product,
            displayOrder = displaySetting?.displayOrder,
            visibility =
                ProductVisibilityPolicy.resolve(
                    product = product,
                    storeId = storeId,
                    displaySetting = displaySetting,
                    availability = availability,
                ),
        )
}

// 매장 상품 한 줄. 상품 정보와 이 매장에서의 노출 상태를 함께 담는다.
// 대상이 Active이고 판매 범위에 든 상품뿐이라, 노출 판단이 비노출이면 곧 점주가 숨긴 것이다.
data class StoreProductView(
    val product: Product,
    val displayOrder: Int?,
    val visibility: StoreVisibility,
)
