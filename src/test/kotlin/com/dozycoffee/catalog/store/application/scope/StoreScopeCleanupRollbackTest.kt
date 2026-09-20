package com.dozycoffee.catalog.store.application.scope

import com.dozycoffee.catalog.common.event.DomainEventHandler
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.command.ChangeStoreScopeCommand
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.event.ProductStoreScopeChanged
import com.dozycoffee.catalog.store.domain.display.StoreDisplaySettingRepository
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 도메인 이벤트 핸들러는 발행 측의 트랜잭션 안에서 동기로 실행되므로, 핸들러가 실패하면 원래 변경도
// 함께 롤백된다(#65). 정리되지 않은 매장 설정이 남는 것보다 요청을 거부하는 쪽이 낫기 때문이다.
// 실패를 만들려고 항상 예외를 던지는 핸들러를 하나 더 등록한다.
@DisplayName("S4 매장 설정 정리 실패 (요구사항 1.5)")
@Import(StoreScopeCleanupRollbackTest.FailingHandlerConfiguration::class)
class StoreScopeCleanupRollbackTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var displaySettingRepository: StoreDisplaySettingRepository

    @Test
    fun `핸들러가 실패하면 판매 범위 변경과 매장 설정 정리가 함께 롤백된다`() =
        runTest {
            val beverage = categoryService.registerTopLevel("음료")
            val coffee = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
            val product =
                service.register(
                    RegisterProductCommand(
                        name = "아메리카노",
                        categoryId = coffee.id,
                        basePrice = Money(4500),
                        tracksInventory = false,
                    ),
                )
            execute(
                "INSERT INTO store_display_settings (store_id, product_id, visibility) " +
                    "VALUES (2, ${product.id.value}, 'HIDDEN')",
            )

            assertFailsWith<IllegalStateException> {
                service.changeStoreScope(
                    ChangeStoreScopeCommand(
                        productId = product.id,
                        version = product.version,
                        scope = StoreScope.Limited(setOf(StoreId(1))),
                    ),
                )
            }

            assertEquals(StoreScope.All, tx { productRepository.findById(product.id) }?.storeScope)
            assertEquals(
                listOf(StoreId(2)),
                tx { displaySettingRepository.findAllByProduct(product.id) }.map { it.storeId },
            )
        }

    @TestConfiguration
    class FailingHandlerConfiguration {
        @Bean
        fun failingStoreScopeChangedHandler() =
            object : DomainEventHandler<ProductStoreScopeChanged> {
                override val eventType: Class<ProductStoreScopeChanged> = ProductStoreScopeChanged::class.java

                override suspend fun handle(event: ProductStoreScopeChanged): Unit = error("정리 실패")
            }
    }
}
