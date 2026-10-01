package com.dozycoffee.catalog.schedule.presentation

import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.application.ScheduledChangeApplicationService
import com.dozycoffee.catalog.schedule.application.command.CancelScheduledChangeCommand
import com.dozycoffee.catalog.schedule.presentation.dto.RegisterScheduledChangeRequest
import com.dozycoffee.catalog.schedule.presentation.dto.ScheduledChangeResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 예약 API(docs/api/schedule.md). 예약은 대상(상품, 옵션 그룹)의 하위 리소스이고 예약 ID 대신 필드 이름으로 가리킨다.
// 같은 대상·같은 필드의 대기 예약은 최대 1건이라 필드 이름만으로 하나가 정해지기 때문이다.
//
// 필드는 {*field}로 경로의 나머지를 통째로 받는다. 옵션 그룹별 예외(option-overrides/7)가 두 단계라
// 매핑을 따로 두면 모르는 두 단계 경로가 UNKNOWN_SCHEDULE_FIELD가 아니라 없는 경로(NOT_FOUND)가 되기 때문이다.
@RestController
@RequestMapping("/api/v1/admin")
class ScheduledChangeController(
    private val service: ScheduledChangeApplicationService,
) {
    @GetMapping("/products/{productId}/scheduled-changes")
    suspend fun listForProduct(
        @PathVariable productId: Long,
    ): List<ScheduledChangeResponse> = service.findPendingForProduct(ProductId(productId)).map(ScheduledChangeResponse::from)

    @PutMapping("/products/{productId}/scheduled-changes/{*field}")
    suspend fun registerForProduct(
        @PathVariable productId: Long,
        @PathVariable field: String,
        @RequestBody request: RegisterScheduledChangeRequest,
    ): ScheduledChangeResponse {
        val command = request.toCommand(ProductId(productId), ProductScheduleField.parse(field.removePrefix("/")))
        return ScheduledChangeResponse.from(service.register(command))
    }

    @DeleteMapping("/products/{productId}/scheduled-changes/{*field}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun cancelForProduct(
        @PathVariable productId: Long,
        @PathVariable field: String,
    ) {
        val scheduleField = ProductScheduleField.parse(field.removePrefix("/"))
        service.cancel(CancelScheduledChangeCommand.forProduct(ProductId(productId), scheduleField.fieldName))
    }

    @GetMapping("/option-groups/{optionGroupId}/scheduled-changes")
    suspend fun listForOptionGroup(
        @PathVariable optionGroupId: Long,
    ): List<ScheduledChangeResponse> = service.findPendingForOptionGroup(OptionGroupId(optionGroupId)).map(ScheduledChangeResponse::from)

    @PutMapping("/option-groups/{optionGroupId}/scheduled-changes/{*field}")
    suspend fun registerForOptionGroup(
        @PathVariable optionGroupId: Long,
        @PathVariable field: String,
        @RequestBody request: RegisterScheduledChangeRequest,
    ): ScheduledChangeResponse {
        val command = request.toCommand(OptionGroupId(optionGroupId), OptionGroupScheduleField.parse(field.removePrefix("/")))
        return ScheduledChangeResponse.from(service.register(command))
    }

    @DeleteMapping("/option-groups/{optionGroupId}/scheduled-changes/{*field}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun cancelForOptionGroup(
        @PathVariable optionGroupId: Long,
        @PathVariable field: String,
    ) {
        val scheduleField = OptionGroupScheduleField.parse(field.removePrefix("/"))
        service.cancel(CancelScheduledChangeCommand.forOptionGroup(OptionGroupId(optionGroupId), scheduleField.fieldName))
    }
}
