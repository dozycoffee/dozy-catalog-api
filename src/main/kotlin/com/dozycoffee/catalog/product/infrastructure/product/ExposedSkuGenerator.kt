package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.product.application.product.SkuGenerator
import com.dozycoffee.catalog.product.domain.product.Sku
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.springframework.stereotype.Component

// 전용 시퀀스(product_sku_seq, V4)에서 번호를 받아 DZ-00000123 형식으로 만든다.
// 시퀀스라 값이 겹치지 않고, 삭제한 상품의 SKU가 다시 쓰이지 않는다.
@Component
class ExposedSkuGenerator : SkuGenerator {
    override suspend fun next(): Sku {
        val sequenceValue =
            TransactionManager
                .current()
                .exec(
                    "SELECT nextval('$SEQUENCE_NAME')",
                    explicitStatementType = StatementType.SELECT,
                ) { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
                ?.first()
                ?: error("시퀀스 $SEQUENCE_NAME 에서 값을 받지 못했습니다")
        return Sku("$PREFIX${sequenceValue.toString().padStart(NUMBER_LENGTH, '0')}")
    }

    private companion object {
        const val SEQUENCE_NAME = "product_sku_seq"
        const val PREFIX = "DZ-"
        const val NUMBER_LENGTH = 8
    }
}
