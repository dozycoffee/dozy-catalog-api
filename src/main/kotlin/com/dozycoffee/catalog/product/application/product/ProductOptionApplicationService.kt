package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionConfig
import com.dozycoffee.catalog.product.application.policy.EffectiveOptionResolver
import com.dozycoffee.catalog.product.application.product.command.ExcludeOptionCommand
import com.dozycoffee.catalog.product.application.product.command.LinkOptionGroupCommand
import com.dozycoffee.catalog.product.application.product.command.OverrideOptionPriceCommand
import com.dozycoffee.catalog.product.application.product.command.RemoveOptionOverrideCommand
import com.dozycoffee.catalog.product.application.product.command.ReorderOptionGroupsCommand
import com.dozycoffee.catalog.product.application.product.command.UnlinkOptionGroupCommand
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import org.springframework.stereotype.Service

// 상품의 옵션 그룹 연결과 상품별 옵션 예외(요구사항 1.9 / 시나리오 S5).
// 상품의 나머지 정보는 ProductApplicationService가 다룬다 — 같은 Product 애그리거트지만
// 옵션 쪽은 옵션 그룹을 함께 조회해야 해서 협력 상대와 관심사가 다르다.
// 연결 여부·옵션 키 존재·제외로 0개가 되는지는 Product가 지키고, 여기서는 옵션 그룹을 불러와
// 옵션 키 목록을 값으로 넘긴다(애그리거트끼리는 ID로만 참조, ADR-0012).
@Service
class ProductOptionApplicationService(
    private val productRepository: ProductRepository,
    private val optionGroupRepository: OptionGroupRepository,
    private val transactionRunner: TransactionRunner,
) {
    // 새 연결은 기존 연결들 뒤에 붙인다. 연결을 해제해 순서에 빈 자리가 생겼을 수 있으므로
    // 연결 수가 아니라 마지막 순서 다음 값을 쓴다.
    suspend fun linkOptionGroup(command: LinkOptionGroupCommand): Product =
        change(command.productId, command.version) { product ->
            requireOptionGroup(command.optionGroupId)
            val nextOrder = (product.optionGroupLinks.maxOfOrNull { it.displayOrder } ?: -1) + 1
            product.linkOptionGroup(command.optionGroupId, nextOrder)
        }

    suspend fun unlinkOptionGroup(command: UnlinkOptionGroupCommand): Product =
        change(command.productId, command.version) { product ->
            product.unlinkOptionGroup(command.optionGroupId)
        }

    suspend fun reorderOptionGroups(command: ReorderOptionGroupsCommand): Product =
        change(command.productId, command.version) { product ->
            product.reorderOptionGroups(command.order)
        }

    suspend fun overrideOptionPrice(command: OverrideOptionPriceCommand): Product =
        change(command.productId, command.version) { product ->
            product.overrideOptionPrice(
                optionGroupId = command.optionGroupId,
                groupOptionKeys = optionKeysOf(command.optionGroupId),
                optionKey = command.optionKey,
                price = command.price,
            )
        }

    suspend fun excludeOption(command: ExcludeOptionCommand): Product =
        change(command.productId, command.version) { product ->
            product.excludeOption(
                optionGroupId = command.optionGroupId,
                groupOptionKeys = optionKeysOf(command.optionGroupId),
                optionKey = command.optionKey,
            )
        }

    suspend fun removeOverride(command: RemoveOptionOverrideCommand): Product =
        change(command.productId, command.version) { product ->
            product.removeOverride(command.optionGroupId, command.optionKey)
        }

    // 상품별 예외를 반영한 유효 옵션 구성(요구사항 1.9). 연결된 옵션 그룹 전체를 불러와 정책에 넘긴다.
    suspend fun getEffectiveOptions(productId: ProductId): EffectiveOptionConfig =
        transactionRunner.inTransaction {
            val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
            val optionGroups = product.optionGroupLinks.map { requireOptionGroup(it.id) }
            EffectiveOptionResolver.resolve(product, optionGroups)
        }

    // 옵션 그룹 연결·예외 변경도 전체 교체와 같이 화면이 보던 버전을 확인한다(ADR-0013).
    private suspend fun change(
        productId: ProductId,
        version: Long,
        block: suspend (Product) -> Unit,
    ): Product =
        transactionRunner.inTransaction {
            val product = productRepository.findById(productId) ?: throw ProductNotFoundException(productId)
            product.checkVersion(version)
            block(product)
            productRepository.save(product)
        }

    private suspend fun optionKeysOf(optionGroupId: OptionGroupId): Set<OptionKey> =
        requireOptionGroup(optionGroupId).options.map { it.optionKey }.toSet()

    private suspend fun requireOptionGroup(optionGroupId: OptionGroupId): OptionGroup =
        optionGroupRepository.findById(optionGroupId) ?: throw OptionGroupNotFoundException(optionGroupId)
}
