package com.dozycoffee.catalog.schedule.infrastructure

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.schedule.application.OptionGroupFieldValue
import com.dozycoffee.catalog.schedule.application.ProductFieldValue
import com.dozycoffee.catalog.schedule.application.ScheduledFieldValue
import com.dozycoffee.catalog.schedule.domain.ScheduleStatus
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.ScheduledValue
import com.dozycoffee.catalog.schedule.domain.TargetKind
import com.dozycoffee.catalog.schedule.domain.exception.ScheduleAlreadyProcessedException
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("ExposedScheduledChangeRepository")
class ExposedScheduledChangeRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: ScheduledChangeRepository

    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 9, 19)

    @Nested
    @DisplayName("저장과 조회")
    inner class RoundTrip {
        @Test
        fun `적용일과 적용 시각을 함께 저장한다`() =
            runTest {
                val saved = register(effectiveDate = LocalDate.of(2026, 10, 1))

                val found = assertNotNull(tx.inTransaction { repository.findById(saved.id) })
                assertEquals(LocalDate.of(2026, 10, 1), found.effectiveDate)
                assertEquals(Instant.parse("2026-09-30T15:00:00Z"), found.effectiveAt)
                assertEquals(ScheduleStatus.PENDING, found.status)
                assertEquals(TargetKind.PRODUCT, found.targetKind)
                assertEquals("basePrice", found.fieldName)
            }

        @Test
        fun `예약 값은 타입 구분자와 값을 담은 JSON 객체로 저장된다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(5000)))
                register(targetId = 2, newValue = overrides(optionGroupId = 12, OptionOverride.Exclude(OptionKey("L"))))

                assertEquals(
                    1,
                    count(
                        "SELECT count(*) FROM scheduled_changes WHERE jsonb_typeof(new_value) = 'object' " +
                            "AND new_value->>'type' = 'basePrice' AND (new_value->>'value')::bigint = 5000",
                    ),
                )
                assertEquals(
                    1,
                    count(
                        "SELECT count(*) FROM scheduled_changes WHERE field_name = 'optionOverrides:12' AND new_value @> " +
                            "'{\"type\": \"optionOverrides\", \"value\": {\"optionGroupId\": 12, " +
                            "\"overrides\": [{\"optionKey\": \"L\", \"type\": \"EXCLUDE\"}]}}'",
                    ),
                )
            }

        @Test
        fun `모든 필드의 예약 값이 타입 그대로 복원된다`() =
            runTest {
                val values = allFieldValues()
                assertEquals(leafTypesOf(ScheduledFieldValue::class), values.map { it::class }.toSet(), "왕복 검사에 빠진 값 타입이 있습니다")

                values.forEachIndexed { index, value ->
                    val saved = register(targetId = index + 1L, newValue = value)

                    val found = assertNotNull(tx.inTransaction { repository.findById(saved.id) })
                    assertEquals(value, found.newValue)
                    assertEquals(value.targetKind, found.targetKind)
                    assertEquals(value.fieldName, found.fieldName)
                }
            }

        @Test
        fun `대상의 대기 예약을 모두 가져온다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.Name("아이스 아메리카노"))
                register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(5000)))
                val cancelled = register(targetId = 1, newValue = ProductFieldValue.Description("시즌 한정"))
                tx.inTransaction { repository.save(cancelled.also { it.cancel() }) }
                register(targetId = 2, newValue = ProductFieldValue.Name("라떼"))
                register(targetId = 1, newValue = OptionGroupFieldValue.Options(listOf(option("S"))))

                val pending = tx.inTransaction { repository.findAllPendingByTarget(1, TargetKind.PRODUCT) }

                assertEquals(listOf("name", "basePrice"), pending.map { it.fieldName })
            }
    }

    @Nested
    @DisplayName("대상·필드당 대기 예약 1건")
    inner class OnePendingPerField {
        @Test
        fun `같은 대상·필드의 대기 예약이 두 개가 되면 DB가 거부한다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(5000)))

                assertFails { register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(6000))) }
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes"))
            }

        @Test
        fun `기존 대기 예약을 잠가 취소하면 같은 필드에 새 예약을 등록할 수 있다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(5000)))

                tx.inTransaction {
                    val pending =
                        assertNotNull(repository.findPendingByTargetForUpdate(1, TargetKind.PRODUCT, "basePrice"))
                    pending.cancel()
                    repository.save(pending)
                    repository.insert(newSchedule(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(6000))))
                }

                val pending = tx.inTransaction { repository.findPendingByTargetForUpdate(1, TargetKind.PRODUCT, "basePrice") }
                assertEquals(ProductFieldValue.BasePrice(Money(6000)), pending?.newValue)
                assertEquals(1, count("SELECT count(*) FROM scheduled_changes WHERE status = 'CANCELLED'"))
            }

        @Test
        fun `적용이 끝난 예약은 대기 예약으로 조회되지 않는다`() =
            runTest {
                val saved = register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(5000)))
                tx.inTransaction { repository.save(saved.also { it.apply() }) }

                assertNull(tx.inTransaction { repository.findPendingByTargetForUpdate(1, TargetKind.PRODUCT, "basePrice") })
                register(targetId = 1, newValue = ProductFieldValue.BasePrice(Money(6000)))
            }

        @Test
        fun `같은 상품에 활성화 예약과 단종 예약이 함께 대기할 수 있다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.Activation, effectiveDate = LocalDate.of(2026, 10, 1))
                register(targetId = 1, newValue = ProductFieldValue.Discontinuation, effectiveDate = LocalDate.of(2026, 10, 31))

                val pending = tx.inTransaction { repository.findAllPendingByTarget(1, TargetKind.PRODUCT) }

                assertEquals(listOf(ProductFieldValue.Activation, ProductFieldValue.Discontinuation), pending.map { it.newValue })
            }

        @Test
        fun `같은 상품이라도 옵션 그룹이 다르면 예외 예약이 각각 대기할 수 있다`() =
            runTest {
                register(targetId = 1, newValue = overrides(optionGroupId = 12, OptionOverride.Exclude(OptionKey("L"))))
                register(targetId = 1, newValue = overrides(optionGroupId = 13, OptionOverride.Exclude(OptionKey("HOT"))))

                val pending = tx.inTransaction { repository.findAllPendingByTarget(1, TargetKind.PRODUCT) }

                assertEquals(listOf("optionOverrides:12", "optionOverrides:13"), pending.map { it.fieldName })
                assertFails { register(targetId = 1, newValue = overrides(optionGroupId = 12)) }
            }

        @Test
        fun `상품과 옵션 그룹은 ID가 같아도 다른 대상이다`() =
            runTest {
                register(targetId = 1, newValue = ProductFieldValue.Name("라떼"))
                register(targetId = 1, newValue = OptionGroupFieldValue.Options(listOf(option("S"))))

                assertEquals(1, tx.inTransaction { repository.findAllPendingByTarget(1, TargetKind.OPTION_GROUP) }.size)
            }
    }

    @Nested
    @DisplayName("스키마 제약")
    inner class SchemaConstraints {
        @Test
        fun `상품-옵션 그룹 연결은 예약 대상 종류로 저장할 수 없다`() =
            runTest {
                // 연결의 예약은 상품을 대상으로 하고 필드 이름으로 구분한다(V3, docs/adr/0014).
                assertFails {
                    execute(
                        "INSERT INTO scheduled_changes " +
                            "(target_id, target_kind, field_name, new_value, effective_date, effective_at) " +
                            "VALUES (1, 'PRODUCT_OPTION_GROUP', 'overrides', '{}', '2026-10-01', '2026-09-30T15:00:00Z')",
                    )
                }
            }

        @Test
        fun `대기 예약용 인덱스는 대기 상태에만 걸린다`() =
            runTest {
                // Exposed 스키마 검사(MigrationUtils)는 인덱스의 WHERE 조건을 비교하지 않으므로 여기서 확인한다.
                assertEquals(
                    2,
                    count(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 'scheduled_changes' " +
                            "AND indexname IN ('scheduled_changes_one_pending_per_field', 'scheduled_changes_pending_due_idx') " +
                            "AND indexdef LIKE '%WHERE ((status)::text = ''PENDING''::text)'",
                    ),
                )
            }
    }

    @Nested
    @DisplayName("배치 대상 조회")
    inner class DueForApplication {
        // 서울 2026-10-01 00:00 = 2026-09-30T15:00:00Z
        private val now = Instant.parse("2026-09-30T15:00:00Z")

        @Test
        fun `적용 시각이 지난 대기 예약만 적용 시각 순으로 가져온다`() =
            runTest {
                val onTime = register(targetId = 1, effectiveDate = LocalDate.of(2026, 10, 1))
                val missed = register(targetId = 2, effectiveDate = LocalDate.of(2026, 9, 25))
                register(targetId = 3, effectiveDate = LocalDate.of(2026, 10, 2))
                val cancelled = register(targetId = 4, effectiveDate = LocalDate.of(2026, 9, 25))
                tx.inTransaction { repository.save(cancelled.also { it.cancel() }) }

                val due = tx.inTransaction { repository.findDueForApplication(now, limit = 10) }

                assertEquals(listOf(missed.id, onTime.id), due.map { it.id })
            }

        @Test
        fun `건수 상한만큼만 가져온다`() =
            runTest {
                val first = register(targetId = 1, effectiveDate = LocalDate.of(2026, 9, 25))
                register(targetId = 2, effectiveDate = LocalDate.of(2026, 10, 1))

                val due = tx.inTransaction { repository.findDueForApplication(now, limit = 1) }

                assertEquals(listOf(first.id), due.map { it.id })
            }

        @Test
        fun `다른 트랜잭션이 잠근 예약은 건너뛴다`() =
            runTest {
                val first = register(targetId = 1, effectiveDate = LocalDate.of(2026, 9, 30))
                val second = register(targetId = 2, effectiveDate = LocalDate.of(2026, 10, 1))
                val lockedByA = CompletableDeferred<List<ScheduledChange>>()
                val releaseA = CompletableDeferred<Unit>()

                // 워커 A: 배치 조회로 대기 예약을 잠근 채 트랜잭션을 끝내지 않고 기다린다.
                val workerA =
                    launch {
                        tx.inTransaction {
                            lockedByA.complete(repository.findDueForApplication(now, limit = 10))
                            releaseA.await()
                        }
                    }
                try {
                    assertEquals(listOf(first.id, second.id), lockedByA.await().map { it.id })
                    // A가 잠근 뒤 들어온 예약은 B가 가져간다.
                    val third = register(targetId = 3, effectiveDate = LocalDate.of(2026, 9, 29))

                    // 워커 B: A의 잠금을 기다리지 않고, A가 잠근 예약을 건너뛴다.
                    val dueForB = tx.inTransaction { repository.findDueForApplication(now, limit = 10) }

                    assertEquals(listOf(third.id), dueForB.map { it.id })
                } finally {
                    releaseA.complete(Unit)
                    workerA.join()
                }
            }
    }

    @Nested
    @DisplayName("적용 직전 단건 잠금")
    inner class PendingByIdForUpdate {
        @Test
        fun `대기 중인 예약만 잠가 가져온다`() =
            runTest {
                val pending = register(targetId = 1)
                val processed = register(targetId = 2)
                tx.inTransaction { repository.save(processed.also { it.apply() }) }

                assertEquals(
                    pending.id,
                    tx.inTransaction { repository.findPendingByIdForUpdateSkipLocked(pending.id) }?.id,
                )
                assertNull(tx.inTransaction { repository.findPendingByIdForUpdateSkipLocked(processed.id) })
            }

        @Test
        fun `다른 트랜잭션이 잠근 예약은 기다리지 않고 건너뛴다`() =
            runTest {
                val locked = register(targetId = 1)
                val lockedByA = CompletableDeferred<Unit>()
                val releaseA = CompletableDeferred<Unit>()

                val workerA =
                    launch {
                        tx.inTransaction {
                            repository.findPendingByIdForUpdateSkipLocked(locked.id)
                            lockedByA.complete(Unit)
                            releaseA.await()
                        }
                    }
                try {
                    lockedByA.await()

                    assertNull(tx.inTransaction { repository.findPendingByIdForUpdateSkipLocked(locked.id) })
                } finally {
                    releaseA.complete(Unit)
                    workerA.join()
                }
            }
    }

    @Nested
    @DisplayName("상태 전이 저장")
    inner class StatusTransition {
        @Test
        fun `대기 예약의 상태 전이를 저장한다`() =
            runTest {
                val saved = register(targetId = 1)

                tx.inTransaction { repository.save(saved.also { it.fail() }) }

                assertEquals(ScheduleStatus.FAILED, tx.inTransaction { repository.findById(saved.id) }?.status)
            }

        @Test
        fun `이미 처리된 예약을 다시 전이해 저장하면 거부하고 DB는 그대로다`() =
            runTest {
                val saved = register(targetId = 1)
                // 배치가 적용하는 동안 관리자가 같은 예약을 대기 상태로 읽어 둔 상황
                val staleForAdmin = assertNotNull(tx.inTransaction { repository.findById(saved.id) })
                val forBatch = assertNotNull(tx.inTransaction { repository.findById(saved.id) })
                tx.inTransaction { repository.save(forBatch.also { it.apply() }) }

                staleForAdmin.cancel()
                assertFailsWith<ScheduleAlreadyProcessedException> { tx.inTransaction { repository.save(staleForAdmin) } }

                assertEquals(ScheduleStatus.APPLIED, tx.inTransaction { repository.findById(saved.id) }?.status)
            }
    }

    private suspend fun register(
        targetId: Long = 1,
        newValue: ScheduledValue = ProductFieldValue.BasePrice(Money(5000)),
        effectiveDate: LocalDate = LocalDate.of(2026, 10, 1),
    ): ScheduledChange = tx.inTransaction { repository.insert(newSchedule(targetId, newValue, effectiveDate)) }

    private fun newSchedule(
        targetId: Long,
        newValue: ScheduledValue,
        effectiveDate: LocalDate = LocalDate.of(2026, 10, 1),
    ) = ScheduledChange.NewScheduledChange.of(
        targetId = targetId,
        newValue = newValue,
        effectiveDate = effectiveDate,
        today = today,
        businessZone = seoul,
    )

    private fun option(
        key: String,
        price: Long = 0,
    ) = Option(OptionKey(key), "옵션 $key", Money(price))

    private fun overrides(
        optionGroupId: Long,
        vararg overrides: OptionOverride,
    ) = ProductFieldValue.OptionOverrides(OptionGroupId(optionGroupId), overrides.toList())

    // 예약 가능한 필드마다 하나씩. 값이 비어 있을 수 있는 필드는 빈 값도 함께 확인한다.
    private fun allFieldValues(): List<ScheduledFieldValue> =
        listOf(
            ProductFieldValue.Name("아이스 아메리카노"),
            ProductFieldValue.Category(CategoryId(3)),
            ProductFieldValue.Description("산미 있는 원두"),
            ProductFieldValue.Description(null),
            ProductFieldValue.Image("https://cdn.example.com/americano.png"),
            ProductFieldValue.Image(null),
            ProductFieldValue.BasePrice(Money(4500)),
            ProductFieldValue.Tags(setOf(TagId(1), TagId(2))),
            ProductFieldValue.Tags(emptySet()),
            ProductFieldValue.Groups(setOf(ProductGroupId(7))),
            ProductFieldValue.Scope(StoreScope.All),
            ProductFieldValue.Scope(StoreScope.Limited(setOf(StoreId(10), StoreId(11)))),
            ProductFieldValue.Scope(StoreScope.Limited(emptySet())),
            ProductFieldValue.Activation,
            ProductFieldValue.Discontinuation,
            ProductFieldValue.OptionGroupLinks(listOf(OptionGroupId(13), OptionGroupId(12))),
            overrides(
                optionGroupId = 12,
                OptionOverride.Price(OptionKey("L"), Money(700)),
                OptionOverride.Exclude(OptionKey("XL")),
            ),
            OptionGroupFieldValue.Options(listOf(option("S"), option("M", price = 500), option("L", price = 1000))),
        )

    private fun leafTypesOf(type: KClass<*>): Set<KClass<*>> =
        if (type.isSealed) type.sealedSubclasses.flatMap { leafTypesOf(it) }.toSet() else setOf(type)
}
