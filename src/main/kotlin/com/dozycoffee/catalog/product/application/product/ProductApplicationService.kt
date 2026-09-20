package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.application.product.command.ReplaceProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import org.springframework.stereotype.Service

// 상품 등록·수정·상태 전환·삭제(요구사항 1.2, 1.3, 1.4, 1.11 / 시나리오 S1, S3).
// 상태 전이는 Product가 지키고, 카테고리·태그·그룹·옵션 그룹처럼 다른 애그리거트를 확인해야 하는 규칙은
// 여기서 조회해 넘긴다(ADR-0012).
@Service
class ProductApplicationService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val productGroupRepository: ProductGroupRepository,
    private val optionGroupRepository: OptionGroupRepository,
    private val skuGenerator: SkuGenerator,
    private val eventPublisher: ProductEventPublisherPort,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun register(command: RegisterProductCommand): Product =
        transactionRunner.inTransaction {
            val category = requireChildCategory(command.categoryId)
            requireGroupsExist(command.groupIds)
            requireOptionGroupsExist(command.optionGroupIds)
            val newProduct =
                Product.NewProduct.of(
                    sku = skuGenerator.next(),
                    name = command.name,
                    categoryId = category.id,
                    description = command.description,
                    imageUrl = command.imageUrl,
                    basePrice = command.basePrice,
                    tracksInventory = command.tracksInventory,
                    tagIds = resolveTags(command.tagNames),
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
            val category = requireChildCategory(command.categoryId)
            requireGroupsExist(command.groupIds)
            val tagIds = resolveTags(command.tagNames)

            product.rename(command.name)
            product.changeCategory(category.id)
            product.changeDescription(command.description)
            product.changeImage(command.imageUrl)
            product.changeBasePrice(command.basePrice)
            product.changeTags(tagIds)
            product.changeGroups(command.groupIds)
            productRepository.save(product)
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

    // 상품에는 소분류만 지정할 수 있다(요구사항 1.6). 대분류면 requireChild()가 거부한다.
    private suspend fun requireChildCategory(categoryId: CategoryId): ChildCategory =
        (categoryRepository.findById(categoryId) ?: throw CategoryNotFoundException(categoryId)).requireChild()

    // 같은 이름의 태그가 있으면 재사용하고 없으면 만든다(요구사항 1.7).
    private suspend fun resolveTags(tagNames: List<String>): Set<TagId> = tagNames.map { tagRepository.findOrCreateByName(it).id }.toSet()

    private suspend fun requireGroupsExist(groupIds: Set<ProductGroupId>) {
        groupIds.forEach { groupId ->
            productGroupRepository.findById(groupId) ?: throw ProductGroupNotFoundException(groupId)
        }
    }

    private suspend fun requireOptionGroupsExist(optionGroupIds: List<OptionGroupId>) {
        optionGroupIds.forEach { optionGroupId ->
            optionGroupRepository.findById(optionGroupId) ?: throw OptionGroupNotFoundException(optionGroupId)
        }
    }
}
