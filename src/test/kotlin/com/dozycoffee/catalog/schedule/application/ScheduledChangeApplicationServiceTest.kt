package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.ValidateStoreExistsPort
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotAssignableException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.exception.DuplicateOptionKeyException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.product.exception.DuplicateOptionGroupLinkException
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.product.exception.TargetStoreNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.exception.TagNotFoundException
import com.dozycoffee.catalog.schedule.application.command.CancelScheduledChangeCommand
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.exception.InvalidEffectiveDateException
import com.dozycoffee.catalog.schedule.domain.exception.NoPendingScheduleException
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.FakeStoreExistenceValidator
import com.dozycoffee.catalog.support.MutableClock
import com.dozycoffee.catalog.support.MutableClockConfiguration
import kotlinx.coroutines.reactor.awaitSingle
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

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var storeValidator: ValidateStoreExistsPort

    private var beverage = CategoryId(0)
    private var coffee = CategoryId(0)
    private var product = ProductId(0)
    private var sizeGroup = OptionGroupId(0)
    private var shotGroup = OptionGroupId(0)
    private val tomorrow = LocalDate.of(2026, 9, 22)

    // 검증 대상이 아닌 준비 데이터는 유스케이스를 거치지 않고 직접 넣는다(docs/architecture/testing.md).
    @BeforeEach
    fun setUp() =
        runTest {
            clock.reset()
            fakeStoreValidator().reset()
            execute("INSERT INTO categories (name) VALUES ('음료')")
            beverage = CategoryId(count("SELECT max(id) FROM categories"))
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', ${beverage.value})")
            coffee = CategoryId(count("SELECT max(id) FROM categories"))
            product = insertProduct("아메리카노")
            sizeGroup = insertOptionGroup("사이즈", "TALL", "GRANDE")
            shotGroup = insertOptionGroup("샷 추가", "EXTRA")
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
                // 단종 예약을 등록하는 시점에 상품은 아직 DRAFT지만, 상태 전이는 적용 시점에만 판단하므로 거부하지 않는다.
                assertEquals(ProductStatus.DRAFT, tx { productRepository.findById(product) }?.status)
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
                    setOf("optionOverrides:${sizeGroup.value}", "optionOverrides:${shotGroup.value}"),
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
    @DisplayName("S2 기본 흐름: 태그 이름으로 등록")
    inner class TagNames {
        @Test
        fun `태그 이름으로 등록하면 없는 태그는 만들고 기존 태그는 재사용해 ID로 저장한다`() =
            runTest {
                execute("INSERT INTO tags (name) VALUES ('시즌한정')")
                val seasonal = TagId(count("SELECT max(id) FROM tags"))

                val registered =
                    service.register(RegisterScheduledChangeCommand.forProductTags(product, listOf("시즌한정", "신메뉴"), tomorrow))

                assertEquals(listOf("시즌한정", "신메뉴"), tagNames())
                val newMenu = TagId(count("SELECT id FROM tags WHERE name = '신메뉴'"))
                val found = assertNotNull(tx { scheduledChangeRepository.findById(registered.id) })
                assertEquals("tags", found.fieldName)
                assertEquals(ProductFieldValue.Tags(setOf(seasonal, newMenu)), found.newValue)
            }

        @Test
        fun `거부된 태그 예약은 새 태그를 남기지 않는다`() =
            runTest {
                val pending = service.register(RegisterScheduledChangeCommand.forProductTags(product, listOf("시즌한정"), tomorrow))

                assertRejectedKeeping<InvalidEffectiveDateException>(pending) {
                    service.register(
                        RegisterScheduledChangeCommand.forProductTags(product, listOf("신메뉴"), LocalDate.of(2026, 9, 21)),
                    )
                }
                assertRejectedKeeping<ProductNotFoundException>(pending) {
                    service.register(RegisterScheduledChangeCommand.forProductTags(ProductId(999), listOf("베스트"), tomorrow))
                }
                assertEquals(listOf("시즌한정"), tagNames())
            }
    }

    // 등록할 때 값 자체와 참조 대상의 존재를 확인한다(docs/api/schedule.md 검증 시점). 거부된 요청은 같은 필드에
    // 대기 중이던 예약을 취소하지 않고, 새 태그도 만들지 않는다.
    @Nested
    @DisplayName("S2 예외 흐름 1d: 등록 시점 검증")
    inner class RegistrationValidation {
        @Test
        fun `없는 상품이나 옵션 그룹을 대상으로 하면 거부한다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.Name("아메리카노"), tomorrow))

                assertRejectedKeeping<ProductNotFoundException>(pending) {
                    service.register(
                        RegisterScheduledChangeCommand.forProduct(ProductId(999), ProductFieldValue.Name("라떼"), tomorrow),
                    )
                }
                assertRejectedKeeping<OptionGroupNotFoundException>(pending) {
                    service.register(
                        RegisterScheduledChangeCommand.forOptionGroup(OptionGroupId(999), options("TALL"), tomorrow),
                    )
                }
            }

        @Test
        fun `없는 카테고리나 대분류로는 카테고리를 예약할 수 없다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.Category(coffee), tomorrow))

                assertRejectedKeeping<CategoryNotFoundException>(pending) {
                    service.register(command(ProductFieldValue.Category(CategoryId(999)), tomorrow))
                }
                assertRejectedKeeping<CategoryNotAssignableException>(pending) {
                    service.register(command(ProductFieldValue.Category(beverage), tomorrow))
                }
            }

        @Test
        fun `없는 태그 ID는 거부한다`() =
            runTest {
                val pending = service.register(RegisterScheduledChangeCommand.forProductTags(product, listOf("시즌한정"), tomorrow))

                assertRejectedKeeping<TagNotFoundException>(pending) {
                    service.register(command(ProductFieldValue.Tags(setOf(TagId(999))), tomorrow))
                }
            }

        @Test
        fun `없는 상품 그룹은 거부한다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.Groups(emptySet()), tomorrow))

                assertRejectedKeeping<ProductGroupNotFoundException>(pending) {
                    service.register(command(ProductFieldValue.Groups(setOf(ProductGroupId(999))), tomorrow))
                }
            }

        @Test
        fun `판매 범위의 대상 매장이 없으면 거부한다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.Scope(StoreScope.All), tomorrow))
                fakeStoreValidator().markMissing(StoreId(404))

                assertRejectedKeeping<TargetStoreNotFoundException>(pending) {
                    service.register(command(ProductFieldValue.Scope(StoreScope.Limited(setOf(StoreId(1), StoreId(404)))), tomorrow))
                }
                assertEquals(listOf(setOf(StoreId(1), StoreId(404))), fakeStoreValidator().checked)
            }

        @Test
        fun `옵션 그룹 연결에 없는 옵션 그룹이 있거나 같은 옵션 그룹이 두 번 있으면 거부한다`() =
            runTest {
                val pending = service.register(command(ProductFieldValue.OptionGroupLinks(listOf(sizeGroup)), tomorrow))

                assertRejectedKeeping<OptionGroupNotFoundException>(pending) {
                    service.register(command(ProductFieldValue.OptionGroupLinks(listOf(sizeGroup, OptionGroupId(999))), tomorrow))
                }
                assertRejectedKeeping<DuplicateOptionGroupLinkException>(pending) {
                    service.register(command(ProductFieldValue.OptionGroupLinks(listOf(sizeGroup, shotGroup, sizeGroup)), tomorrow))
                }
            }

        @Test
        fun `없는 옵션 그룹에 대한 예외는 거부한다`() =
            runTest {
                val pending =
                    service.register(command(optionOverrides(sizeGroup, OptionOverride.Exclude(OptionKey("TALL"))), tomorrow))

                assertRejectedKeeping<OptionGroupNotFoundException>(pending) {
                    service.register(
                        command(optionOverrides(OptionGroupId(999), OptionOverride.Exclude(OptionKey("TALL"))), tomorrow),
                    )
                }
            }

        @Test
        fun `옵션이 0개이거나 옵션 키가 겹치는 옵션 목록은 거부한다`() =
            runTest {
                val pending = service.register(RegisterScheduledChangeCommand.forOptionGroup(sizeGroup, options("TALL"), tomorrow))

                assertRejectedKeeping<EmptyOptionGroupException>(pending) {
                    service.register(RegisterScheduledChangeCommand.forOptionGroup(sizeGroup, options(), tomorrow))
                }
                assertRejectedKeeping<DuplicateOptionKeyException>(pending) {
                    service.register(RegisterScheduledChangeCommand.forOptionGroup(sizeGroup, options("TALL", "TALL"), tomorrow))
                }
            }

        // 상태에 달린 규칙은 적용 전에 다른 예약이나 즉시 변경으로 바뀔 수 있어 적용 시점에만 판단한다.
        @Test
        fun `연결되지 않은 옵션 그룹이나 옵션 그룹에 없는 옵션 키의 예외는 등록할 때 거부하지 않는다`() =
            runTest {
                service.register(
                    command(
                        optionOverrides(sizeGroup, OptionOverride.Exclude(OptionKey("TALL")), OptionOverride.Exclude(OptionKey("VENTI"))),
                        tomorrow,
                    ),
                )

                assertEquals(listOf("optionOverrides:${sizeGroup.value}"), pendingForProduct().map { it.fieldName })
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
                        insertProduct("라떼"),
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

    // 거부된 뒤에도 대기 중이던 예약이 그대로이고, 다른 예약이나 태그가 새로 생기지 않았는지 확인한다.
    private suspend inline fun <reified T : DomainException> assertRejectedKeeping(
        pending: ScheduledChange,
        crossinline register: suspend () -> Unit,
    ) {
        val schedulesBefore = count("SELECT count(*) FROM scheduled_changes")
        val tagsBefore = tagNames()

        assertFailsWith<T> { register() }

        val found = assertNotNull(tx { scheduledChangeRepository.findById(pending.id) })
        assertEquals(ScheduleStatus.PENDING, found.status)
        assertEquals(pending.newValue, found.newValue)
        assertEquals(schedulesBefore, count("SELECT count(*) FROM scheduled_changes"))
        assertEquals(tagsBefore, tagNames())
    }

    private suspend fun tagNames(): List<String> =
        databaseClient
            .sql("SELECT name FROM tags ORDER BY id")
            .map { row -> row.get("name", String::class.java)!! }
            .all()
            .collectList()
            .awaitSingle()

    private fun options(vararg optionKeys: String) = OptionGroupFieldValue.Options(optionKeys.map { Option(OptionKey(it), it, Money(0)) })

    private fun fakeStoreValidator() = storeValidator as FakeStoreExistenceValidator

    private suspend fun insertProduct(name: String): ProductId {
        execute(
            "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) " +
                "VALUES ('$name', ${coffee.value}, 4500, false, 'DRAFT')",
        )
        return ProductId(count("SELECT max(id) FROM products"))
    }

    private suspend fun insertOptionGroup(
        name: String,
        vararg optionKeys: String,
    ): OptionGroupId {
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('$name', 'SINGLE', true)")
        val id = count("SELECT max(id) FROM option_groups")
        optionKeys.forEachIndexed { index, key ->
            execute(
                "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                    "VALUES ($id, '$key', '$key', 0, $index)",
            )
        }
        return OptionGroupId(id)
    }
}
