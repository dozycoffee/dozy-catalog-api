package com.dozycoffee.catalog.domain.category

import com.dozycoffee.catalog.domain.category.exception.InvalidParentCategoryException
import com.dozycoffee.catalog.domain.shared.AggregateRoot

// categories는 2단계 계층만 허용한다(catalog-erd.md). TopLevelCategory/ChildCategory로
// 타입을 나눠서 "부모가 이미 소분류인 경우"와 자기참조(하위 타입 간 전이 한정)를
// 컴파일 타임에 표현 불가능하게 만든다. 유일하게 남는 런타임 검증은
// TopLevelCategory.becomeChildOf의 자기참조/하위 카테고리 보유 여부뿐이다.
sealed class Category(
    id: CategoryId,
    name: String,
) : AggregateRoot<CategoryId>(id) {
    var name: String = name
        protected set

    fun rename(newName: String) {
        this.name = newName
    }
}

class TopLevelCategory internal constructor(
    id: CategoryId,
    name: String,
) : Category(id, name) {
    // hasChildren은 DB 조회가 필요해 application 레이어가 미리 조회해 전달한다.
    fun becomeChildOf(
        parent: TopLevelCategory,
        hasChildren: Boolean,
    ): ChildCategory {
        if (parent.id == this.id) {
            throw InvalidParentCategoryException("자기 자신을 부모로 지정할 수 없습니다")
        }
        if (hasChildren) {
            throw InvalidParentCategoryException(
                "이미 하위 카테고리가 있는 카테고리는 다른 카테고리의 하위로 지정할 수 없습니다",
            )
        }
        return ChildCategory(id, name, parentId = parent.id)
    }
}

class ChildCategory internal constructor(
    id: CategoryId,
    name: String,
    val parentId: CategoryId,
) : Category(id, name) {
    fun changeParent(newParent: TopLevelCategory): ChildCategory = ChildCategory(id, name, parentId = newParent.id)

    fun becomeTopLevel(): TopLevelCategory = TopLevelCategory(id, name)
}
