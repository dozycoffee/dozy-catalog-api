package com.dozycoffee.catalog.product.application.optiongroup

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.application.optiongroup.command.ChangeOptionGroupDefinitionCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.RegisterOptionGroupCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.ReplaceOptionsCommand
import com.dozycoffee.catalog.product.application.policy.OptionReplacementPolicy
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupStillReferencedException
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import org.springframework.stereotype.Service

// 옵션 그룹 관리(요구사항 1.9 / 시나리오 S5). 옵션 목록 자체의 규칙은 OptionGroup이 지키고,
// 연결 상품 전체를 함께 봐야 하는 판단은 OptionReplacementPolicy에 맡긴다(ADR-0012).
// 여기서는 조회·잠금·저장 순서만 정한다.
@Service
class OptionGroupApplicationService(
    private val optionGroupRepository: OptionGroupRepository,
    private val productRepository: ProductRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun register(command: RegisterOptionGroupCommand): OptionGroup =
        transactionRunner.inTransaction {
            optionGroupRepository.insert(
                OptionGroup.NewOptionGroup.of(
                    name = command.name,
                    selectionType = command.selectionType,
                    required = command.required,
                    options = command.options,
                ),
            )
        }

    // 이름·선택 방식·필수 여부는 즉시 반영만 한다(요구사항 1.9).
    suspend fun changeDefinition(command: ChangeOptionGroupDefinitionCommand): OptionGroup =
        transactionRunner.inTransaction {
            val optionGroup = requireOptionGroup(command.optionGroupId)
            optionGroup.checkVersion(command.version)
            optionGroup.rename(command.name)
            optionGroup.changeSelectionType(command.selectionType)
            optionGroup.changeRequired(command.required)
            optionGroupRepository.save(optionGroup)
        }

    // 옵션 목록을 입력한 목록 전체로 교체한다(요구사항 1.9 / S5 기본 흐름).
    // 검증과 교체 사이에 다른 변경이 끼어들지 않도록 옵션 그룹을 먼저 잠그고, 연결 상품을 상태와
    // 무관하게 모두(id 순서로) 잠근 뒤 한 트랜잭션에서 옵션 그룹과 상품을 함께 저장한다(ADR-0012).
    suspend fun replaceOptions(command: ReplaceOptionsCommand): OptionGroup =
        replaceOptionsLocked(command.optionGroupId, command.options) { it.checkVersion(command.version) }

    // 예약 적용(배치) 경로: 등록 시점의 옵션 목록 스냅샷으로 교체한다(요구사항 1.9).
    // 검증(연결 상품의 선택 가능 옵션이 1개 이상 남는지)은 적용 시점에 다시 이뤄지고,
    // 실패하면 예약만 실패로 기록되고 옵션 그룹은 그대로다.
    // 배치에는 사람이 보던 화면이 없으므로 버전을 확인하지 않는다(ADR-0013).
    suspend fun replaceOptions(
        optionGroupId: OptionGroupId,
        options: List<Option>,
    ): OptionGroup = replaceOptionsLocked(optionGroupId, options) { }

    private suspend fun replaceOptionsLocked(
        optionGroupId: OptionGroupId,
        options: List<Option>,
        checkVersion: (OptionGroup) -> Unit,
    ): OptionGroup =
        transactionRunner.inTransaction {
            val optionGroup =
                optionGroupRepository.findByIdForUpdate(optionGroupId)
                    ?: throw OptionGroupNotFoundException(optionGroupId)
            checkVersion(optionGroup)
            val linkedProducts = productRepository.findAllLinkedToForUpdate(optionGroup.id)

            val plan = OptionReplacementPolicy.check(optionGroup, options, linkedProducts)
            optionGroup.replaceOptions(options)
            val saved = optionGroupRepository.save(optionGroup)

            // 사라진 옵션 키의 예외를 실제로 갖고 있던 상품만 저장한다. 나머지 상품까지 저장하면
            // 바뀐 것 없이 version만 올라가 관리자의 다음 수정이 충돌로 거부된다(ADR-0013).
            linkedProducts
                .filter { it.hasOverrideOf(optionGroup.id, plan.removedOptionKeys) }
                .forEach { product ->
                    product.removeOverrides(optionGroup.id, plan.removedOptionKeys)
                    productRepository.save(product)
                }
            saved
        }

    // 삭제 제한은 DB FK로도 막히지만, 사용자에게 이유를 알려 주기 위해 먼저 확인한다. 옵션은 함께 삭제된다.
    suspend fun delete(optionGroupId: OptionGroupId) {
        transactionRunner.inTransaction {
            requireOptionGroup(optionGroupId)
            if (productRepository.existsLinkedTo(optionGroupId)) {
                throw OptionGroupStillReferencedException(optionGroupId)
            }
            optionGroupRepository.delete(optionGroupId)
        }
    }

    suspend fun get(optionGroupId: OptionGroupId): OptionGroup = transactionRunner.inTransaction { requireOptionGroup(optionGroupId) }

    private suspend fun requireOptionGroup(optionGroupId: OptionGroupId): OptionGroup =
        optionGroupRepository.findById(optionGroupId) ?: throw OptionGroupNotFoundException(optionGroupId)

    private fun Product.hasOverrideOf(
        optionGroupId: OptionGroupId,
        optionKeys: Set<OptionKey>,
    ): Boolean =
        optionGroupLinks
            .firstOrNull { it.id == optionGroupId }
            ?.overrides
            .orEmpty()
            .any { it.optionKey in optionKeys }
}
