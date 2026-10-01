package com.dozycoffee.catalog.schedule.presentation.dto

import com.dozycoffee.catalog.common.web.InvalidRequestException
import tools.jackson.databind.JsonNode

// 예약 요청의 value는 필드마다 JSON 타입이 달라 JsonNode로 받아 필드별로 읽는다. 타입이 틀리면 Jackson이 본문을 읽을 때와
// 같은 문구로 400 INVALID_REQUEST를 던진다(docs/api/README.md 오류 응답). path는 문제가 된 위치(value[1].price 등)다.
internal class JsonValueReader(
    private val node: JsonNode?,
    private val path: String,
) {
    // JSON null과 빠진 값을 구분하지 않는다. 둘 다 "값 없음"이다.
    val isNull: Boolean get() = node == null || node.isNull || node.isMissingNode

    fun string(): String = nullableString() ?: invalid()

    fun nullableString(): String? {
        if (isNull) return null
        val value = node!!
        return if (value.isString) value.stringValue() else invalid()
    }

    fun long(): Long = nullableLong() ?: invalid()

    fun nullableLong(): Long? {
        if (isNull) return null
        val value = node!!
        return if (value.isIntegralNumber && value.canConvertToLong()) value.longValue() else invalid()
    }

    fun items(): List<JsonValueReader> {
        val value = node?.takeIf { it.isArray } ?: invalid()
        return value.values().mapIndexed { index, item -> JsonValueReader(item, "$path[$index]") }
    }

    fun field(name: String): JsonValueReader {
        val value = node?.takeIf { it.isObject } ?: invalid()
        return JsonValueReader(value.get(name), "$path.$name")
    }

    fun requireNull() {
        if (!isNull) invalid()
    }

    fun invalid(): Nothing = throw InvalidRequestException("요청 본문의 '$path' 값이 없거나 형식이 맞지 않습니다")
}
