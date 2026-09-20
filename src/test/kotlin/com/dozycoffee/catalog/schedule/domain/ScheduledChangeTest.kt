package com.dozycoffee.catalog.schedule.domain

import com.dozycoffee.catalog.fixture.TestScheduledValue
import com.dozycoffee.catalog.fixture.scheduledChange
import com.dozycoffee.catalog.schedule.domain.exception.InvalidEffectiveDateException
import com.dozycoffee.catalog.schedule.domain.exception.InvalidScheduleStatusTransitionException
import com.dozycoffee.catalog.schedule.domain.exception.NoPendingScheduleException
import com.dozycoffee.catalog.schedule.domain.exception.ScheduledChangeErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("ScheduledChange")
class ScheduledChangeTest {
    @Nested
    @DisplayName("등록")
    inner class Register {
        private val seoul = ZoneId.of("Asia/Seoul")
        private val today = LocalDate.of(2026, 9, 19)

        @Test
        fun `적용일이 내일 이후면 등록된다`() {
            val newSchedule = register(effectiveDate = today.plusDays(1))

            assertEquals(LocalDate.of(2026, 9, 20), newSchedule.effectiveDate)
        }

        @Test
        fun `적용 시각은 적용일 00시를 업무 시간대로 해석한 순간이다`() {
            // 서울 2026-10-01 00:00은 UTC로 2026-09-30 15:00이다.
            val newSchedule = register(effectiveDate = LocalDate.of(2026, 10, 1))

            assertEquals(Instant.parse("2026-09-30T15:00:00Z"), newSchedule.effectiveAt)
        }

        @Test
        fun `대상 종류와 필드 이름은 예약 값이 정한다`() {
            val newSchedule =
                register(
                    effectiveDate = today.plusDays(1),
                    newValue = TestScheduledValue(targetKind = TargetKind.OPTION_GROUP, fieldName = "options"),
                )

            assertEquals(TargetKind.OPTION_GROUP, newSchedule.targetKind)
            assertEquals("options", newSchedule.fieldName)
        }

        @Test
        fun `적용일이 오늘이면 거부한다`() {
            // 오늘 00시는 이미 지났으므로 오늘 적용은 즉시 반영으로 한다(요구사항 1.4).
            val exception = assertFailsWith<InvalidEffectiveDateException> { register(effectiveDate = today) }

            assertEquals(ScheduledChangeErrorCode.INVALID_EFFECTIVE_DATE, exception.errorCode)
        }

        @Test
        fun `적용일이 과거면 거부한다`() {
            assertFailsWith<InvalidEffectiveDateException> { register(effectiveDate = today.minusDays(1)) }
        }

        private fun register(
            effectiveDate: LocalDate,
            newValue: TestScheduledValue = TestScheduledValue(),
        ) = ScheduledChange.NewScheduledChange.of(
            targetId = 100,
            newValue = newValue,
            effectiveDate = effectiveDate,
            today = today,
            businessZone = seoul,
        )
    }

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
                    newValue = TestScheduledValue(fieldName = "basePrice", value = 5000L),
                    effectiveDate = LocalDate.of(2026, 10, 1),
                    status = ScheduleStatus.PENDING,
                )

            schedule.fail()

            assertEquals("basePrice", schedule.fieldName)
            assertEquals(TestScheduledValue(fieldName = "basePrice", value = 5000L), schedule.newValue)
            assertEquals(LocalDate.of(2026, 10, 1), schedule.effectiveDate)
        }

        @Test
        fun `이미 실패한 예약은 다시 실패로 기록할 수 없다`() {
            val schedule = scheduledChange(status = ScheduleStatus.FAILED)

            assertFailsWith<InvalidScheduleStatusTransitionException> { schedule.fail() }
        }
    }
}
