package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.event.DomainEventDispatcher
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import com.dozycoffee.catalog.product.application.product.command.ChangeStoreScopeCommand
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.application.product.command.ReplaceProductCommand
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import org.springframework.stereotype.Service

// 상품 등록·수정·상태 전환·삭제(요구사항 1.2, 1.3, 1.4, 1.11 / 시나리오 S1, S3).
// 상태 전이는 Product가 지키고, 카테고리·태그·그룹·옵션 그룹처럼 다른 애그리거트를 확인해야 하는 규칙은
// 여기서 조회해 넘긴다(ADR-0012). 참조 대상의 존재 확인은 예약 등록·적용과 같은 ProductReferenceValidator를 쓴다.
@Service
class ProductApplicationService(
    private val productRepository: ProductRepository,
    private val referenceValidator: ProductReferenceValidator,
    private val tagResolver: ProductTagResolver,
    private val skuGenerator: SkuGenerator,
    private val eventPublisher: ProductEventPublisherPort,
    private val eventDispatcher: DomainEventDispatcher,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun register(command: RegisterProductCommand): Product =
        transactionRunner.inTransaction {
            val category = referenceValidator.requireChildCategory(command.categoryId)
            referenceValidator.requireGroupsExist(command.groupIds)
            referenceValidator.requireOptionGroupsExist(command.optionGroupIds)
            val newProduct =
                Product.NewProduct.of(
                    sku = skuGenerator.next(),
                    name = command.name,
                    categoryId = category.id,
                    description = command.description,
                    imageUrl = command.imageUrl,
                    basePrice = command.basePrice,
                    tracksInventory = command.tracksInventory,
                    tagIds = tagResolver.resolve(command.tagNames),
                    groupIds = command.groupIds,
                    optionGroupIds = command.optionGroupIds,
                )
            productRepository.insert(newProduct)
        }

    // 입력한 값 전체로 교체한다. 단종 상태에서도 상태 외 정보는 모두 바꿀 수 있다(요구사항 1.3).
    suspend fun replace(command: ReplaceProductCommand): Product =
        transactionRunner.inTransaction {
            val product = requireProduct(command.productId)
            product.checkVersion(command.version)
            val category = referenceValidator.requireChildCategory(command.categoryId)
            referenceValidator.requireGroupsExist(command.groupIds)
            val tagIds = tagResolver.resolve(command.tagNames)

            product.rename(command.name)
            product.changeCategory(category.id)
            product.changeDescription(command.description)
            product.changeImage(command.imageUrl)
            product.changeBasePrice(command.basePrice)
            product.changeTags(tagIds)
            product.changeGroups(command.groupIds)
            productRepository.save(product)
        }

    // 판매 범위 변경(요구사항 1.5, 시나리오 S4). Limited면 대상 매장이 모두 존재하는지 Store BC에 확인하고,
    // 하나라도 없으면 아무것도 바꾸지 않고 거부한다. 대상에서 빠진 매장의 설정 정리는 store 모듈이
    // ProductStoreScopeChanged를 구독해 같은 트랜잭션에서 처리한다 — product은 store를 부르지 않는다(ADR-0015).
    suspend fun changeStoreScope(command: ChangeStoreScopeCommand): Product =
        transactionRunner.inTransaction {
            val product =
                productRepository.findByIdForUpdate(command.productId)
                    ?: throw ProductNotFoundException(command.productId)
            product.checkVersion(command.version)
            referenceValidator.requireTargetStoresExist(command.scope)

            product.changeStoreScope(command.scope)
            val saved = productRepository.save(product)
            eventDispatcher.dispatch(saved.pullDomainEvents())
            saved
        }

    // 상태 전이는 행을 잠근 채 확인하고 바꾼다. 동시에 두 요청이 들어와도 한 번만 전이된다.
    suspend fun activate(productId: ProductId): Product = changeStatus(productId) { it.activate() }

    suspend fun discontinue(productId: ProductId): Product = changeStatus(productId) { it.discontinue() }

    // Draft 상품만 삭제할 수 있다(요구사항 1.11). 하위 데이터와 매장 설정은 FK CASCADE로 함께 사라진다.
    suspend fun delete(productId: ProductId) {
        transactionRunner.inTransaction {
            val product = productRepository.findByIdForUpdate(productId) ?: throw ProductNotFoundException(productId)
            product.delete()
            productRepository.delete(productId)
        }
    }

    suspend fun get(productId: ProductId): Product = transactionRunner.inTransaction { requireProduct(productId) }

    private suspend fun changeStatus(
        productId: ProductId,
        transition: (Product) -> Unit,
    ): Product =
        transactionRunner.inTransaction {
            val product = productRepository.findByIdForUpdate(productId) ?: throw ProductNotFoundException(productId)
            transition(product)
            val saved = productRepository.save(product)
            eventPublisher.publish(saved.pullDomainEvents())
            saved
        }

    private suspend fun requireProduct(productId: ProductId): Product =
        productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
}
