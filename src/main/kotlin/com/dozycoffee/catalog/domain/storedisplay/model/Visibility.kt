package com.dozycoffee.catalog.domain.storedisplay.model

// 점주의 노출 의도만 담는다. 본사 단종으로 인한 비노출은 이 값에 반영하지 않는다
// — 단종 여부는 Product.status가 이미 갖고 있고, ProductVisibilityPolicy가
// 1단계에서 걸러내므로 여기에 중복 기록하면 점주 의도를 덮어쓰게 된다.
enum class Visibility {
    VISIBLE,
    HIDDEN,
}
