package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.product.application.optiongroup.OptionGroupApplicationService
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.ProductFieldApplicationService
import com.dozycoffee.catalog.product.application.product.ProductOptionApplicationService
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import org.springframework.stereotype.Component

// 예약 값 하나를 대상에 반영한다. 대상 모듈의 유스케이스만 부르고 Product·OptionGroup을 직접 조작하지 않는다 —
// 잠금·검증·이벤트 발행이 그 모듈 한곳에 남아야 즉시 반영과 예약 적용이 같게 동작한다(ADR-0015).
//
// 값 타입을 when으로 빠짐없이 분기하므로, 예약 가능한 필드를 추가하고 적용을 빠뜨리면 컴파일 에러가 된다(ADR-0014).
// 규칙 위반은 DomainException으로 올라가고, 예약을 실패로 기록하는 일은 부르는 쪽(배치)이 한다.
@Component
class ScheduledChangeApplier(
    private val productService: ProductApplicationService,
    private val productFieldService: ProductFieldApplicationService,
    private val productOptionService: ProductOptionApplicationService,
    private val optionGroupService: OptionGroupApplicationService,
) {
    suspend fun apply(scheduledChange: ScheduledChange) {
        val newValue = scheduledChange.newValue
        // 저장된 예약 값은 언제나 이 sealed 계층이다. 아니라면 등록·직렬화 경로의 코드 오류다(docs/architecture/exception.md).
        check(newValue is ScheduledFieldValue) {
            "알 수 없는 예약 값입니다: 예약 ${scheduledChange.id.value}, ${newValue::class.simpleName}"
        }
        when (newValue) {
            is ProductFieldValue -> applyToProduct(ProductId(scheduledChange.targetId), newValue)
            is OptionGroupFieldValue -> applyToOptionGroup(OptionGroupId(scheduledChange.targetId), newValue)
        }
    }

    private suspend fun applyToProduct(
        productId: ProductId,
        newValue: ProductFieldValue,
    ) {
        when (newValue) {
            is ProductFieldValue.Name -> productFieldService.changeName(productId, newValue.name)
            is ProductFieldValue.Category -> productFieldService.changeCategory(productId, newValue.categoryId)
            is ProductFieldValue.Description -> productFieldService.changeDescription(productId, newValue.description)
            is ProductFieldValue.Image -> productFieldService.changeImage(productId, newValue.imageUrl)
            is ProductFieldValue.BasePrice -> productFieldService.changeBasePrice(productId, newValue.basePrice)
            is ProductFieldValue.Tags -> productFieldService.changeTags(productId, newValue.tagIds)
            is ProductFieldValue.Groups -> productFieldService.changeGroups(productId, newValue.groupIds)
            is ProductFieldValue.Scope -> productFieldService.changeStoreScope(productId, newValue.storeScope)
            // 상태 전이는 즉시 반영과 같은 전용 액션을 그대로 쓴다. 적용 시점에 허용되지 않는 전이면
            // (예: 단종 예약이 활성화보다 앞서 아직 Draft인 상품) 예약만 실패로 기록된다(요구사항 1.3).
            ProductFieldValue.Activation -> productService.activate(productId)
            ProductFieldValue.Discontinuation -> productService.discontinue(productId)
            is ProductFieldValue.OptionGroupLinks ->
                productOptionService.replaceOptionGroupLinks(productId, newValue.optionGroupIds)
            is ProductFieldValue.OptionOverrides ->
                productOptionService.replaceOptionOverrides(productId, newValue.optionGroupId, newValue.overrides)
        }
    }

    private suspend fun applyToOptionGroup(
        optionGroupId: OptionGroupId,
        newValue: OptionGroupFieldValue,
    ) {
        when (newValue) {
            is OptionGroupFieldValue.Options -> optionGroupService.replaceOptions(optionGroupId, newValue.options)
        }
    }
}
