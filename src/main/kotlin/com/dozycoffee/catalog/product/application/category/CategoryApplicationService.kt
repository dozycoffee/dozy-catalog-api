package com.dozycoffee.catalog.product.application.category

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.application.category.command.ChangeCategoryParentCommand
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.category.command.RenameCategoryCommand
import com.dozycoffee.catalog.product.domain.category.Category
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.CategoryRepository
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.TopLevelCategory
import com.dozycoffee.catalog.product.domain.category.exception.CategoryHasChildrenException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryStillReferencedException
import com.dozycoffee.catalog.product.domain.category.exception.TopLevelCategoryNotFoundException
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import org.springframework.stereotype.Service

// 카테고리 관리(요구사항 1.6). 2단계 계층은 Category 타입이 지키고, 하위 카테고리·참조 상품 존재 여부처럼
// 조회가 필요한 규칙은 여기서 확인해 도메인 메서드에 넘긴다(ADR-0012).
@Service
class CategoryApplicationService(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun registerTopLevel(name: String): TopLevelCategory =
        transactionRunner.inTransaction {
            categoryRepository.insertTopLevel(name)
        }

    // 부모 후보를 잠근 채로 등록한다. 잠그지 않으면 그 사이 부모가 소분류로 바뀌어 3단계가 될 수 있다.
    suspend fun registerChild(command: RegisterChildCategoryCommand): ChildCategory =
        transactionRunner.inTransaction {
            val parent = requireTopLevel(command.parentId)
            categoryRepository.insertChild(command.name, parent)
        }

    suspend fun rename(command: RenameCategoryCommand): Category =
        transactionRunner.inTransaction {
            val category = requireCategory(command.categoryId)
            category.rename(command.name)
            categoryRepository.save(category)
        }

    // 소분류의 부모 변경과 대분류의 강등을 함께 다룬다. 어느 쪽이든 결과는 그 대분류의 소분류다.
    suspend fun changeParent(command: ChangeCategoryParentCommand): ChildCategory =
        transactionRunner.inTransaction {
            val parent = requireTopLevel(command.parentId)
            val child =
                when (val category = requireCategory(command.categoryId)) {
                    is ChildCategory -> category.changeParent(parent)
                    is TopLevelCategory ->
                        category.becomeChildOf(parent, hasChildren = categoryRepository.hasChildren(category.id))
                }
            categoryRepository.save(child) as ChildCategory
        }

    suspend fun promoteToTopLevel(categoryId: CategoryId): TopLevelCategory =
        transactionRunner.inTransaction {
            val category = requireCategory(categoryId)
            val topLevel =
                when (category) {
                    is TopLevelCategory -> category
                    is ChildCategory ->
                        category.becomeTopLevel(hasProducts = productRepository.existsByCategory(categoryId))
                }
            categoryRepository.save(topLevel) as TopLevelCategory
        }

    // 삭제 제한은 DB FK로도 막히지만, 사용자에게 이유를 알려 주기 위해 먼저 확인한다.
    suspend fun delete(categoryId: CategoryId) {
        transactionRunner.inTransaction {
            requireCategory(categoryId)
            if (categoryRepository.hasChildren(categoryId)) {
                throw CategoryHasChildrenException(categoryId)
            }
            if (productRepository.existsByCategory(categoryId)) {
                throw CategoryStillReferencedException(categoryId)
            }
            categoryRepository.delete(categoryId)
        }
    }

    private suspend fun requireCategory(categoryId: CategoryId): Category =
        categoryRepository.findById(categoryId) ?: throw CategoryNotFoundException(categoryId)

    private suspend fun requireTopLevel(categoryId: CategoryId): TopLevelCategory =
        categoryRepository.findTopLevelByIdForUpdate(categoryId) ?: throw TopLevelCategoryNotFoundException(categoryId)
}
