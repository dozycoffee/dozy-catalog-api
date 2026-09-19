package com.dozycoffee.catalog.fixture

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.category.ChildCategory
import com.dozycoffee.catalog.domain.category.TopLevelCategory

fun topLevelCategory(
    id: Long = 1,
    name: String = "음료",
) = TopLevelCategory(CategoryId(id), name)

// 기본값은 topLevelCategory() 기본값(1)의 하위이고, product()의 기본 categoryId(10)와 같다.
fun childCategory(
    id: Long = 10,
    parentId: Long = 1,
    name: String = "커피",
) = ChildCategory(CategoryId(id), name, parentId = CategoryId(parentId))
