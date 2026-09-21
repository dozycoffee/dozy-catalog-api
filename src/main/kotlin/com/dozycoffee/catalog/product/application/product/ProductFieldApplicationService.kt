package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.event.DomainEventDispatcher
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import org.springframework.stereotype.Service

// 상품의 필드 하나만 바꾸는 유스케이스(요구사항 1.4의 예약 적용 경로).
// 즉시 반영(PUT)은 입력한 값 전체로 교체하므로 이 경로가 없었고, 예약은 필드 단위라 여기서 붙인다.
//
// 즉시 반영은 화면이 보던 버전을 함께 받아 그 사이의 다른 변경을 거부하지만(ADR-0013),
// 이 경로를 부르는 예약 배치에는 사람이 보던 화면이 없다. 버전을 요구하는 대신 행을 잠그고
// 최신 상태에 적용한다 — 예약은 "그 날 00시의 현재 값에 이 필드를 반영한다"는 뜻이기 때문이다.
//
// 다른 애그리거트를 확인해야 하는 규칙(소분류인지, 태그·그룹이 있는지, 대상 매장이 존재하는지)은
// 예약 등록 때 확인했더라도 적용 시점에 같은 ProductReferenceValidator로 다시 확인한다.
// 그 사이 대상이 삭제되거나 바뀌었으면 예약은 실패로 기록된다(요구사항 1.4).
@Service
class ProductFieldApplicationService(
    private val productRepository: ProductRepository,
    private val referenceValidator: ProductReferenceValidator,
    private val eventDispatcher: DomainEventDispatcher,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun changeName(
        productId: ProductId,
        name: String,
    ): Product = change(productId) { it.rename(name) }

    // 상품에는 소분류만 지정할 수 있다(요구사항 1.6). 적용 시점에 대분류가 되었으면 requireChild()가 거부한다.
    suspend fun changeCategory(
        productId: ProductId,
        categoryId: CategoryId,
    ): Product =
        change(productId) { product ->
            val category = referenceValidator.requireChildCategory(categoryId)
            product.changeCategory(category.id)
        }

    suspend fun changeDescription(
        productId: ProductId,
        description: String?,
    ): Product = change(productId) { it.changeDescription(description) }

    suspend fun changeImage(
        productId: ProductId,
        imageUrl: String?,
    ): Product = change(productId) { it.changeImage(imageUrl) }

    suspend fun changeBasePrice(
        productId: ProductId,
        basePrice: Money,
    ): Product = change(productId) { it.changeBasePrice(basePrice) }

    // 예약 값은 태그 이름이 아니라 태그 ID다(등록 시점에 이미 만들어진 태그를 가리킨다).
    // 그 사이 태그가 삭제됐으면 거부한다 — 없는 태그를 연결하면 FK에 걸린다.
    suspend fun changeTags(
        productId: ProductId,
        tagIds: Set<TagId>,
    ): Product =
        change(productId) { product ->
            referenceValidator.requireTagsExist(tagIds)
            product.changeTags(tagIds)
        }

    suspend fun changeGroups(
        productId: ProductId,
        groupIds: Set<ProductGroupId>,
    ): Product =
        change(productId) { product ->
            referenceValidator.requireGroupsExist(groupIds)
            product.changeGroups(groupIds)
        }

    // 판매 범위 변경(요구사항 1.5). 즉시 반영 경로와 같이 대상 매장의 존재를 Store BC에 확인하고,
    // 대상에서 빠진 매장의 설정 정리는 store 모듈이 ProductStoreScopeChanged를 구독해 같은 트랜잭션에서 처리한다.
    suspend fun changeStoreScope(
        productId: ProductId,
        scope: StoreScope,
    ): Product =
        change(productId) { product ->
            referenceValidator.requireTargetStoresExist(scope)
            product.changeStoreScope(scope)
        }

    private suspend fun change(
        productId: ProductId,
        block: suspend (Product) -> Unit,
    ): Product =
        transactionRunner.inTransaction {
            val product = productRepository.findByIdForUpdate(productId) ?: throw ProductNotFoundException(productId)
            block(product)
            val saved = productRepository.save(product)
            eventDispatcher.dispatch(saved.pullDomainEvents())
            saved
        }
}
