package com.dozycoffee.catalog.core

// Store BC(별도 서비스) 참조 — FK 제약 없음.
@JvmInline
value class StoreId(
    val value: Long,
)
