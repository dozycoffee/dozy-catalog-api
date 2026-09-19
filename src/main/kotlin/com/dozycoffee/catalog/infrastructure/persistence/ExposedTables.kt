package com.dozycoffee.catalog.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

// Flyway 스키마와 일치해야 하는 Exposed Table 목록. 애그리거트별 Repository를 구현하면서 Table 객체를 만들 때마다
// 여기에 추가한다. 통합 테스트(ExposedSchemaConsistencyTest)가 이 목록과 실제 스키마의 차이를 검사한다.
object ExposedTables {
    val all: List<Table> = emptyList()
}
