package com.dozycoffee.catalog.schedule.infrastructure

import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

// 다른 통합 테스트는 스케줄러를 끈다(IntegrationTest). 이 클래스만 짧은 간격으로 켜고,
// 끝나면 컨텍스트를 닫아 스케줄러가 다른 테스트의 대기 예약을 건드리지 않게 한다.
@TestPropertySource(properties = ["catalog.schedule.batch.enabled=true", "catalog.schedule.batch.fixed-delay=PT0.2S"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("S2. 예약 적용 스케줄러 (요구사항 1.4)")
class ScheduledChangeBatchSchedulerTest : IntegrationTest() {
    @Test
    fun `적용 시각이 지난 대기 예약을 스스로 적용한다`() =
        runBlocking {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
            execute(
                """
                INSERT INTO products (name, category_id, base_price, status, store_scope, tracks_inventory)
                VALUES ('아메리카노', 2, 4500, 'ACTIVE', 'ALL', false)
                """.trimIndent(),
            )
            execute(
                """
                INSERT INTO scheduled_changes (target_id, target_kind, field_name, new_value, effective_date, effective_at)
                VALUES (1, 'PRODUCT', 'name', '{"type": "name", "value": "아이스 아메리카노"}', '2026-09-22', '2026-09-21T15:00:00Z')
                """.trimIndent(),
            )

            withTimeout(10_000) {
                while (count("SELECT count(*) FROM scheduled_changes WHERE status = 'APPLIED'") == 0L) delay(100)
            }

            assertEquals(1, count("SELECT count(*) FROM products WHERE name = '아이스 아메리카노'"))
        }
}
