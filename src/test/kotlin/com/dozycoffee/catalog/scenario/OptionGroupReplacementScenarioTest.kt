package com.dozycoffee.catalog.scenario

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.fixture.option
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.optiongroup.OptionGroupApplicationService
import com.dozycoffee.catalog.product.application.optiongroup.command.RegisterOptionGroupCommand
import com.dozycoffee.catalog.product.application.optiongroup.command.ReplaceOptionsCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.ProductOptionApplicationService
import com.dozycoffee.catalog.product.application.product.command.ExcludeOptionCommand
import com.dozycoffee.catalog.product.application.product.command.OverrideOptionPriceCommand
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.optiongroup.SelectionType
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.exception.NoSelectableOptionException
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 여러 유스케이스에 걸친 흐름을 순서대로 밟는다(docs/architecture/testing.md).
@DisplayName("S5. 옵션 그룹 변경과 상품별 예외")
class OptionGroupReplacementScenarioTest : ApplicationTest() {
    @Autowired
    private lateinit var optionGroupService: OptionGroupApplicationService

    @Autowired
    private lateinit var productService: ProductApplicationService

    @Autowired
    private lateinit var productOptionService: ProductOptionApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    private var coffeeId: CategoryId = CategoryId(0)

    @BeforeEach
    fun setUpCategory() =
        runTest {
            val beverage = categoryService.registerTopLevel("음료")
            coffeeId = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피")).id
        }

    @Test
    fun `S5 옵션 목록을 교체하면 예외 없는 상품은 새 구성을 따르고 사라진 키의 예외만 삭제된다`() =
        runTest {
            // 준비: 두 상품이 같은 옵션 그룹을 공유하고, 아메리카노만 예외를 갖는다.
            val size =
                optionGroupService.register(
                    RegisterOptionGroupCommand(
                        name = "사이즈",
                        selectionType = SelectionType.SINGLE,
                        required = true,
                        options = listOf(option("TALL"), option("GRANDE", price = 500), option("VENTI", price = 1000)),
                    ),
                )
            val americano = productService.register(registerCommand("아메리카노", size.id))
            val latte = productService.register(registerCommand("라떼", size.id))

            // 기본 흐름 (상품별 예외 지정): 아메리카노만 GRANDE 가격을 바꾸고 VENTI를 제외한다.
            val priced =
                productOptionService.overrideOptionPrice(
                    OverrideOptionPriceCommand(americano.id, americano.version, size.id, OptionKey("GRANDE"), Money(700)),
                )
            productOptionService.excludeOption(
                ExcludeOptionCommand(americano.id, priced.version, size.id, OptionKey("VENTI")),
            )
            assertEquals(
                listOf(OptionKey("TALL"), OptionKey("GRANDE")),
                effectiveKeys(americano.id, size.id),
            )

            // 1~4단계: 옵션 목록 전체를 새로 입력해 즉시 교체한다. VENTI가 사라지고 TALL 가격이 바뀐다.
            optionGroupService.replaceOptions(
                ReplaceOptionsCommand(
                    optionGroupId = size.id,
                    version = size.version,
                    options = listOf(option("TALL", price = 300), option("GRANDE", price = 600)),
                ),
            )

            // 5단계: 사라진 VENTI의 예외는 삭제되고, 남은 GRANDE의 가격 예외는 그대로다.
            assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
            val americanoOptions =
                productOptionService
                    .getEffectiveOptions(americano.id)
                    .groups
                    .single()
                    .options
            assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), americanoOptions.map { it.optionKey })
            assertEquals(Money(300), americanoOptions[0].price)
            assertEquals(Money(700), americanoOptions[1].price)
            assertTrue(americanoOptions[1].priceOverridden)

            // 6단계: 예외가 없는 라떼는 새 구성과 가격을 그대로 따른다.
            val latteOptions =
                productOptionService
                    .getEffectiveOptions(latte.id)
                    .groups
                    .single()
                    .options
            assertEquals(listOf(Money(300), Money(600)), latteOptions.map { it.price })
            assertEquals(Money(4800), productOptionService.getEffectiveOptions(latte.id).displayStartingPrice)
        }

    @Test
    fun `S5 예외 흐름 어느 한 상품이라도 선택 가능한 옵션이 0개가 되면 교체 전체를 거부한다`() =
        runTest {
            val size =
                optionGroupService.register(
                    RegisterOptionGroupCommand(
                        name = "사이즈",
                        selectionType = SelectionType.SINGLE,
                        required = true,
                        options = listOf(option("TALL"), option("GRANDE", price = 500)),
                    ),
                )
            val americano = productService.register(registerCommand("아메리카노", size.id))
            val latte = productService.register(registerCommand("라떼", size.id))
            // 라떼만 GRANDE를 제외해 두었다.
            productOptionService.excludeOption(
                ExcludeOptionCommand(latte.id, latte.version, size.id, OptionKey("GRANDE")),
            )

            // 예외 흐름 3a: GRANDE만 남기면 라떼의 선택 가능한 옵션이 0개가 되므로 교체 전체가 거부된다.
            assertFailsWith<NoSelectableOptionException> {
                optionGroupService.replaceOptions(
                    ReplaceOptionsCommand(size.id, size.version, listOf(option("GRANDE", price = 500))),
                )
            }

            // 옵션 목록도, 상품의 예외도 그대로다.
            assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), optionGroupService.get(size.id).options.map { it.optionKey })
            assertEquals(1, count("SELECT count(*) FROM product_option_overrides"))
            assertEquals(listOf(OptionKey("TALL"), OptionKey("GRANDE")), effectiveKeys(americano.id, size.id))
            assertEquals(listOf(OptionKey("TALL")), effectiveKeys(latte.id, size.id))
        }

    private fun registerCommand(
        name: String,
        optionGroupId: OptionGroupId,
    ) = RegisterProductCommand(
        name = name,
        categoryId = coffeeId,
        basePrice = Money(4500),
        tracksInventory = false,
        optionGroupIds = listOf(optionGroupId),
    )

    private suspend fun effectiveKeys(
        productId: ProductId,
        optionGroupId: OptionGroupId,
    ): List<OptionKey> =
        productOptionService
            .getEffectiveOptions(productId)
            .groups
            .single { it.optionGroupId == optionGroupId }
            .options
            .map { it.optionKey }
}
