package com.dozycoffee.catalog.store.application.display

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.display.command.ChangeDisplayOrderCommand
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.store.domain.display.Visibility
import org.springframework.stereotype.Service

// 점주의 진열 설정 변경(요구사항 2.2, 2.3 / 시나리오 S6).
// 진열 설정은 점주가 처음 바꾸는 시점에 생기므로(Lazy) 두 유스케이스 모두 findOrCreate로 시작하고,
// 노출 여부와 진열 순서는 서로 덮어쓰지 않도록 바꾼 필드만 저장한다(ERD 동시성 처리).
@Service
class StoreDisplaySettingApplicationService(
    private val displaySettingRepository: StoreDisplaySettingRepository,
    private val productRepository: ProductRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 숨김 처리와 다시 노출로 되돌리기. 이 매장에서 이 상품이 실제로 보이는지는 조회 시점에
    // ProductVisibilityPolicy가 판단하므로, 여기서는 점주의 의도만 저장한다.
    suspend fun changeVisibility(command: ChangeVisibilityCommand): StoreDisplaySetting =
        transactionRunner.inTransaction {
            val setting = findOrCreate(command.storeId, command.productId)
            when (command.visibility) {
                Visibility.VISIBLE -> setting.show()
                Visibility.HIDDEN -> setting.hide()
            }
            displaySettingRepository.saveVisibility(setting)
            setting
        }

    suspend fun changeDisplayOrder(command: ChangeDisplayOrderCommand): StoreDisplaySetting =
        transactionRunner.inTransaction {
            val setting = findOrCreate(command.storeId, command.productId)
            setting.changeDisplayOrder(command.displayOrder)
            displaySettingRepository.saveDisplayOrder(setting)
            setting
        }

    // 진열 설정 테이블은 상품을 FK로 참조한다. 없는 상품이면 DB 오류 대신 이유를 알 수 있는 404로 거부한다.
    private suspend fun findOrCreate(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting {
        productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        return displaySettingRepository.findOrCreate(storeId, productId)
    }
}
