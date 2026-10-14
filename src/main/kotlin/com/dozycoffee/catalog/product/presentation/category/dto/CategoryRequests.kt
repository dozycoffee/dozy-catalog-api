package com.dozycoffee.catalog.product.presentation.category.dto

import com.fasterxml.jackson.annotation.JsonProperty

// parentId가 null이면 대분류, 값이 있으면 그 대분류의 소분류로 등록한다.
data class RegisterCategoryRequest(
    val name: String,
    val parentId: Long? = null,
)

data class RenameCategoryRequest(
    val name: String,
)

// parentId가 null이면 승격(대분류가 됨), 값이 있으면 그 대분류의 소분류가 된다(이동·강등).
// 빠뜨린 본문이 승격으로 읽히지 않도록 null이라도 필드는 반드시 보내게 한다.
// 같은 부모를 가진 카테고리 전체의 노출 순서. parentId가 null이면 대분류들의 순서다. 빠뜨리면 대분류 순서로 읽지 않도록
// null이라도 필드는 반드시 보내게 한다.
data class ReorderCategoriesRequest(
    @param:JsonProperty(required = true)
    val parentId: Long?,
    @param:JsonProperty(required = true)
    val categoryIds: List<Long>,
)

data class ChangeCategoryParentRequest(
    @param:JsonProperty(required = true)
    val parentId: Long?,
)
