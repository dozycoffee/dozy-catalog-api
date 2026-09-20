package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.product.application.product.command.ExcludeOptionCommand
import com.dozycoffee.catalog.product.application.product.command.LinkOptionGroupCommand
import com.dozycoffee.catalog.product.application.product.command.OverrideOptionPriceCommand
import com.dozycoffee.catalog.product.application.product.command.RemoveOptionOverrideCommand
import com.dozycoffee.catalog.product.application.product.command.ReorderOptionGroupsCommand
import com.dozycoffee.catalog.product.application.product.command.UnlinkOptionGroupCommand
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.exception.DuplicateOptionGroupLinkException
import com.dozycoffee.catalog.product.domain.product.exception.InvalidOptionGroupOrderException
import com.dozycoffee.catalog.product.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.product.domain.product.exception.OptionKeyNotFoundException
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.product.exception.ProductOptionGroupNotLinkedException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("상품의 옵션 그룹 연결과 상품별 옵션 예외 (요구사항 1.9 / S5)")
class ProductOptionApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductOptionApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    // 검증 대상은 상품의 옵션 연결·예외다. 카테고리·상품·옵션 그룹은 다른 유스케이스를 거치지 않고
    // SQL로 직접 넣는다(docs/architecture/testing.md).
    @BeforeEach
    fun setUpCategory() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1)")
        }

    @Nested
    @DisplayName("옵션 그룹 연결")
    inner class Linking {
        @Test
        fun `연결하면 기존 연결들 뒤에 붙는다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L)

                val linked = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                service.linkOptionGroup(LinkOptionGroupCommand(product, linked.version, shot))

                assertEquals(listOf(size, shot), linkedIdsInOrder(product))
            }

        @Test
        fun `같은 옵션 그룹을 두 번 연결하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val linked = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))

                assertFailsWith<DuplicateOptionGroupLinkException> {
                    service.linkOptionGroup(LinkOptionGroupCommand(product, linked.version, size))
                }
                assertEquals(1, count("SELECT count(*) FROM product_option_groups"))
            }

        @Test
        fun `없는 옵션 그룹을 연결하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")

                assertFailsWith<OptionGroupNotFoundException> {
                    service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = OptionGroupId(999)))
                }
                assertEquals(0, count("SELECT count(*) FROM product_option_groups"))
            }

        @Test
        fun `오래된 버전으로 연결하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L)
                service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))

                assertFailsWith<VersionConflictException> {
                    service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = shot))
                }
                assertEquals(1, count("SELECT count(*) FROM product_option_groups"))
            }

        @Test
        fun `없는 상품에 연결하면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> {
                    service.linkOptionGroup(LinkOptionGroupCommand(ProductId(999), version = 0, optionGroupId = OptionGroupId(1)))
                }
            }
    }

    @Nested
    @DisplayName("옵션 그룹 연결 해제")
    inner class Unlinking {
        @Test
        fun `해제하면 그 연결의 예외도 함께 사라지고 다시 연결해도 복원되지 않는다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current =
                    service.overrideOptionPrice(
                        OverrideOptionPriceCommand(product, current.version, size, OptionKey("GRANDE"), Money(700)),
                    )

                current = service.unlinkOptionGroup(UnlinkOptionGroupCommand(product, current.version, size))
                assertEquals(0, count("SELECT count(*) FROM product_option_groups"))
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))

                service.linkOptionGroup(LinkOptionGroupCommand(product, current.version, size))
                assertTrue(overridesOf(product, size).isEmpty())
            }
    }

    @Nested
    @DisplayName("연결 순서 변경")
    inner class Reordering {
        @Test
        fun `연결된 옵션 그룹 전체를 담은 순서대로 바꾼다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current = service.linkOptionGroup(LinkOptionGroupCommand(product, current.version, shot))

                service.reorderOptionGroups(ReorderOptionGroupsCommand(product, current.version, listOf(shot, size)))

                assertEquals(listOf(shot, size), linkedIdsInOrder(product))
            }

        @Test
        fun `일부만 담은 순서는 거부하고 연결을 그대로 둔다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current = service.linkOptionGroup(LinkOptionGroupCommand(product, current.version, shot))

                assertFailsWith<InvalidOptionGroupOrderException> {
                    service.reorderOptionGroups(ReorderOptionGroupsCommand(product, current.version, listOf(shot)))
                }
                assertEquals(listOf(size, shot), linkedIdsInOrder(product))
            }

        @Test
        fun `연결되지 않은 옵션 그룹이 섞이면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L)
                val current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))

                assertFailsWith<ProductOptionGroupNotLinkedException> {
                    service.reorderOptionGroups(ReorderOptionGroupsCommand(product, current.version, listOf(size, shot)))
                }
                assertEquals(listOf(size), linkedIdsInOrder(product))
            }
    }

    @Nested
    @DisplayName("상품별 옵션 예외")
    inner class Overrides {
        @Test
        fun `가격 예외는 그 상품에만 적용된다`() =
            runTest {
                val americano = insertProduct("아메리카노")
                val latte = insertProduct("라떼")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                val linkedAmericano = service.linkOptionGroup(LinkOptionGroupCommand(americano, version = 0, optionGroupId = size))
                service.linkOptionGroup(LinkOptionGroupCommand(latte, version = 0, optionGroupId = size))

                service.overrideOptionPrice(
                    OverrideOptionPriceCommand(americano, linkedAmericano.version, size, OptionKey("GRANDE"), Money(700)),
                )

                val americanoGrande =
                    service
                        .getEffectiveOptions(americano)
                        .groups
                        .single()
                        .options
                        .last()
                assertEquals(Money(700), americanoGrande.price)
                assertTrue(americanoGrande.priceOverridden)
                val latteGrande =
                    service
                        .getEffectiveOptions(latte)
                        .groups
                        .single()
                        .options
                        .last()
                assertEquals(Money(500), latteGrande.price)
                assertEquals(false, latteGrande.priceOverridden)
            }

        @Test
        fun `같은 옵션 키에 다시 지정하면 기존 예외를 대체한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current =
                    service.overrideOptionPrice(
                        OverrideOptionPriceCommand(product, current.version, size, OptionKey("GRANDE"), Money(700)),
                    )

                service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("GRANDE")))

                assertEquals(1, overridesOf(product, size).size)
                assertEquals(
                    listOf(OptionKey("TALL")),
                    service
                        .getEffectiveOptions(product)
                        .groups
                        .single()
                        .options
                        .map { it.optionKey },
                )
            }

        @Test
        fun `연결되지 않은 옵션 그룹에 예외를 지정하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)

                assertFailsWith<ProductOptionGroupNotLinkedException> {
                    service.overrideOptionPrice(
                        OverrideOptionPriceCommand(
                            product,
                            version = 0,
                            optionGroupId = size,
                            optionKey = OptionKey("TALL"),
                            price = Money(700),
                        ),
                    )
                }
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `옵션 그룹에 없는 옵션 키에 예외를 지정하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                val current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))

                assertFailsWith<OptionKeyNotFoundException> {
                    service.overrideOptionPrice(
                        OverrideOptionPriceCommand(product, current.version, size, OptionKey("VENTI"), Money(700)),
                    )
                }
                assertFailsWith<OptionKeyNotFoundException> {
                    service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("VENTI")))
                }
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `제외로 선택 가능한 옵션이 0개가 되면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current = service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("GRANDE")))

                assertFailsWith<NoSelectableOptionException> {
                    service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("TALL")))
                }
                assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
                assertEquals(
                    listOf(OptionKey("TALL")),
                    service
                        .getEffectiveOptions(product)
                        .groups
                        .single()
                        .options
                        .map { it.optionKey },
                )
            }

        @Test
        fun `예외를 해제하면 옵션 그룹의 구성과 가격을 다시 따른다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current = service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("GRANDE")))

                service.removeOverride(RemoveOptionOverrideCommand(product, current.version, size, OptionKey("GRANDE")))

                val options =
                    service
                        .getEffectiveOptions(product)
                        .groups
                        .single()
                        .options
                assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), options.map { it.optionKey })
                assertEquals(Money(500), options.last().price)
                assertEquals(0, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `오래된 버전으로 예외를 지정하면 거부한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                val current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                service.overrideOptionPrice(
                    OverrideOptionPriceCommand(product, current.version, size, OptionKey("GRANDE"), Money(700)),
                )

                assertFailsWith<VersionConflictException> {
                    service.overrideOptionPrice(
                        OverrideOptionPriceCommand(product, current.version, size, OptionKey("TALL"), Money(300)),
                    )
                }
                assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
            }
    }

    @Nested
    @DisplayName("유효 옵션 구성 조회")
    inner class EffectiveOptions {
        @Test
        fun `연결 순서와 옵션 순서를 지키고 제외한 옵션은 빠진다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 0L, "GRANDE" to 500L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L, "TWO" to 1000L, required = false)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                current = service.linkOptionGroup(LinkOptionGroupCommand(product, current.version, shot))
                service.excludeOption(ExcludeOptionCommand(product, current.version, shot, OptionKey("TWO")))

                val config = service.getEffectiveOptions(product)

                assertEquals(listOf(size, shot), config.groups.map { it.optionGroupId })
                assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), config.groups[0].options.map { it.optionKey })
                assertEquals(listOf(OptionKey("ONE")), config.groups[1].options.map { it.optionKey })
            }

        @Test
        fun `표시용 시작가는 기준가에 필수 그룹의 최저가만 더한다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 300L, "GRANDE" to 800L)
                val shot = insertOptionGroup("샷 추가", "ONE" to 500L, required = false)
                var current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                service.linkOptionGroup(LinkOptionGroupCommand(product, current.version, shot))

                val config = service.getEffectiveOptions(product)

                assertEquals(Money(4800), config.displayStartingPrice)
                assertNull(config.groups[0].autoSelectedOption)
                assertEquals(
                    OptionKey("ONE"),
                    config.groups[1]
                        .options
                        .single()
                        .optionKey,
                )
            }

        @Test
        fun `필수 그룹에 유효 옵션이 1개면 자동 선택으로 본다`() =
            runTest {
                val product = insertProduct("아메리카노")
                val size = insertOptionGroup("사이즈", "TALL" to 300L, "GRANDE" to 800L)
                val current = service.linkOptionGroup(LinkOptionGroupCommand(product, version = 0, optionGroupId = size))
                service.excludeOption(ExcludeOptionCommand(product, current.version, size, OptionKey("GRANDE")))

                val group = service.getEffectiveOptions(product).groups.single()

                assertEquals(OptionKey("TALL"), assertNotNull(group.autoSelectedOption).optionKey)
            }

        @Test
        fun `없는 상품을 조회하면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.getEffectiveOptions(ProductId(999)) }
            }
    }

    private suspend fun linkedIdsInOrder(productId: ProductId): List<OptionGroupId> {
        val product = assertNotNull(tx { productRepository.findById(productId) })
        return product.optionGroupLinks.sortedBy { it.displayOrder }.map { it.id }
    }

    private suspend fun overridesOf(
        productId: ProductId,
        optionGroupId: OptionGroupId,
    ) = assertNotNull(
        assertNotNull(tx { productRepository.findById(productId) })
            .optionGroupLinks
            .firstOrNull { it.id == optionGroupId },
        "상품(${productId.value})에 옵션 그룹(${optionGroupId.value}) 연결이 없습니다",
    ).overrides

    private suspend fun insertProduct(name: String): ProductId {
        execute("INSERT INTO products (name, category_id, base_price, tracks_inventory) VALUES ('$name', 2, 4500, false)")
        return ProductId(count("SELECT max(id) FROM products"))
    }

    private suspend fun insertOptionGroup(
        name: String,
        vararg options: Pair<String, Long>,
        required: Boolean = true,
    ): OptionGroupId {
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('$name', 'SINGLE', $required)")
        val optionGroupId = count("SELECT max(id) FROM option_groups")
        options.forEachIndexed { index, (key, price) ->
            execute(
                "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                    "VALUES ($optionGroupId, '$key', '$key', $price, $index)",
            )
        }
        return OptionGroupId(optionGroupId)
    }
}
