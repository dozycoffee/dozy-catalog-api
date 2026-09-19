package com.dozycoffee.catalog.domain.scheduledchange

import com.dozycoffee.catalog.domain.scheduledchange.exception.InvalidScheduleStatusTransitionException
import com.dozycoffee.catalog.domain.scheduledchange.exception.NoPendingScheduleException
import com.dozycoffee.catalog.fixture.scheduledChange
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("ScheduledChange")
class ScheduledChangeTest {
    @Nested
    @DisplayName("취소")
    inner class Cancel {
        @Test
        fun `대기 중인 예약을 취소한다`() {
            val schedule = scheduledChange(status = ScheduleStatus.PENDING)

            schedule.cancel()

            assertEquals(ScheduleStatus.CANCELLED, schedule.status)
        }

        @Test
        fun `이미 적용된 예약은 취소할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.APPLIED)

            assertFailsWith<NoPendingScheduleException> { schedule.cancel() }
        }

        @Test
        fun `이미 취소된 예약은 다시 취소할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.CANCELLED)

            assertFailsWith<NoPendingScheduleException> { schedule.cancel() }
        }
    }

    @Nested
    @DisplayName("적용")
    inner class Apply {
        @Test
        fun `대기 중인 예약을 적용 완료로 기록한다`() {
            val schedule = scheduledChange(status = ScheduleStatus.PENDING)

            schedule.apply()

            assertEquals(ScheduleStatus.APPLIED, schedule.status)
        }

        @Test
        fun `취소된 예약은 적용할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.CANCELLED)

            assertFailsWith<InvalidScheduleStatusTransitionException> { schedule.apply() }
        }

        @Test
        fun `이미 적용된 예약은 다시 적용할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.APPLIED)

            assertFailsWith<InvalidScheduleStatusTransitionException> { schedule.apply() }
        }
    }

    @Nested
    @DisplayName("실패 기록")
    inner class Fail {
        @Test
        fun `대기 중인 예약을 실패로 기록한다`() {
            val schedule = scheduledChange(status = ScheduleStatus.PENDING)

            schedule.fail()

            assertEquals(ScheduleStatus.FAILED, schedule.status)
        }

        @Test
        fun `실패해도 예약값은 그대로 유지된다`() {
            // 적용 실패 시 기존 값을 유지하고 실패 사실만 기록한다(1.4).
            val schedule =
                scheduledChange(
                    fieldName = "basePrice",
                    newValue = 5000L,
                    effectiveDate = LocalDate.of(2026, 10, 1),
                    status = ScheduleStatus.PENDING,
                )

            schedule.fail()

            assertEquals("basePrice", schedule.fieldName)
            assertEquals(5000L, schedule.newValue)
            assertEquals(LocalDate.of(2026, 10, 1), schedule.effectiveDate)
        }

        @Test
        fun `이미 실패한 예약은 다시 실패로 기록할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.FAILED)

            assertFailsWith<InvalidScheduleStatusTransitionException> { schedule.fail() }
        }
    }
}
