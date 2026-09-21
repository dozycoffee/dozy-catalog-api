package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.product.application.port.ValidateStoreExistsPort
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.TargetStoreNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import com.dozycoffee.catalog.product.domain.tag.exception.TagNotFoundException
import org.springframework.stereotype.Component

// 상품이 가리키는 다른 애그리거트(카테고리, 태그, 상품 그룹, 옵션 그룹)와 Store BC의 매장이 존재하는지 확인한다.
// 즉시 변경, 예약 등록, 예약 적용이 모두 이 코드를 써서 같은 값이 경로마다 다르게 판단되지 않게 한다(요구사항 1.4).
// 트랜잭션을 열지 않으므로 부르는 유스케이스의 트랜잭션 안에서 호출한다.
@Component
class ProductReferenceValidator(
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val productGroupRepository: ProductGroupRepository,
    private val optionGroupRepository: OptionGroupRepository,
    private val validateStoreExists: ValidateStoreExistsPort,
) {
    // 상품에는 소분류만 지정할 수 있다(요구사항 1.6). 대분류면 requireChild()가 거부한다.
    suspend fun requireChildCategory(categoryId: CategoryId): ChildCategory =
        (categoryRepository.findById(categoryId) ?: throw CategoryNotFoundException(categoryId)).requireChild()

    suspend fun requireTagsExist(tagIds: Set<TagId>) {
        tagIds.forEach { tagId ->
            tagRepository.findById(tagId) ?: throw TagNotFoundException(tagId)
        }
    }

    suspend fun requireGroupsExist(groupIds: Set<ProductGroupId>) {
        groupIds.forEach { groupId ->
            productGroupRepository.findById(groupId) ?: throw ProductGroupNotFoundException(groupId)
        }
    }

    suspend fun requireOptionGroupsExist(optionGroupIds: Collection<OptionGroupId>) {
        optionGroupIds.forEach { optionGroupId ->
            optionGroupRepository.findById(optionGroupId) ?: throw OptionGroupNotFoundException(optionGroupId)
        }
    }

    // 대상 매장을 비운 Limited도 허용하므로(요구사항 1.5) 확인할 매장이 없으면 그대로 통과한다.
    suspend fun requireTargetStoresExist(scope: StoreScope) {
        val targetStoreIds = (scope as? StoreScope.Limited)?.targetStoreIds.orEmpty()
        if (targetStoreIds.isEmpty()) return
        val missing = validateStoreExists.findMissing(targetStoreIds)
        if (missing.isNotEmpty()) {
            throw TargetStoreNotFoundException(missing)
        }
    }
}
