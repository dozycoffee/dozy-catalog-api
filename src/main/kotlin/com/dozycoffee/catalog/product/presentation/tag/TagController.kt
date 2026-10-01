package com.dozycoffee.catalog.product.presentation.tag

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.product.application.tag.TagApplicationService
import com.dozycoffee.catalog.product.application.tag.command.RenameTagCommand
import com.dozycoffee.catalog.product.application.tag.query.TagFilter
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.presentation.tag.dto.RenameTagRequest
import com.dozycoffee.catalog.product.presentation.tag.dto.TagResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 태그 API(docs/api/product.md 태그, 요구사항 1.7). 태그는 상품 등록·수정에서 이름으로 입력하면 생기므로 등록 엔드포인트가 없다.
@RestController
@RequestMapping("/api/v1/admin/tags")
class TagController(
    private val tagService: TagApplicationService,
) {
    // 이름 순.
    @GetMapping
    suspend fun list(
        @RequestParam ids: String?,
        @RequestParam keyword: String?,
    ): List<TagResponse> {
        val filter = TagFilter(ids = ListParams.ids(ids) { TagId(it) }, keyword = keyword)
        return tagService.list(filter).map(TagResponse::from)
    }

    @PutMapping("/{tagId}")
    suspend fun rename(
        @PathVariable tagId: Long,
        @RequestBody request: RenameTagRequest,
    ): TagResponse = TagResponse.from(tagService.rename(RenameTagCommand(TagId(tagId), request.name)))

    // 참조하던 모든 상품에서 자동으로 빠진다.
    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @PathVariable tagId: Long,
    ) {
        tagService.delete(TagId(tagId))
    }
}
