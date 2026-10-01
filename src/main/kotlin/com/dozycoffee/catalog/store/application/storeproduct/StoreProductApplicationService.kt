package com.dozycoffee.catalog.store.application.storeproduct

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.store.application.availability.StoreProductAvailabilityApplicationService
import com.dozycoffee.catalog.store.application.availability.command.ChangeStockStatusByOwnerCommand
import com.dozycoffee.catalog.store.application.display.StoreDisplaySettingApplicationService
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.policy.ProductVisibilityPolicy
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityId
import com.dozycoffee.catalog.store.domain.availability.StoreProductAvailabilityRepository
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import org.springframework.stereotype.Service

// 점주가 상품 하나의 설정(숨김, 수동 품절)을 바꾸고 바뀐 결과를 매장 상품으로 돌려받는 유스케이스(요구사항 2.2, 2.5 / 시나리오 S6).
// 변경은 진열 설정·판매 가능 여부의 유스케이스에 맡기고, 결과 조회까지 한 트랜잭션에서 해 어느 쪽이 실패해도 변경이 남지 않게 한다.
// 점주는 판매 범위에 든 상품이면 상태와 무관하게 설정을 바꿀 수 있으므로(Draft·단종 상품도 미리 바꿔 두면 활성화 후 그대로 쓰임),
// 결과도 상품 상태를 보지 않고 점주의 설정만으로 정한다. 판매 범위 밖이거나 없는 상품은 변경 유스케이스가 404로 거부한다.
@Service
class StoreProductApplicationService(
    private val displaySettingService: StoreDisplaySettingApplicationService,
    private val availabilityService: StoreProductAvailabilityApplicationService,
    private val productRepository: ProductRepository,
    private val displaySettingRepository: StoreDisplaySettingRepository,
    private val availabilityRepository: StoreProductAvailabilityRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun changeVisibility(command: ChangeVisibilityCommand): StoreProductView =
        transactionRunner.inTransaction {
            displaySettingService.changeVisibility(command)
            settingViewOf(command.storeId, command.productId)
        }

    suspend fun changeStockStatus(command: ChangeStockStatusByOwnerCommand): StoreProductView =
        transactionRunner.inTransaction {
            availabilityService.changeStockStatusByOwner(command)
            settingViewOf(command.storeId, command.productId)
        }

    // 노출 판단 3·4단계만 적용한다(ProductVisibilityPolicy.resolveByOwnerSetting). 설정이 없으면 기본값(노출, 출처별 판매중·품절)이다.
    // 변경 유스케이스가 방금 상품을 확인했으므로 상품이 없으면 호출 순서가 잘못된 것이다.
    private suspend fun settingViewOf(
        storeId: StoreId,
        productId: ProductId,
    ): StoreProductView {
        val product = checkNotNull(productRepository.findById(productId)) { "변경 직후 상품을 찾을 수 없습니다: ${productId.value}" }
        val displaySetting = displaySettingRepository.findByStoreAndProduct(storeId, productId)
        val availability = availabilityRepository.findById(StoreProductAvailabilityId(storeId, productId))
        return StoreProductView(
            product = product,
            displayOrder = displaySetting?.displayOrder,
            visibility =
                ProductVisibilityPolicy.resolveByOwnerSetting(
                    tracksInventory = product.tracksInventory,
                    visibility = displaySetting?.visibility,
                    stockStatus = availability?.stockStatus,
                ),
        )
    }
}
