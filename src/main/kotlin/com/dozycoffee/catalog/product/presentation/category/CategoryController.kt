package com.dozycoffee.catalog.product.presentation.category

import com.dozycoffee.catalog.common.web.ListParams
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.ChangeCategoryParentCommand
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.category.command.RenameCategoryCommand
import com.dozycoffee.catalog.product.application.category.query.CategoryFilter
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.presentation.category.dto.CategoryResponse
import com.dozycoffee.catalog.product.presentation.category.dto.ChangeCategoryParentRequest
import com.dozycoffee.catalog.product.presentation.category.dto.RegisterCategoryRequest
import com.dozycoffee.catalog.product.presentation.category.dto.RenameCategoryRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

// 카테고리 API(docs/api/product.md 카테고리, 요구사항 1.6). 대분류·소분류를 같은 리소스로 다루고 parentId로 구분한다.
@RestController
@RequestMapping("/api/v1/admin/categories")
class CategoryController(
    private val categoryService: CategoryApplicationService,
) {
    // 대분류·소분류를 평평한 목록으로 등록 순으로 준다. 계층은 클라이언트가 parentId로 구성한다.
    @GetMapping
    suspend fun list(
        @RequestParam ids: String?,
        @RequestParam parentId: Long?,
        @RequestParam topLevel: Boolean?,
    ): List<CategoryResponse> {
        val filter =
            CategoryFilter(
                ids = ListParams.ids(ids) { CategoryId(it) },
                parentId = parentId?.let(::CategoryId),
                topLevelOnly = topLevel ?: false,
            )
        return categoryService.list(filter).map(CategoryResponse::from)
    }

    @PostMapping
    suspend fun register(
        @RequestBody request: RegisterCategoryRequest,
    ): ResponseEntity<CategoryResponse> {
        val category =
            when (val parentId = request.parentId) {
                null -> categoryService.registerTopLevel(request.name)
                else -> categoryService.registerChild(RegisterChildCategoryCommand(CategoryId(parentId), request.name))
            }
        return ResponseEntity
            .created(URI.create("$BASE_PATH/${category.id.value}"))
            .body(CategoryResponse.from(category))
    }

    @PutMapping("/{categoryId}")
    suspend fun rename(
        @PathVariable categoryId: Long,
        @RequestBody request: RenameCategoryRequest,
    ): CategoryResponse {
        val category = categoryService.rename(RenameCategoryCommand(CategoryId(categoryId), request.name))
        return CategoryResponse.from(category)
    }

    // parentId가 null이면 승격(대분류는 그대로), 값이 있으면 그 대분류의 소분류가 된다(소분류는 이동, 대분류는 강등).
    @PutMapping("/{categoryId}/parent")
    suspend fun changeParent(
        @PathVariable categoryId: Long,
        @RequestBody request: ChangeCategoryParentRequest,
    ): CategoryResponse {
        val category =
            when (val parentId = request.parentId) {
                null -> categoryService.promoteToTopLevel(CategoryId(categoryId))
                else -> categoryService.changeParent(ChangeCategoryParentCommand(CategoryId(categoryId), CategoryId(parentId)))
            }
        return CategoryResponse.from(category)
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @PathVariable categoryId: Long,
    ) {
        categoryService.delete(CategoryId(categoryId))
    }

    private companion object {
        const val BASE_PATH = "/api/v1/admin/categories"
    }
}
