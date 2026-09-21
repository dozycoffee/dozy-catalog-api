package com.dozycoffee.catalog.store.application.display

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.store.application.display.command.ChangeVisibilityCommand
import com.dozycoffee.catalog.store.application.display.command.ReplaceDisplayOrderCommand
import com.dozycoffee.catalog.store.domain.display.StoreDisplayOrder
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySetting
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.store.domain.display.Visibility
import org.springframework.stereotype.Service

// 점주의 진열 설정 변경(요구사항 2.2, 2.3 / 시나리오 S6).
// 진열 설정은 점주가 처음 바꾸는 시점에 생기고(Lazy), 노출 여부와 진열 순서는 서로 덮어쓰지 않도록
// 바꾼 필드만 저장한다(ERD 동시성 처리).
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

    // 점주가 완성한 최종 순서 전체로 이 매장의 진열 순서를 한 번에 바꾼다(요구사항 2.3). 목록의 상품은 1부터 번호를 받고
    // 설정이 없으면 만들며, 목록에 없는 상품은 순서를 비운다. 노출 여부는 건드리지 않는다.
    // 목록에 없는 상품이나 판매 범위 밖 상품이 하나라도 있으면 아무것도 바꾸지 않고 전체를 거부한다.
    // 같은 매장의 일괄 변경이 동시에 들어오면 두 순서가 섞일 수 있으므로(한쪽이 비운 뒤 다른 쪽이 번호를 매김),
    // 매장 단위 잠금을 먼저 잡아 줄을 세운다. 나중 요청은 앞 요청이 커밋한 뒤 전체를 다시 쓰므로 결과는 둘 중 하나다.
    suspend fun replaceDisplayOrder(command: ReplaceDisplayOrderCommand) {
        val order = StoreDisplayOrder(command.storeId, command.productIds)
        transactionRunner.inTransaction {
            displaySettingRepository.lockStoreDisplayOrder(order.storeId)
            val products = productRepository.findAllByIds(order.productIds).associateBy { it.id }
            order.productIds.forEach { productId ->
                val product = products[productId] ?: throw ProductNotFoundException(productId)
                if (!product.storeScope.covers(order.storeId)) throw ProductNotFoundException(productId)
            }
            displaySettingRepository.replaceDisplayOrder(order)
        }
    }

    // 진열 설정 테이블은 상품을 FK로 참조한다. 없는 상품이면 DB 오류 대신 이유를 알 수 있는 404로 거부한다.
    // 이 매장이 판매 범위에 들지 않은 상품도 점주에게는 없는 상품이므로 같은 404로 거부한다(요구사항 2.2).
    // 판매 범위에서 빠지면 설정이 지워지므로(요구사항 1.5) 범위 밖에서 설정을 만들면 되살아나는 셈이 된다.
    // 상품 상태는 보지 않는다 — DRAFT·DISCONTINUED 상품의 설정도 미리 바꿀 수 있다.
    private suspend fun findOrCreate(
        storeId: StoreId,
        productId: ProductId,
    ): StoreDisplaySetting {
        val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
        if (!product.storeScope.covers(storeId)) throw ProductNotFoundException(productId)
        return displaySettingRepository.findOrCreate(storeId, productId)
    }
}
