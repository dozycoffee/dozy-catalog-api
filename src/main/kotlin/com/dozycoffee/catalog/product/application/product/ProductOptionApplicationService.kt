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
import com.dozycoffee.catalog.product.domain.product.OptionOverride
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
    private val referenceValidator: ProductReferenceValidator,
    private val transactionRunner: TransactionRunner,
) {
    // 새 연결은 기존 연결들 뒤에 붙인다. 연결을 해제해 순서에 빈 자리가 생겼을 수 있으므로
    // 연결 수가 아니라 마지막 순서 다음 값을 쓴다.
    suspend fun linkOptionGroup(command: LinkOptionGroupCommand): Product =
        change(command.productId, command.version) { product ->
            referenceValidator.requireOptionGroupsExist(listOf(command.optionGroupId))
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

    // 예약 적용(배치) 경로: 연결할 옵션 그룹 목록 전체를 스냅샷대로 맞추고 목록 순서를 노출 순서로 삼는다.
    // 목록에서 빠진 연결은 그 연결의 예외와 함께 해제된다(요구사항 1.4의 optionGroupLinks 필드).
    suspend fun replaceOptionGroupLinks(
        productId: ProductId,
        optionGroupIds: List<OptionGroupId>,
    ): Product =
        changeLocked(productId) { product ->
            referenceValidator.requireOptionGroupsExist(optionGroupIds)
            val linkedIds = product.optionGroupLinks.map { it.id }
            (linkedIds - optionGroupIds.toSet()).forEach { product.unlinkOptionGroup(it) }
            var nextOrder = (product.optionGroupLinks.maxOfOrNull { it.displayOrder } ?: -1) + 1
            optionGroupIds.filterNot { it in linkedIds }.forEach { product.linkOptionGroup(it, nextOrder++) }
            product.reorderOptionGroups(optionGroupIds)
        }

    // 예약 적용(배치) 경로: 이 옵션 그룹에 대한 이 상품의 예외 전체를 스냅샷으로 교체한다.
    // 기존 예외를 모두 비운 뒤 스냅샷을 하나씩 지정하므로, 옵션 키 존재 여부와 "선택 가능한 옵션 0개" 검증은
    // 스냅샷만을 기준으로 이뤄진다. 적용 시점에 옵션 키가 사라졌으면 Product가 거부하고 예약은 실패로 기록된다.
    suspend fun replaceOptionOverrides(
        productId: ProductId,
        optionGroupId: OptionGroupId,
        overrides: List<OptionOverride>,
    ): Product =
        changeLocked(productId) { product ->
            val groupOptionKeys = optionKeysOf(optionGroupId)
            val currentOverrideKeys =
                product.optionGroupLinks
                    .firstOrNull { it.id == optionGroupId }
                    ?.overrides
                    .orEmpty()
                    .map { it.optionKey }
                    .toSet()
            // 연결되지 않은 옵션 그룹이면 여기서 거부된다.
            product.removeOverrides(optionGroupId, currentOverrideKeys)
            overrides.forEach { override ->
                when (override) {
                    is OptionOverride.Price ->
                        product.overrideOptionPrice(optionGroupId, groupOptionKeys, override.optionKey, override.price)
                    is OptionOverride.Exclude ->
                        product.excludeOption(optionGroupId, groupOptionKeys, override.optionKey)
                }
            }
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

    // 예약 적용 경로는 버전을 요구하지 않는 대신 행을 잠그고 최신 상태에 적용한다
    // (배치에는 사람이 보던 화면이 없다, ADR-0013).
    private suspend fun changeLocked(
        productId: ProductId,
        block: suspend (Product) -> Unit,
    ): Product =
        transactionRunner.inTransaction {
            val product = productRepository.findByIdForUpdate(productId) ?: throw ProductNotFoundException(productId)
            block(product)
            productRepository.save(product)
        }

    private suspend fun optionKeysOf(optionGroupId: OptionGroupId): Set<OptionKey> =
        requireOptionGroup(optionGroupId).options.map { it.optionKey }.toSet()

    private suspend fun requireOptionGroup(optionGroupId: OptionGroupId): OptionGroup =
        optionGroupRepository.findById(optionGroupId) ?: throw OptionGroupNotFoundException(optionGroupId)
}
