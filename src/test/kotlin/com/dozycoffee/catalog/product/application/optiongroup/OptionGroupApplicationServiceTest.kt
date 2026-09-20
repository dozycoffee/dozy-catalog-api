package com.dozycoffee.catalog.product.application.optiongroup

import com.dozycoffee.catalog.core.InvalidMoneyAmountException
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.fixture.option
import com.dozycoffee.catalog.product.application.optiongroup.command.ChangeOptionGroupDefinitionCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.RegisterOptionGroupCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.ReplaceOptionsCommand
import com.dozycoffee.catalog.product.domain.optiongroup.Option
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.product.domain.optiongroup.exception.DuplicateOptionKeyException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.EmptyOptionGroupException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupStillReferencedException
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("옵션 그룹 관리와 옵션 목록 즉시 교체 (요구사항 1.9 / S5)")
class OptionGroupApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: OptionGroupApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    // 검증 대상이 아닌 상품·카테고리는 다른 유스케이스를 거치지 않고 SQL로 직접 넣는다
    // (docs/architecture/testing.md).
    @BeforeEach
    fun setUpCategory() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        }

    @Nested
    @DisplayName("생성")
    inner class Registration {
        @Test
        fun `입력한 순서대로 옵션이 저장된다`() =
            runTest {
                val created =
                    service.register(
                        registerCommand(option("TALL"), option("GRANDE", price = 500), required = true),
                    )

                val found = service.get(created.id)
                assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), found.options.map { it.optionKey })
                assertEquals(Money(500), found.options[1].price)
                assertEquals(SelectionType.SINGLE, found.selectionType)
                assertTrue(found.required)
            }

        @Test
        fun `옵션이 0개면 거부한다`() =
            runTest {
                assertFailsWith<EmptyOptionGroupException> { service.register(registerCommand()) }
                assertEquals(0, count("SELECT count(*) FROM option_groups"))
            }

        @Test
        fun `옵션 키가 겹치면 거부한다`() =
            runTest {
                assertFailsWith<DuplicateOptionKeyException> {
                    service.register(registerCommand(option("TALL"), option("TALL", price = 500)))
                }
                assertEquals(0, count("SELECT count(*) FROM option_groups"))
            }

        // 가격은 Money가 만들어지는 시점에 0 이상으로 강제된다.
        @Test
        fun `옵션 가격이 음수면 거부한다`() =
            runTest {
                assertFailsWith<InvalidMoneyAmountException> {
                    service.register(registerCommand(option("TALL", price = -500)))
                }
                assertEquals(0, count("SELECT count(*) FROM option_groups"))
            }
    }

    @Nested
    @DisplayName("이름·선택 방식·필수 여부 변경 (즉시 반영)")
    inner class DefinitionChange {
        @Test
        fun `입력한 값이 즉시 반영된다`() =
            runTest {
                val created = service.register(registerCommand(option("TALL"), required = true))

                service.changeDefinition(
                    ChangeOptionGroupDefinitionCommand(
                        optionGroupId = created.id,
                        version = created.version,
                        name = "샷 추가",
                        selectionType = SelectionType.MULTI,
                        required = false,
                    ),
                )

                val found = service.get(created.id)
                assertEquals("샷 추가", found.name)
                assertEquals(SelectionType.MULTI, found.selectionType)
                assertEquals(false, found.required)
                assertEquals(listOf(OptionKey("TALL")), found.options.map { it.optionKey })
            }

        @Test
        fun `오래된 버전으로 변경하면 거부하고 값을 바꾸지 않는다`() =
            runTest {
                val created = service.register(registerCommand(option("TALL")))
                service.changeDefinition(definitionCommand(created.id, created.version, name = "사이즈 v2"))

                assertFailsWith<VersionConflictException> {
                    service.changeDefinition(definitionCommand(created.id, created.version, name = "사이즈 v3"))
                }
                assertEquals("사이즈 v2", service.get(created.id).name)
            }

        @Test
        fun `없는 옵션 그룹을 변경하면 거부한다`() =
            runTest {
                assertFailsWith<OptionGroupNotFoundException> {
                    service.changeDefinition(definitionCommand(OptionGroupId(999), version = 0, name = "사이즈"))
                }
            }
    }

    @Nested
    @DisplayName("S5 기본 흐름 — 옵션 목록 즉시 교체")
    inner class OptionReplacement {
        @Test
        fun `예외가 없는 연결 상품에는 새 구성과 가격이 그대로 반영된다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE", price = 500)))
                val americano = insertProduct("아메리카노")
                linkTo(americano, group.id)

                service.replaceOptions(
                    ReplaceOptionsCommand(
                        optionGroupId = group.id,
                        version = group.version,
                        options = listOf(option("TALL", price = 300), option("VENTI", price = 1000)),
                    ),
                )

                val found = service.get(group.id)
                assertEquals(listOf(OptionKey("TALL"), OptionKey("VENTI")), found.options.map { it.optionKey })
                assertEquals(Money(300), found.options[0].price)
                assertTrue(overridesOf(americano, group.id).isEmpty())
            }

        @Test
        fun `사라진 옵션 키의 예외는 모든 연결 상품에서 삭제되고 남은 키의 예외는 유지된다`() =
            runTest {
                val group =
                    service.register(registerCommand(option("TALL"), option("GRANDE"), option("VENTI")))
                val americano = insertProduct("아메리카노")
                val latte = insertProduct("라떼")
                linkTo(americano, group.id)
                linkTo(latte, group.id)
                insertPriceOverride(americano, group.id, "GRANDE", 700)
                insertExcludeOverride(americano, group.id, "VENTI")
                insertPriceOverride(latte, group.id, "VENTI", 1200)

                service.replaceOptions(
                    ReplaceOptionsCommand(
                        optionGroupId = group.id,
                        version = group.version,
                        options = listOf(option("TALL"), option("GRANDE")),
                    ),
                )

                val americanoOverrides = overridesOf(americano, group.id)
                assertEquals(1, americanoOverrides.size)
                val kept = assertIs<OptionOverride.Price>(americanoOverrides.single())
                assertEquals(OptionKey("GRANDE"), kept.optionKey)
                assertEquals(Money(700), kept.price)
                assertTrue(overridesOf(latte, group.id).isEmpty())
                assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `삭제된 예외는 같은 옵션 키가 다시 생겨도 복원되지 않는다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("VENTI")))
                val americano = insertProduct("아메리카노")
                linkTo(americano, group.id)
                insertPriceOverride(americano, group.id, "VENTI", 1200)

                val afterRemoval =
                    service.replaceOptions(
                        ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL"))),
                    )
                service.replaceOptions(
                    ReplaceOptionsCommand(group.id, afterRemoval.version, listOf(option("TALL"), option("VENTI"))),
                )

                assertTrue(overridesOf(americano, group.id).isEmpty())
            }

        @Test
        fun `어느 한 상품이라도 선택 가능한 옵션이 0개가 되면 전체를 거부한다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))
                val americano = insertProduct("아메리카노")
                val latte = insertProduct("라떼")
                linkTo(americano, group.id)
                linkTo(latte, group.id)
                // 라떼는 TALL을 제외해 두었으므로, TALL만 남기면 선택 가능한 옵션이 0개가 된다.
                insertExcludeOverride(latte, group.id, "TALL")

                assertFailsWith<NoSelectableOptionException> {
                    service.replaceOptions(ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL"))))
                }

                val found = service.get(group.id)
                assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), found.options.map { it.optionKey })
                assertEquals(group.version, found.version)
                assertEquals(1, overridesOf(latte, group.id).size)
            }

        @Test
        fun `Draft나 Discontinued 상품의 제외 설정도 함께 본다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))
                val seasonal = insertProduct("시즌 음료", status = "DISCONTINUED")
                linkTo(seasonal, group.id)
                insertExcludeOverride(seasonal, group.id, "TALL")

                assertFailsWith<NoSelectableOptionException> {
                    service.replaceOptions(ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL"))))
                }
                assertEquals(2, count("SELECT count(*) FROM options"))
            }

        @Test
        fun `옵션 0개·키 중복·음수 가격이면 거부하고 목록을 바꾸지 않는다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))

                assertFailsWith<EmptyOptionGroupException> {
                    service.replaceOptions(ReplaceOptionsCommand(group.id, group.version, emptyList()))
                }
                assertFailsWith<DuplicateOptionKeyException> {
                    service.replaceOptions(
                        ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL"), option("TALL"))),
                    )
                }
                assertFailsWith<InvalidMoneyAmountException> {
                    service.replaceOptions(
                        ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL", price = -1))),
                    )
                }
                assertEquals(2, count("SELECT count(*) FROM options"))
            }

        @Test
        fun `오래된 버전으로 교체하면 거부한다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))
                service.replaceOptions(ReplaceOptionsCommand(group.id, group.version, listOf(option("TALL"))))

                assertFailsWith<VersionConflictException> {
                    service.replaceOptions(ReplaceOptionsCommand(group.id, group.version, listOf(option("VENTI"))))
                }
                assertEquals(listOf(OptionKey("TALL")), service.get(group.id).options.map { it.optionKey })
            }

        // 옵션 그룹을 잠근 채 검증하고 교체하므로, 같은 그룹의 교체는 끼어들지 못하고 차례로 처리된다.
        // 뒤에 처리되는 쪽은 자기가 보던 버전이 이미 올라가 있어 충돌로 거부된다(ADR-0013).
        @Test
        fun `같은 옵션 그룹을 동시에 교체하면 한쪽만 성공한다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))

                val results =
                    coroutineScope {
                        listOf("ICE", "HOT")
                            .map { key ->
                                async(Dispatchers.IO) {
                                    runCatching {
                                        service.replaceOptions(
                                            ReplaceOptionsCommand(group.id, group.version, listOf(option(key))),
                                        )
                                    }
                                }
                            }.awaitAll()
                    }

                assertEquals(1, results.count { it.isSuccess })
                assertIs<VersionConflictException>(results.single { it.isFailure }.exceptionOrNull())
                assertEquals(1, count("SELECT count(*) FROM options"))
                assertEquals(1, count("SELECT version FROM option_groups WHERE id = ${group.id.value}"))
            }

        @Test
        fun `없는 옵션 그룹의 옵션을 교체하면 거부한다`() =
            runTest {
                assertFailsWith<OptionGroupNotFoundException> {
                    service.replaceOptions(ReplaceOptionsCommand(OptionGroupId(999), version = 0, options = listOf(option("TALL"))))
                }
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `연결한 상품이 없으면 옵션까지 함께 삭제된다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL"), option("GRANDE")))

                service.delete(group.id)

                assertEquals(0, count("SELECT count(*) FROM option_groups"))
                assertEquals(0, count("SELECT count(*) FROM options"))
            }

        @Test
        fun `연결한 상품이 있으면 거부한다`() =
            runTest {
                val group = service.register(registerCommand(option("TALL")))
                linkTo(insertProduct("아메리카노"), group.id)

                assertFailsWith<OptionGroupStillReferencedException> { service.delete(group.id) }
                assertEquals(1, count("SELECT count(*) FROM option_groups"))
                assertEquals(1, count("SELECT count(*) FROM options"))
            }

        @Test
        fun `없는 옵션 그룹을 삭제하면 거부한다`() =
            runTest {
                assertFailsWith<OptionGroupNotFoundException> { service.delete(OptionGroupId(999)) }
            }
    }

    private fun registerCommand(
        vararg options: Option,
        name: String = "사이즈",
        selectionType: SelectionType = SelectionType.SINGLE,
        required: Boolean = true,
    ) = RegisterOptionGroupCommand(
        name = name,
        selectionType = selectionType,
        required = required,
        options = options.toList(),
    )

    private fun definitionCommand(
        optionGroupId: OptionGroupId,
        version: Long,
        name: String,
    ) = ChangeOptionGroupDefinitionCommand(
        optionGroupId = optionGroupId,
        version = version,
        name = name,
        selectionType = SelectionType.SINGLE,
        required = true,
    )

    private suspend fun overridesOf(
        productId: ProductId,
        optionGroupId: OptionGroupId,
    ): List<OptionOverride> {
        val product = assertNotNull(tx { productRepository.findById(productId) })
        val link = product.optionGroupLinks.firstOrNull { it.id == optionGroupId }
        return assertNotNull(link, "상품(${productId.value})에 옵션 그룹(${optionGroupId.value}) 연결이 없습니다").overrides
    }

    private suspend fun insertProduct(
        name: String,
        status: String = "DRAFT",
    ): ProductId {
        execute(
            "INSERT INTO products (name, category_id, base_price, tracks_inventory, status) " +
                "VALUES ('$name', 2, 4500, false, '$status')",
        )
        return ProductId(count("SELECT max(id) FROM products"))
    }

    private suspend fun linkTo(
        productId: ProductId,
        optionGroupId: OptionGroupId,
    ) = execute(
        "INSERT INTO product_option_groups (product_id, option_group_id, display_order) " +
            "VALUES (${productId.value}, ${optionGroupId.value}, 0)",
    )

    private suspend fun insertPriceOverride(
        productId: ProductId,
        optionGroupId: OptionGroupId,
        optionKey: String,
        price: Long,
    ) = execute(
        "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price) " +
            "VALUES (${productId.value}, ${optionGroupId.value}, '$optionKey', 'PRICE', $price)",
    )

    private suspend fun insertExcludeOverride(
        productId: ProductId,
        optionGroupId: OptionGroupId,
        optionKey: String,
    ) = execute(
        "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type) " +
            "VALUES (${productId.value}, ${optionGroupId.value}, '$optionKey', 'EXCLUDE')",
    )
}
