package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.product.domain.product.Sku

// SKU 발급(요구사항 1.2). 의미를 담지 않는 일련번호라 형식은 구현 한 곳에서만 만든다.
fun interface SkuGenerator {
    suspend fun next(): Sku
}
