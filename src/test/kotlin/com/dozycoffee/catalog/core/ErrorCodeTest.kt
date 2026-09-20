package com.dozycoffee.catalog.core

import com.dozycoffee.catalog.domain.category.exception.CategoryErrorCode
import com.dozycoffee.catalog.domain.optiongroup.exception.OptionGroupErrorCode
import com.dozycoffee.catalog.domain.product.exception.ProductErrorCode
import com.dozycoffee.catalog.schedule.domain.exception.ScheduledChangeErrorCode
import com.dozycoffee.catalog.store.domain.availability.exception.StoreProductAvailabilityErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@DisplayName("ErrorCode")
class ErrorCodeTest {
    // 새 ErrorCode enum을 추가하면 이 목록에도 추가해야 유일성 검사 대상이 된다.
    private val allErrorCodes: List<ErrorCode> =
        SharedErrorCode.entries +
            CategoryErrorCode.entries +
            OptionGroupErrorCode.entries +
            ProductErrorCode.entries +
            ScheduledChangeErrorCode.entries +
            StoreProductAvailabilityErrorCode.entries

    @Test
    fun `모든 애그리거트에 걸쳐 code 문자열은 유일하다`() {
        val duplicates =
            allErrorCodes
                .groupBy { it.code }
                .filterValues { it.size > 1 }
                .keys

        assertTrue(duplicates.isEmpty(), "중복된 code: $duplicates")
    }
}
