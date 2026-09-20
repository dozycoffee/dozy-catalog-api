package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupRepository
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.MutableClock
import com.dozycoffee.catalog.support.MutableClockConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// 오늘은 MutableClock 기준 2026-09-21(업무 시간대 Asia/Seoul)이라 예약은 9/22 이후로 등록한다.
@Import(MutableClockConfiguration::class)
@DisplayName("S2. 예약 적용 배치 (요구사항 1.4)")
class ScheduledChangeApplicationBatchTest : ApplicationTest() {
    @Autowired
    private lateinit var batch: ScheduledChangeApplicationBatch

    @Autowired
    private lateinit var scheduledChangeService: ScheduledChangeApplicationService

    @Autowired
    private lateinit var scheduledChangeRepository: ScheduledChangeRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var optionGroupRepository: OptionGroupRepository

    @Autowired
    private lateinit var clock: MutableClock

    private var beverage: CategoryId = CategoryId(0)
    private var coffee: CategoryId = CategoryId(0)

    @BeforeEach
    fun setUp() =
        runTest {
            clock.reset()
            execute("INSERT INTO categories (name) VALUES ('음료')")
            beverage = CategoryId(count("SELECT max(id) FROM categories"))
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', ${beverage.value})")
            coffee = CategoryId(count("SELECT max(id) FROM categories"))
        }

    @Nested
    @DisplayName("S2 기본 흐름: 적용 대상")
    inner class Due {
        @Test
        fun `적용 시각이 지난 대기 예약만 적용하고 적용완료로 기록한다`() =
            runTest {
                val product = insertProduct()
                val today = register(product, ProductFieldValue.Name("따뜻한 아메리카노"), OCTOBER_1)
                val later = register(product, ProductFieldValue.BasePrice(Money(5200)), LocalDate.of(2026, 10, 5))

                val result = runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(ScheduledChangeBatchResult(applied = 1, failed = 0, skipped = 0), result)
                assertEquals(ScheduleStatus.APPLIED, statusOf(today))
                assertEquals(ScheduleStatus.PENDING, statusOf(later))
                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals("따뜻한 아메리카노", found.name)
                assertEquals(Money(4500), found.basePrice)
            }

        @Test
        fun `00시를 놓친 예약도 다음 실행에서 함께 적용한다`() =
            runTest {
                val product = insertProduct()
                val missed = register(product, ProductFieldValue.Name("놓친 예약"), OCTOBER_1)

                // 10/1 00시에 배치가 돌지 못하고 10/3에야 실행된 상황
                val result = runBatchAt(Instant.parse("2026-10-02T15:00:00Z"))

                assertEquals(1, result.applied)
                assertEquals(ScheduleStatus.APPLIED, statusOf(missed))
                assertEquals("놓친 예약", tx { productRepository.findById(product) }?.name)
            }

        @Test
        fun `건수 상한만큼만 처리하고 나머지는 다음 실행에서 적용한다`() =
            runTest {
                val products = List(3) { insertProduct(name = "상품 $it") }
                products.forEach { register(it, ProductFieldValue.Name("바뀐 이름"), OCTOBER_1) }

                val first = runBatchAt(OCTOBER_1_MIDNIGHT, limit = 2)
                assertEquals(2, first.applied)
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes WHERE status = 'PENDING'"))

                val second = runBatchAt(OCTOBER_1_MIDNIGHT, limit = 2)
                assertEquals(1, second.applied)
                assertEquals(0, count("SELECT count(*) FROM scheduled_changes WHERE status = 'PENDING'"))
            }
    }

    @Nested
    @DisplayName("S2 기본 흐름: 값 타입별 적용 경로")
    inner class FieldValues {
        @Test
        fun `상품명`() =
            runTest {
                val product = insertProduct()
                register(product, ProductFieldValue.Name("라떼"), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals("라떼", tx { productRepository.findById(product) }?.name)
            }

        @Test
        fun `카테고리는 적용 시점에 소분류인지 다시 확인한다`() =
            runTest {
                val product = insertProduct()
                execute("INSERT INTO categories (name, parent_category_id) VALUES ('디카페인', ${beverage.value})")
                val decaf = CategoryId(count("SELECT max(id) FROM categories"))
                register(product, ProductFieldValue.Category(decaf), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(decaf, tx { productRepository.findById(product) }?.categoryId)
            }

        @Test
        fun `설명과 이미지는 비울 수도 있다`() =
            runTest {
                val product = insertProduct()
                register(product, ProductFieldValue.Description("깊고 진한 맛"), OCTOBER_1)
                register(product, ProductFieldValue.Image(null), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals("깊고 진한 맛", found.description)
                assertNull(found.imageUrl)
            }

        @Test
        fun `기준가`() =
            runTest {
                val product = insertProduct()
                register(product, ProductFieldValue.BasePrice(Money(5200)), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(Money(5200), tx { productRepository.findById(product) }?.basePrice)
            }

        @Test
        fun `태그와 그룹`() =
            runTest {
                val product = insertProduct()
                execute("INSERT INTO tags (name) VALUES ('시즌한정')")
                val tag = TagId(count("SELECT max(id) FROM tags"))
                execute("INSERT INTO product_groups (name) VALUES ('가을 시즌')")
                val group = ProductGroupId(count("SELECT max(id) FROM product_groups"))
                register(product, ProductFieldValue.Tags(setOf(tag)), OCTOBER_1)
                register(product, ProductFieldValue.Groups(setOf(group)), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals(setOf(tag), found.tagIds)
                assertEquals(setOf(group), found.groupIds)
            }

        @Test
        fun `판매 범위를 바꾸면 대상에서 빠진 매장의 진열 설정도 정리된다`() =
            runTest {
                val product = insertProduct(status = "ACTIVE")
                execute(
                    "INSERT INTO store_display_settings (store_id, product_id, visibility) " +
                        "VALUES (2, ${product.value}, 'HIDDEN')",
                )
                register(product, ProductFieldValue.Scope(StoreScope.Limited(setOf(StoreId(1)))), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals(StoreScope.Limited(setOf(StoreId(1))), found.storeScope)
                assertEquals(0, count("SELECT count(*) FROM store_display_settings"))
            }

        @Test
        fun `활성화와 단종`() =
            runTest {
                val product = insertProduct()
                register(product, ProductFieldValue.Activation, OCTOBER_1)
                register(product, ProductFieldValue.Discontinuation, LocalDate.of(2026, 10, 31))

                runBatchAt(OCTOBER_1_MIDNIGHT)
                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product) }?.status)

                runBatchAt(Instant.parse("2026-10-30T15:00:00Z"))
                assertEquals(ProductStatus.DISCONTINUED, tx { productRepository.findById(product) }?.status)
            }

        @Test
        fun `옵션 그룹 연결은 목록 순서대로 맞추고 빠진 연결은 예외와 함께 해제한다`() =
            runTest {
                val product = insertProduct()
                val size = insertOptionGroup("TALL", "GRANDE", name = "사이즈")
                val shot = insertOptionGroup("EXTRA", name = "샷 추가")
                link(product, size, displayOrder = 0)
                link(product, shot, displayOrder = 1)
                excludeOption(product, size, "TALL")
                register(product, ProductFieldValue.OptionGroupLinks(listOf(shot)), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals(listOf(shot), found.optionGroupLinks.map { it.id })
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `옵션 예외는 그 옵션 그룹의 예외 전체를 스냅샷으로 교체한다`() =
            runTest {
                val product = insertProduct()
                val size = insertOptionGroup("TALL", "GRANDE")
                link(product, size)
                excludeOption(product, size, "TALL")
                register(
                    product,
                    ProductFieldValue.OptionOverrides(size, listOf(OptionOverride.Price(OptionKey("GRANDE"), Money(300)))),
                    OCTOBER_1,
                )

                runBatchAt(OCTOBER_1_MIDNIGHT)

                val found = assertNotNull(tx { productRepository.findById(product) })
                assertEquals(
                    listOf(OptionOverride.Price(OptionKey("GRANDE"), Money(300))),
                    found.optionGroupLinks.single().overrides,
                )
            }

        @Test
        fun `옵션 목록은 등록 시점 스냅샷으로 교체된다`() =
            runTest {
                val size = insertOptionGroup("TALL", "GRANDE")
                val snapshot =
                    listOf(
                        Option(OptionKey("GRANDE"), "그란데", Money(500)),
                        Option(OptionKey("VENTI"), "벤티", Money(1000)),
                    )
                registerForOptionGroup(size, OptionGroupFieldValue.Options(snapshot), OCTOBER_1)

                runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(snapshot, tx { optionGroupRepository.findById(size) }?.options)
            }
    }

    @Nested
    @DisplayName("S2 예외 흐름: 실패 기록")
    inner class Failure {
        @Test
        fun `옵션 목록 스냅샷으로 어떤 상품의 선택 가능 옵션이 0개가 되면 실패로 기록하고 옵션 그룹은 그대로다`() =
            runTest {
                val product = insertProduct()
                val size = insertOptionGroup("TALL", "GRANDE")
                link(product, size)
                excludeOption(product, size, "TALL")
                val schedule =
                    registerForOptionGroup(
                        size,
                        OptionGroupFieldValue.Options(listOf(Option(OptionKey("TALL"), "톨", Money(0)))),
                        OCTOBER_1,
                    )

                val result = runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(ScheduledChangeBatchResult(applied = 0, failed = 1, skipped = 0), result)
                assertEquals(ScheduleStatus.FAILED, statusOf(schedule))
                assertEquals(
                    listOf(OptionKey("TALL"), OptionKey("GRANDE")),
                    tx { optionGroupRepository.findById(size) }?.options?.map { it.optionKey },
                )
            }

        @Test
        fun `단종 날짜가 활성화보다 앞서면 단종 예약만 실패하고 활성화는 제 날짜에 적용된다`() =
            runTest {
                val product = insertProduct(status = "DRAFT")
                val discontinuation = register(product, ProductFieldValue.Discontinuation, OCTOBER_1)
                val activation = register(product, ProductFieldValue.Activation, LocalDate.of(2026, 10, 5))

                val first = runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(ScheduledChangeBatchResult(applied = 0, failed = 1, skipped = 0), first)
                assertEquals(ScheduleStatus.FAILED, statusOf(discontinuation))
                assertEquals(ProductStatus.DRAFT, tx { productRepository.findById(product) }?.status)

                val second = runBatchAt(Instant.parse("2026-10-04T15:00:00Z"))

                assertEquals(1, second.applied)
                assertEquals(ScheduleStatus.APPLIED, statusOf(activation))
                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product) }?.status)
            }

        @Test
        fun `예외 예약 적용 시점에 옵션 키가 사라졌으면 실패로 기록하고 예외는 남기지 않는다`() =
            runTest {
                val product = insertProduct()
                val size = insertOptionGroup("TALL", "GRANDE")
                link(product, size)
                val schedule =
                    register(
                        product,
                        ProductFieldValue.OptionOverrides(size, listOf(OptionOverride.Price(OptionKey("GRANDE"), Money(300)))),
                        OCTOBER_1,
                    )
                // 예약이 대기하는 동안 옵션 그룹에서 GRANDE가 사라졌다.
                execute("DELETE FROM options WHERE option_key = 'GRANDE'")

                val result = runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(1, result.failed)
                assertEquals(ScheduleStatus.FAILED, statusOf(schedule))
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `한 건이 실패해도 나머지 예약은 적용된다`() =
            runTest {
                val draft = insertProduct(name = "판매 대기 상품")
                val other = insertProduct(name = "다른 상품")
                val failing = register(draft, ProductFieldValue.Discontinuation, OCTOBER_1)
                val succeeding = register(other, ProductFieldValue.Name("바뀐 이름"), OCTOBER_1)

                val result = runBatchAt(OCTOBER_1_MIDNIGHT)

                assertEquals(ScheduledChangeBatchResult(applied = 1, failed = 1, skipped = 0), result)
                assertEquals(ScheduleStatus.FAILED, statusOf(failing))
                assertEquals(ScheduleStatus.APPLIED, statusOf(succeeding))
                assertEquals("바뀐 이름", tx { productRepository.findById(other) }?.name)
                assertEquals(ProductStatus.DRAFT, tx { productRepository.findById(draft) }?.status)
            }
    }

    @Nested
    @DisplayName("S2 기본 흐름: 여러 워커")
    inner class Workers {
        @Test
        fun `두 워커가 동시에 돌아도 같은 예약을 두 번 적용하지 않는다`() =
            runTest {
                val product = insertProduct()
                val schedule = register(product, ProductFieldValue.Activation, OCTOBER_1)
                clock.moveTo(OCTOBER_1_MIDNIGHT)

                val results =
                    listOf(
                        async { batch.applyDue() },
                        async { batch.applyDue() },
                    ).awaitAll()

                // 한쪽만 적용하고 다른 쪽은 기다리지 않고 건너뛴다. 두 번 적용됐다면 두 번째 활성화가
                // 거부되어 실패로 기록됐을 것이다.
                assertEquals(1, results.sumOf { it.applied })
                assertEquals(0, results.sumOf { it.failed })
                assertEquals(ScheduleStatus.APPLIED, statusOf(schedule))
                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product) }?.status)
            }
    }

    private suspend fun runBatchAt(
        now: Instant,
        limit: Int = 100,
    ): ScheduledChangeBatchResult {
        clock.moveTo(now)
        return batch.applyDue(limit)
    }

    private suspend fun statusOf(scheduledChange: ScheduledChange): ScheduleStatus? =
        tx { scheduledChangeRepository.findById(scheduledChange.id) }?.status

    private suspend fun register(
        productId: ProductId,
        newValue: ProductFieldValue,
        effectiveDate: LocalDate,
    ): ScheduledChange = scheduledChangeService.register(RegisterScheduledChangeCommand.forProduct(productId, newValue, effectiveDate))

    private suspend fun registerForOptionGroup(
        optionGroupId: OptionGroupId,
        newValue: OptionGroupFieldValue,
        effectiveDate: LocalDate,
    ): ScheduledChange =
        scheduledChangeService.register(
            RegisterScheduledChangeCommand.forOptionGroup(optionGroupId, newValue, effectiveDate),
        )

    // 검증 대상이 아닌 준비 데이터는 유스케이스를 거치지 않고 직접 넣는다(docs/architecture/testing.md).
    private suspend fun insertProduct(
        name: String = "아메리카노",
        status: String = "DRAFT",
    ): ProductId {
        execute(
            "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) " +
                "VALUES ('$name', ${coffee.value}, 4500, false, '$status')",
        )
        return ProductId(count("SELECT max(id) FROM products"))
    }

    private suspend fun insertOptionGroup(
        vararg optionKeys: String,
        name: String = "사이즈",
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

    private suspend fun link(
        productId: ProductId,
        optionGroupId: OptionGroupId,
        displayOrder: Int = 0,
    ) = execute(
        "INSERT INTO product_option_groups (product_id, option_group_id, display_order) " +
            "VALUES (${productId.value}, ${optionGroupId.value}, $displayOrder)",
    )

    private suspend fun excludeOption(
        productId: ProductId,
        optionGroupId: OptionGroupId,
        optionKey: String,
    ) = execute(
        "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type) " +
            "VALUES (${productId.value}, ${optionGroupId.value}, '$optionKey', 'EXCLUDE')",
    )

    private companion object {
        val OCTOBER_1: LocalDate = LocalDate.of(2026, 10, 1)

        // 서울 2026-10-01 00:00
        val OCTOBER_1_MIDNIGHT: Instant = Instant.parse("2026-09-30T15:00:00Z")
    }
}
