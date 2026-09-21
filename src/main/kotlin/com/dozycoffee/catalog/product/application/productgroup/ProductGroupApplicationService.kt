package com.dozycoffee.catalog.product.application.productgroup

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.common.paging.requireIdsWithinLimit
import com.dozycoffee.catalog.product.application.productgroup.command.RenameProductGroupCommand
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroup
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupRepository
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import org.springframework.stereotype.Service

// 상품 그룹 관리(요구사항 1.8). 내부 관리용 단일 레벨 분류다.
@Service
class ProductGroupApplicationService(
    private val productGroupRepository: ProductGroupRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 등록 순으로 돌려준다. 조건이 ids 하나뿐이라 조건 객체 없이 받는다.
    suspend fun list(ids: Set<ProductGroupId>? = null): List<ProductGroup> {
        requireIdsWithinLimit(ids)
        return transactionRunner.inTransaction {
            productGroupRepository.findAll(ids)
        }
    }

    suspend fun register(name: String): ProductGroup =
        transactionRunner.inTransaction {
            productGroupRepository.insert(name)
        }

    suspend fun rename(command: RenameProductGroupCommand): ProductGroup =
        transactionRunner.inTransaction {
            val group =
                productGroupRepository.findById(command.productGroupId)
                    ?: throw ProductGroupNotFoundException(command.productGroupId)
            group.rename(command.name)
            productGroupRepository.save(group)
        }

    // 참조 중이어도 삭제할 수 있다. 상품과의 연결은 FK CASCADE로 함께 지워진다(요구사항 1.8).
    suspend fun delete(productGroupId: ProductGroupId) {
        transactionRunner.inTransaction {
            productGroupRepository.findById(productGroupId)
                ?: throw ProductGroupNotFoundException(productGroupId)
            productGroupRepository.delete(productGroupId)
        }
    }
}
