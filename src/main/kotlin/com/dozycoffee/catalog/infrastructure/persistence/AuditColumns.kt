package com.dozycoffee.catalog.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.JavaOffsetDateTimeColumnType
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

// DB 시계 now(). 마이그레이션이 DEFAULT now()로 만들었으므로 Exposed도 같은 표현을 써야 스키마 검사가 통과한다
// (Exposed 기본 제공 CurrentTimestampWithTimeZone은 CURRENT_TIMESTAMP로 표현되어 다르게 보인다).
val DbNow = CustomFunction<OffsetDateTime>("now", JavaOffsetDateTimeColumnType())

// 감사 컬럼 created_at, updated_at. 값은 DB 시계로 채운다(docs/adr/0010).
fun Table.auditTimestamp(name: String): Column<OffsetDateTime> = timestampWithTimeZone(name).defaultExpression(DbNow)
