package com.dozycoffee.catalog.common.exposed

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.JsonColumnMarker
import org.jetbrains.exposed.v1.core.Table
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// 값과 JSON 트리 사이의 변환. 저장 형식을 코드에 명시적으로 적어, 클래스 이름·프로퍼티 이름을 바꿔도
// 이미 저장된 JSONB가 깨지지 않게 한다. 컬럼마다 하나씩 만든다.
interface JsonbCodec<T : Any> {
    fun toJson(value: T): JsonNode

    fun fromJson(json: JsonNode): T
}

// Jackson 기반 JSONB 컬럼 타입(docs/architecture/tech-stack.md 직렬화). exposed-json은 kotlinx.serialization
// 기반이라 쓰지 않는다. DB와는 문자열로 주고받고, Exposed R2DBC의 PostgreSQL 매퍼가 JsonColumnMarker를 보고
// io.r2dbc.postgresql.codec.Json으로 바인딩한다.
class JsonbColumnType<T : Any>(
    private val codec: JsonbCodec<T>,
) : ColumnType<T>(),
    JsonColumnMarker {
    override val usesBinaryFormat: Boolean = true
    override val needsBinaryFormatCast: Boolean = false

    override fun sqlType(): String = "JSONB"

    override fun notNullValueToDB(value: T): Any = mapper.writeValueAsString(codec.toJson(value))

    override fun valueFromDB(value: Any): T =
        when (value) {
            is String -> codec.fromJson(mapper.readTree(value))
            is ByteArray -> codec.fromJson(mapper.readTree(value))
            else -> error("JSONB 컬럼에서 예상하지 못한 값을 읽었습니다: ${value::class}")
        }

    // SQL 리터럴로 쓰일 때(로그, 기본값 비교 등)는 JSON 문자열을 따옴표로 감싼다.
    override fun nonNullValueToString(value: T): String = "'${mapper.writeValueAsString(codec.toJson(value)).replace("'", "''")}'"

    private companion object {
        // Table은 Spring 빈이 아니라 object라 주입받지 않고 여기서 만든다. 트리 모델만 다루므로 모듈 설정이 필요 없다.
        val mapper: JsonMapper = JsonMapper.builder().build()
    }
}

fun <T : Any> Table.jsonb(
    name: String,
    codec: JsonbCodec<T>,
): Column<T> = registerColumn(name, JsonbColumnType(codec))
