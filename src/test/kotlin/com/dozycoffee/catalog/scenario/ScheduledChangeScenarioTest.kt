package com.dozycoffee.catalog.scenario

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.optiongroup.OptionGroupApplicationService
import com.dozycoffee.catalog.product.application.optiongroup.command.RegisterOptionGroupCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue
import com.dozycoffee.catalog.schedule.application.ScheduledChangeApplicationBatch
import com.dozycoffee.catalog.schedule.application.ScheduledChangeApplicationService
import com.dozycoffee.catalog.schedule.application.command.CancelScheduledChangeCommand
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.exception.NoPendingScheduleException
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.MutableClock
import com.dozycoffee.catalog.support.MutableClockConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// 여러 유스케이스에 걸친 흐름을 순서대로 밟는다(docs/architecture/testing.md).
// 오늘은 MutableClock 기준 2026-09-21(업무 시간대 Asia/Seoul)이다.
@Import(MutableClockConfiguration::class)
@DisplayName("S2. 예약 변경의 수명")
class ScheduledChangeScenarioTest : ApplicationTest() {
    @Autowired
    private lateinit var scheduledChangeService: ScheduledChangeApplicationService

    @Autowired
    private lateinit var batch: ScheduledChangeApplicationBatch

    @Autowired
    private lateinit var scheduledChangeRepository: ScheduledChangeRepository

    @Autowired
    private lateinit var productService: ProductApplicationService

    @Autowired
    private lateinit var optionGroupService: OptionGroupApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var clock: MutableClock

    private var coffee: CategoryId = CategoryId(0)

    @BeforeEach
    fun setUp() =
        runTest {
            clock.reset()
            val beverage = categoryService.registerTopLevel("음료")
            coffee = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피")).id
        }

    @Test
    fun `S2 시즌 상품의 활성화와 단종을 함께 예약하면 각자의 날짜에 적용된다`() =
        runTest {
            val product = registerProduct("가을 라떼")

            // 1·1b단계: 활성화와 단종은 서로 다른 필드라 함께 대기한다.
            val activation = register(product, ProductFieldValue.Activation, LocalDate.of(2026, 10, 1))
            val discontinuation = register(product, ProductFieldValue.Discontinuation, LocalDate.of(2026, 10, 31))

            // 2단계: 조회하면 대기 중인 예약이 필드 이름·적용 날짜와 함께 보인다.
            assertEquals(
                listOf("activation" to LocalDate.of(2026, 10, 1), "discontinuation" to LocalDate.of(2026, 10, 31)),
                scheduledChangeService.findPendingForProduct(product).map { it.fieldName to it.effectiveDate },
            )

            // 3단계: 10/1 00시에 활성화만 적용된다.
            clock.moveTo(Instant.parse("2026-09-30T15:00:00Z"))
            assertEquals(1, batch.applyDue().applied)
            assertEquals(ProductStatus.ACTIVE, statusOf(product))
            assertEquals(ScheduleStatus.APPLIED, statusOf(activation))
            assertEquals(ScheduleStatus.PENDING, statusOf(discontinuation))

            // 3단계: 10/31 00시에 단종이 적용된다.
            clock.moveTo(Instant.parse("2026-10-30T15:00:00Z"))
            assertEquals(1, batch.applyDue().applied)
            assertEquals(ProductStatus.DISCONTINUED, statusOf(product))
            assertEquals(ScheduleStatus.APPLIED, statusOf(discontinuation))
        }

    @Test
    fun `S2 같은 필드에 다시 예약하면 기존 예약이 취소되고 마지막 예약만 적용된다`() =
        runTest {
            val product = registerProduct("아메리카노")

            // 1a단계: 같은 대상·필드의 대기 예약은 항상 최대 1건이다.
            val first = register(product, ProductFieldValue.BasePrice(Money(5000)), LocalDate.of(2026, 10, 1))
            val second = register(product, ProductFieldValue.BasePrice(Money(5200)), LocalDate.of(2026, 10, 1))
            assertEquals(ScheduleStatus.CANCELLED, statusOf(first))

            clock.moveTo(Instant.parse("2026-09-30T15:00:00Z"))
            assertEquals(1, batch.applyDue().applied)

            assertEquals(Money(5200), assertNotNull(tx { productRepository.findById(product) }).basePrice)
            assertEquals(ScheduleStatus.APPLIED, statusOf(second))
        }

    @Test
    fun `S2 취소한 예약은 적용되지 않고, 배치가 처리한 뒤에는 취소할 수 없다`() =
        runTest {
            val product = registerProduct("아메리카노")

            // 2a단계: 대기 중에 취소하면 적용되지 않는다.
            val cancelled = register(product, ProductFieldValue.Name("겨울 아메리카노"), LocalDate.of(2026, 10, 1))
            scheduledChangeService.cancel(CancelScheduledChangeCommand.forProduct(product, "name"))
            assertEquals(ScheduleStatus.CANCELLED, statusOf(cancelled))

            clock.moveTo(Instant.parse("2026-09-30T15:00:00Z"))
            assertEquals(0, batch.applyDue().applied)
            assertEquals("아메리카노", tx { productRepository.findById(product) }?.name)

            // 2c단계: 배치가 먼저 적용한 예약은 더는 취소할 수 없다.
            val applied = register(product, ProductFieldValue.Name("봄 아메리카노"), LocalDate.of(2026, 10, 5))
            clock.moveTo(Instant.parse("2026-10-04T15:00:00Z"))
            assertEquals(1, batch.applyDue().applied)
            assertFailsWith<NoPendingScheduleException> {
                scheduledChangeService.cancel(CancelScheduledChangeCommand.forProduct(product, "name"))
            }
            assertEquals(ScheduleStatus.APPLIED, statusOf(applied))
            assertEquals("봄 아메리카노", tx { productRepository.findById(product) }?.name)
        }

    @Test
    fun `S2 옵션 목록 스냅샷은 그 뒤의 즉시 변경과 무관하게 등록 시점 그대로 적용된다`() =
        runTest {
            val sizeGroup =
                optionGroupService.register(
                    RegisterOptionGroupCommand(
                        name = "사이즈",
                        selectionType = SelectionType.SINGLE,
                        required = true,
                        options = listOf(Option(OptionKey("TALL"), "톨", Money(0))),
                    ),
                )

            // 1단계: 등록 시점의 옵션 목록 전체를 스냅샷으로 담는다(S5 대체 흐름 1a).
            val snapshot =
                listOf(
                    Option(OptionKey("TALL"), "톨", Money(0)),
                    Option(OptionKey("GRANDE"), "그란데", Money(500)),
                )
            val scheduled =
                scheduledChangeService.register(
                    RegisterScheduledChangeCommand.forOptionGroup(
                        sizeGroup.id,
                        OptionGroupFieldValue.Options(snapshot),
                        LocalDate.of(2026, 10, 1),
                    ),
                )

            // 예약이 대기하는 동안의 즉시 변경은 스냅샷에 영향을 주지 않는다.
            optionGroupService.replaceOptions(sizeGroup.id, listOf(Option(OptionKey("SHORT"), "숏", Money(0))))

            clock.moveTo(Instant.parse("2026-09-30T15:00:00Z"))
            assertEquals(1, batch.applyDue().applied)

            assertEquals(snapshot, optionGroupService.get(sizeGroup.id).options)
            assertEquals(ScheduleStatus.APPLIED, statusOf(scheduled))
        }

    private suspend fun registerProduct(name: String): ProductId =
        productService
            .register(
                RegisterProductCommand(
                    name = name,
                    categoryId = coffee,
                    basePrice = Money(4500),
                    tracksInventory = false,
                ),
            ).id

    private suspend fun register(
        productId: ProductId,
        newValue: ProductFieldValue,
        effectiveDate: LocalDate,
    ): ScheduledChange = scheduledChangeService.register(RegisterScheduledChangeCommand.forProduct(productId, newValue, effectiveDate))

    private suspend fun statusOf(scheduledChange: ScheduledChange): ScheduleStatus? =
        tx { scheduledChangeRepository.findById(scheduledChange.id) }?.status

    private suspend fun statusOf(productId: ProductId): ProductStatus? = tx { productRepository.findById(productId) }?.status
}
