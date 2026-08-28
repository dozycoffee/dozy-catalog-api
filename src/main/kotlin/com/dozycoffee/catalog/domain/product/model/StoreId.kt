package com.dozycoffee.catalog.domain.product.model

// Store BC(별도 서비스) 참조 — FK 제약 없음.
@JvmInline
value class StoreId(
    val value: Long,
)
