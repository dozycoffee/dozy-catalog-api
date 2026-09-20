package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.application.command.CancelScheduledChangeCommand
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.exception.InvalidEffectiveDateException
import com.dozycoffee.catalog.schedule.domain.exception.NoPendingScheduleException
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.MutableClock
import com.dozycoffee.catalog.support.MutableClockConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// 오늘은 MutableClock 기준 2026-09-21(업무 시간대 Asia/Seoul)이다.
@Import(MutableClockConfiguration::class)
@DisplayName("S2. 예약 등록·취소·조회 (요구사항 1.4)")
class ScheduledChangeApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ScheduledChangeApplicationService

    @Autowired
    private lateinit var scheduledChangeRepository: ScheduledChangeRepository

    @Autowired
    private lateinit var clock: MutableClock

    private val product = ProductId(1)
    private val sizeGroup = OptionGroupId(11)
    private val shotGroup = OptionGroupId(12)
    private val tomorrow = LocalDate.of(2026, 9, 22)

    @BeforeEach
    fun resetClock() {
        clock.reset()
    }

    @Nested
    @DisplayName("S2 기본 흐름: 등록")
    inner class Registration {
        @Test
        fun `등록한 예약은 대기 상태가 되고 적용 시각은 업무 시간대의 00시다`() =
            runTest {
                val registered =
                    service.register(
                        RegisterScheduledChangeCommand.forProduct(
                            product,
                            ProductFieldValue.BasePrice(Money(5200)),
                            LocalDate.of(2026, 10, 1),
                        ),
                    )

                val found = assertNotNull(tx { scheduledChangeRepository.findById(registered.id) })
                assertEquals(ScheduleStatus.PENDING, found.status)
                assertEquals("basePrice", found.fieldName)
                assertEquals(ProductFieldValue.BasePrice(Money(5200)), found.newValue)
                assertEquals(LocalDate.of(2026, 10, 1), found.effectiveDate)
                // 서울 2026-10-01 00:00 = 2026-09-30T15:00:00Z
                assertEquals("2026-09-30T15:00:00Z", found.effectiveAt.toString())
            }

        @Test
        fun `옵션 목록 예약은 등록 시점의 옵션 목록 전체를 스냅샷으로 담는다`() =
            runTest {
                val snapshot =
                    listOf(
                        Option(OptionKey("TALL"), "톨", Money(0)),
                        Option(OptionKey("GRANDE"), "그란데", Money(500)),
                    )

                val registered =
                    service.register(
                        RegisterScheduledChangeCommand.forOptionGroup(
                            sizeGroup,
                            OptionGroupFieldValue.Options(snapshot),
                            tomorrow,
                        ),
                    )

                val found = assertNotNull(tx { scheduledChangeRepository.findById(registered.id) })
                assertEquals(OptionGroupFieldValue.Options(snapshot), found.newValue)
            }

        @Test
        fun `같은 대상의 같은 필드에 새로 등록하면 기존 예약은 취소되고 새 예약만 대기한다`() =
            runTest {
                val first = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))

                val second = service.register(command(ProductFieldValue.Name("따뜻한 아메리카노"), tomorrow))

                assertEquals(ScheduleStatus.CANCELLED, tx { scheduledChangeRepository.findById(first.id) }?.status)
                assertEquals(ScheduleStatus.PENDING, tx { scheduledChangeRepository.findById(second.id) }?.status)
                assertEquals(listOf(second.id), pendingForProduct().map { it.id })
            }

        @Test
        fun `활성화 예약과 단종 예약은 서로 다른 필드라 함께 대기한다`() =
            runTest {
                service.register(command(ProductFieldValue.Activation, LocalDate.of(2026, 10, 1)))
                service.register(command(ProductFieldValue.Discontinuation, LocalDate.of(2026, 10, 31)))

                assertEquals(
                    listOf("activation" to LocalDate.of(2026, 10, 1), "discontinuation" to LocalDate.of(2026, 10, 31)),
                    pendingForProduct().map { it.fieldName to it.effectiveDate },
                )
            }

        @Test
        fun `옵션 그룹이 다른 예외 예약은 함께 대기하고 같은 옵션 그룹의 예약만 대체된다`() =
            runTest {
                val forSize =
                    service.register(command(optionOverrides(sizeGroup, OptionOverride.Exclude(OptionKey("TALL"))), tomorrow))
                service.register(command(optionOverrides(shotGroup, OptionOverride.Exclude(OptionKey("EXTRA"))), tomorrow))

                val replacedSize =
                    service.register(
                        command(optionOverrides(sizeGroup, OptionOverride.Price(OptionKey("TALL"), Money(300))), tomorrow),
                    )

                assertEquals(ScheduleStatus.CANCELLED, tx { scheduledChangeRepository.findById(forSize.id) }?.status)
                assertEquals(
                    setOf("optionOverrides:11", "optionOverrides:12"),
                    pendingForProduct().map { it.fieldName }.toSet(),
                )
                assertEquals(
                    ScheduleStatus.PENDING,
                    tx { scheduledChangeRepository.findById(replacedSize.id) }?.status,
                )
            }

        @ParameterizedTest(name = "적용일 {0}")
        @ValueSource(strings = ["2026-09-21", "2026-09-20"])
        fun `오늘이나 지난 날짜로는 예약할 수 없다`(effectiveDate: String) =
            runTest {
                assertFailsWith<InvalidEffectiveDateException> {
                    service.register(command(ProductFieldValue.Name("아메리카노"), LocalDate.parse(effectiveDate)))
                }
                assertEquals(0, count("SELECT count(*) FROM scheduled_changes"))
            }

        @Test
        fun `거부된 등록은 대기 중이던 예약을 건드리지 않는다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))

                assertFailsWith<InvalidEffectiveDateException> {
                    service.register(command(ProductFieldValue.Name("라떼"), LocalDate.of(2026, 9, 21)))
                }

                assertEquals(listOf(pending.id), pendingForProduct().map { it.id })
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes"))
            }
    }

    @Nested
    @DisplayName("S2 대체·예외 흐름: 취소")
    inner class Cancellation {
        @Test
        fun `대기 중인 예약을 취소하면 취소로 기록된다`() =
            runTest {
                val registered = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))

                service.cancel(CancelScheduledChangeCommand.forProduct(product, "name"))

                assertEquals(ScheduleStatus.CANCELLED, tx { scheduledChangeRepository.findById(registered.id) }?.status)
                assertEquals(emptyList(), pendingForProduct())
            }

        @Test
        fun `대기 중인 예약이 없는 대상과 필드의 취소는 거부한다`() =
            runTest {
                service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))

                assertFailsWith<NoPendingScheduleException> {
                    service.cancel(CancelScheduledChangeCommand.forProduct(product, "basePrice"))
                }
                assertFailsWith<NoPendingScheduleException> {
                    service.cancel(CancelScheduledChangeCommand.forOptionGroup(sizeGroup, "options"))
                }
                assertEquals(1, pendingForProduct().size)
            }

        @Test
        fun `배치가 먼저 처리한 예약은 취소할 수 없고 처리된 상태로 남는다`() =
            runTest {
                val registered = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))
                execute("UPDATE scheduled_changes SET status = 'APPLIED' WHERE id = ${registered.id.value}")

                assertFailsWith<NoPendingScheduleException> {
                    service.cancel(CancelScheduledChangeCommand.forProduct(product, "name"))
                }

                assertEquals(ScheduleStatus.APPLIED, tx { scheduledChangeRepository.findById(registered.id) }?.status)
            }
    }

    @Nested
    @DisplayName("S2 기본 흐름: 조회")
    inner class PendingQuery {
        @Test
        fun `대상의 대기 예약을 필드 이름과 예약 값, 적용 날짜와 함께 돌려준다`() =
            runTest {
                service.register(command(ProductFieldValue.BasePrice(Money(5200)), LocalDate.of(2026, 10, 1)))
                service.register(command(ProductFieldValue.Activation, LocalDate.of(2026, 10, 2)))

                val pending = pendingForProduct()

                assertEquals(
                    listOf(
                        Triple("basePrice", ProductFieldValue.BasePrice(Money(5200)), LocalDate.of(2026, 10, 1)),
                        Triple("activation", ProductFieldValue.Activation, LocalDate.of(2026, 10, 2)),
                    ),
                    pending.map { Triple(it.fieldName, it.newValue, it.effectiveDate) },
                )
            }

        @Test
        fun `취소되거나 처리된 예약과 다른 대상의 예약은 나오지 않는다`() =
            runTest {
                val cancelled = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))
                service.cancel(CancelScheduledChangeCommand.forProduct(product, "name"))
                service.register(
                    RegisterScheduledChangeCommand.forProduct(
                        ProductId(2),
                        ProductFieldValue.Name("라떼"),
                        tomorrow,
                    ),
                )
                val optionGroupSchedule =
                    service.register(
                        RegisterScheduledChangeCommand.forOptionGroup(
                            sizeGroup,
                            OptionGroupFieldValue.Options(listOf(Option(OptionKey("TALL"), "톨", Money(0)))),
                            tomorrow,
                        ),
                    )

                assertEquals(emptyList(), pendingForProduct())
                assertEquals(
                    listOf(optionGroupSchedule.id),
                    service.findPendingForOptionGroup(sizeGroup).map { it.id },
                )
                assertEquals(ScheduleStatus.CANCELLED, tx { scheduledChangeRepository.findById(cancelled.id) }?.status)
            }
    }

    private fun command(
        newValue: ProductFieldValue,
        effectiveDate: LocalDate,
    ) = RegisterScheduledChangeCommand.forProduct(product, newValue, effectiveDate)

    private fun optionOverrides(
        optionGroupId: OptionGroupId,
        vararg overrides: OptionOverride,
    ) = ProductFieldValue.OptionOverrides(optionGroupId, overrides.toList())

    private suspend fun pendingForProduct() = service.findPendingForProduct(product)
}
