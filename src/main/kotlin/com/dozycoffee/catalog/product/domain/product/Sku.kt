package com.dozycoffee.catalog.product.domain.product

// 외부 시스템(POS, 재고관리 서비스 등) 연동 시 식별 키. 등록 시점에는 미부여일 수
// 있어 Product에는 nullable로 보관한다(ERD상 NULL 허용, UNIQUE).
@JvmInline
value class Sku(
    val value: String,
)
