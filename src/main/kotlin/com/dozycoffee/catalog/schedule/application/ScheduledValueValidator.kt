package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.product.application.optiongroup.OptionGroupApplicationService
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.ProductReferenceValidator
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroup
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import org.springframework.stereotype.Component

// 예약을 등록할 때 대상의 존재, 값 자체, 값이 가리키는 대상의 존재를 확인한다(요구사항 1.4, docs/api/schedule.md 검증 시점).
// 언제 적용하든 틀린 값은 00시에 실패로 기록되기 전에 바로 거부한다. 참조 대상의 존재는 즉시 변경·예약 적용과 같은
// product 모듈의 ProductReferenceValidator로, 값 자체의 규칙은 그 규칙을 가진 애그리거트의 검증으로 확인해
// 경로마다 규칙이 갈라지지 않게 한다.
//
// 대상의 상태에 달린 규칙(상태 전이, 옵션 그룹 연결 여부, 옵션 키 존재, 선택 가능한 옵션 수)은 여기서 보지 않는다.
// 적용 전에 다른 예약이나 즉시 변경으로 바뀔 수 있어 적용 시점에만 판단한다(예: 활성화와 단종을 함께 예약하면
// 단종 예약을 등록하는 시점에 상품은 아직 DRAFT다).
//
// ScheduledChangeApplier와 같이 값 타입을 when으로 빠짐없이 분기하므로, 예약 가능한 필드를 추가하고
// 등록 검증을 빠뜨리면 컴파일 에러가 된다(ADR-0014). 트랜잭션은 부르는 등록 유스케이스가 연다.
@Component
class ScheduledValueValidator(
    private val productService: ProductApplicationService,
    private val optionGroupService: OptionGroupApplicationService,
    private val productReferences: ProductReferenceValidator,
) {
    suspend fun validate(
        targetId: Long,
        newValue: ScheduledFieldValue,
    ) {
        when (newValue) {
            is ProductFieldValue -> validateForProduct(ProductId(targetId), newValue)
            is OptionGroupFieldValue -> validateForOptionGroup(OptionGroupId(targetId), newValue)
        }
    }

    private suspend fun validateForProduct(
        productId: ProductId,
        newValue: ProductFieldValue,
    ) {
        productService.get(productId)
        when (newValue) {
            // 값 자체의 규칙이 타입에 있다(기준가는 Money가 0 이상으로 강제). 활성화·단종은 상태 전이라 적용 시점에만 본다.
            is ProductFieldValue.Name,
            is ProductFieldValue.Description,
            is ProductFieldValue.Image,
            is ProductFieldValue.BasePrice,
            ProductFieldValue.Activation,
            ProductFieldValue.Discontinuation,
            -> Unit
            is ProductFieldValue.Category -> productReferences.requireChildCategory(newValue.categoryId)
            is ProductFieldValue.Tags -> productReferences.requireTagsExist(newValue.tagIds)
            is ProductFieldValue.Groups -> productReferences.requireGroupsExist(newValue.groupIds)
            is ProductFieldValue.Scope -> productReferences.requireTargetStoresExist(newValue.storeScope)
            is ProductFieldValue.OptionGroupLinks -> {
                Product.validateNoDuplicateOptionGroup(newValue.optionGroupIds)
                productReferences.requireOptionGroupsExist(newValue.optionGroupIds)
            }
            // 예외 가격은 Money가 0 이상으로 강제한다. 옵션 그룹이 이 상품에 연결됐는지, 옵션 키가 있는지,
            // 제외로 선택 가능한 옵션이 0개가 되는지는 적용 전에 바뀔 수 있어 적용 시점에만 본다.
            is ProductFieldValue.OptionOverrides -> productReferences.requireOptionGroupsExist(listOf(newValue.optionGroupId))
        }
    }

    private suspend fun validateForOptionGroup(
        optionGroupId: OptionGroupId,
        newValue: OptionGroupFieldValue,
    ) {
        optionGroupService.get(optionGroupId)
        when (newValue) {
            // 옵션 1개 이상, 옵션 키 유일은 스냅샷만으로 판단할 수 있다. 연결 상품의 선택 가능한 옵션 수는
            // 그 사이 연결·예외가 바뀔 수 있어 적용 시점에 OptionReplacementPolicy가 본다.
            is OptionGroupFieldValue.Options -> OptionGroup.validateOptions(newValue.options)
        }
    }
}
