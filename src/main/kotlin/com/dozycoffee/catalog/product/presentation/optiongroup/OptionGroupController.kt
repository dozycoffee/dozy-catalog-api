package com.dozycoffee.catalog.product.presentation.optiongroup

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.common.web.VersionHeaders
import com.dozycoffee.catalog.product.application.optiongroup.OptionGroupApplicationService
import com.dozycoffee.catalog.product.application.optiongroup.query.OptionGroupFilter
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.presentation.optiongroup.dto.ChangeOptionGroupDefinitionRequest
import com.dozycoffee.catalog.product.presentation.optiongroup.dto.OptionGroupResponse
import com.dozycoffee.catalog.product.presentation.optiongroup.dto.RegisterOptionGroupRequest
import com.dozycoffee.catalog.product.presentation.optiongroup.dto.ReplaceOptionsRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 옵션 그룹 API(docs/api/product.md 옵션 그룹, 요구사항 1.9).
// 정의·옵션 목록 변경은 화면이 보던 버전을 If-Match로 받는다(ADR-0013). 삭제는 연결 상품 여부로만 막으므로 버전을 받지 않는다.
@RestController
@RequestMapping("/api/v1/admin/option-groups")
class OptionGroupController(
    private val optionGroupService: OptionGroupApplicationService,
) {
    @PostMapping
    suspend fun register(
        @RequestBody request: RegisterOptionGroupRequest,
    ): ResponseEntity<OptionGroupResponse> {
        val optionGroup = optionGroupService.register(request.toCommand())
        return VersionHeaders.createdWithVersion(
            "$BASE_PATH/${optionGroup.id.value}",
            OptionGroupResponse.from(optionGroup),
            optionGroup.version,
        )
    }

    // 등록 순. 페이징하지 않는다.
    @GetMapping
    suspend fun list(
        @RequestParam ids: String?,
        @RequestParam keyword: String?,
    ): List<OptionGroupResponse> {
        val filter = OptionGroupFilter(ids = ListParams.ids(ids) { OptionGroupId(it) }, keyword = keyword)
        return optionGroupService.list(filter).map(OptionGroupResponse::from)
    }

    @GetMapping("/{optionGroupId}")
    suspend fun get(
        @PathVariable optionGroupId: Long,
    ): ResponseEntity<OptionGroupResponse> {
        val optionGroup = optionGroupService.get(OptionGroupId(optionGroupId))
        return VersionHeaders.okWithVersion(OptionGroupResponse.from(optionGroup), optionGroup.version)
    }

    // 이름·선택 방식·필수 여부. 옵션 목록은 /options로 바꾼다.
    @PutMapping("/{optionGroupId}")
    suspend fun changeDefinition(
        @PathVariable optionGroupId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: ChangeOptionGroupDefinitionRequest,
    ): ResponseEntity<OptionGroupResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        val optionGroup = optionGroupService.changeDefinition(request.toCommand(OptionGroupId(optionGroupId), version))
        return VersionHeaders.okWithVersion(OptionGroupResponse.from(optionGroup), optionGroup.version)
    }

    // 옵션 목록 즉시 교체(시나리오 S5). 사라진 옵션 키의 상품별 예외는 모든 연결 상품에서 함께 삭제된다.
    @PutMapping("/{optionGroupId}/options")
    suspend fun replaceOptions(
        @PathVariable optionGroupId: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: ReplaceOptionsRequest,
    ): ResponseEntity<OptionGroupResponse> {
        val version = VersionHeaders.requireIfMatch(ifMatch)
        val optionGroup = optionGroupService.replaceOptions(request.toCommand(OptionGroupId(optionGroupId), version))
        return VersionHeaders.okWithVersion(OptionGroupResponse.from(optionGroup), optionGroup.version)
    }

    @DeleteMapping("/{optionGroupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @PathVariable optionGroupId: Long,
    ) {
        optionGroupService.delete(OptionGroupId(optionGroupId))
    }

    private companion object {
        const val BASE_PATH = "/api/v1/admin/option-groups"
    }
}
