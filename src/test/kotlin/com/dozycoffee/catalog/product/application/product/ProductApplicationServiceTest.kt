package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.port.ProductEventPublisherPort
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.application.product.command.ReplaceProductCommand
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotAssignableException
import com.dozycoffee.catalog.product.domain.category.exception.CategoryNotFoundException
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.exception.OptionGroupNotFoundException
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.event.ProductActivated
import com.dozycoffee.catalog.product.domain.product.event.ProductDiscontinued
import com.dozycoffee.catalog.product.domain.product.exception.DuplicateOptionGroupLinkException
import com.dozycoffee.catalog.product.domain.product.exception.InvalidProductStatusTransitionException
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotDeletableException
import com.dozycoffee.catalog.product.domain.product.exception.ProductNotFoundException
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.productgroup.exception.ProductGroupNotFoundException
import com.dozycoffee.catalog.support.ApplicationTest
import com.dozycoffee.catalog.support.RecordingProductEventPublisher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("상품 등록·수정·상태 전환·삭제 (요구사항 1.2, 1.3, 1.11)")
class ProductApplicationServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var service: ProductApplicationService

    @Autowired
    private lateinit var categoryService: CategoryApplicationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var eventPublisher: ProductEventPublisherPort

    private lateinit var coffee: ChildCategory

    @BeforeEach
    fun setUpCategory() =
        runTest {
            recorder().clear()
            val beverage = categoryService.registerTopLevel("음료")
            coffee = categoryService.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
        }

    @Nested
    @DisplayName("등록")
    inner class Registration {
        @Test
        fun `등록하면 Draft 상태로 생성되고 SKU가 부여된다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))

                val found = assertNotNull(tx { productRepository.findById(product.id) })
                assertEquals(ProductStatus.DRAFT, found.status)
                assertEquals("아메리카노", found.name)
                assertEquals(coffee.id, found.categoryId)
                val sku = assertNotNull(found.sku)
                assertTrue(sku.value.matches(Regex("""DZ-\d{8}""")), "SKU 형식이 다릅니다: ${sku.value}")
            }

        @Test
        fun `상품마다 다른 SKU가 부여된다`() =
            runTest {
                val first = service.register(command(name = "아메리카노"))
                val second = service.register(command(name = "라떼"))

                assertTrue(first.sku != second.sku)
                assertEquals(2, count("SELECT count(DISTINCT sku) FROM products"))
            }

        @Test
        fun `같은 이름의 태그는 재사용하고 없는 이름은 새로 만든다`() =
            runTest {
                service.register(command(name = "아메리카노", tagNames = listOf("신메뉴")))

                val second = service.register(command(name = "라떼", tagNames = listOf("신메뉴", "베스트")))

                assertEquals(2, count("SELECT count(*) FROM tags"))
                assertEquals(2, second.tagIds.size)
            }

        @Test
        fun `대분류를 카테고리로 지정하면 거부한다`() =
            runTest {
                val dessert = categoryService.registerTopLevel("디저트")

                assertFailsWith<CategoryNotAssignableException> {
                    service.register(command(name = "아메리카노", categoryId = dessert.id))
                }
                assertEquals(0, count("SELECT count(*) FROM products"))
            }

        @Test
        fun `없는 카테고리를 지정하면 거부한다`() =
            runTest {
                assertFailsWith<CategoryNotFoundException> {
                    service.register(command(name = "아메리카노", categoryId = CategoryId(999)))
                }
            }

        @Test
        fun `같은 옵션 그룹을 두 번 연결하면 거부한다`() =
            runTest {
                val optionGroupId = insertOptionGroup()

                assertFailsWith<DuplicateOptionGroupLinkException> {
                    service.register(command(name = "아메리카노", optionGroupIds = listOf(optionGroupId, optionGroupId)))
                }
                assertEquals(0, count("SELECT count(*) FROM products"))
            }

        @Test
        fun `없는 옵션 그룹이나 상품 그룹을 지정하면 거부한다`() =
            runTest {
                assertFailsWith<OptionGroupNotFoundException> {
                    service.register(command(name = "아메리카노", optionGroupIds = listOf(OptionGroupId(999))))
                }
                assertFailsWith<ProductGroupNotFoundException> {
                    service.register(command(name = "아메리카노", groupIds = setOf(ProductGroupId(999))))
                }
            }
    }

    @Nested
    @DisplayName("즉시 반영 (PUT)")
    inner class Replacement {
        @Test
        fun `입력한 값 전체로 교체한다`() =
            runTest {
                val product = service.register(command(name = "아메리카노", tagNames = listOf("신메뉴")))
                val dessert = categoryService.registerTopLevel("디저트")
                val cake = categoryService.registerChild(RegisterChildCategoryCommand(dessert.id, "케이크"))

                service.replace(
                    ReplaceProductCommand(
                        productId = product.id,
                        version = product.version,
                        name = "치즈케이크",
                        categoryId = cake.id,
                        basePrice = Money(6500),
                        description = "부드러운 케이크",
                        tagNames = listOf("베스트"),
                    ),
                )

                val found = assertNotNull(tx { productRepository.findById(product.id) })
                assertEquals("치즈케이크", found.name)
                assertEquals(cake.id, found.categoryId)
                assertEquals(Money(6500), found.basePrice)
                assertEquals("부드러운 케이크", found.description)
                assertEquals(1, found.tagIds.size)
                assertEquals(product.sku, found.sku)
            }

        @Test
        fun `오래된 버전으로 교체하면 거부하고 값을 바꾸지 않는다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.replace(replaceCommand(product.id, product.version, name = "아메리카노 v2"))

                assertFailsWith<VersionConflictException> {
                    service.replace(replaceCommand(product.id, product.version, name = "아메리카노 v3"))
                }
                assertEquals("아메리카노 v2", tx { productRepository.findById(product.id) }?.name)
            }

        @Test
        fun `단종 상태에서도 상태 외 정보를 수정할 수 있다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.activate(product.id)
                val discontinued = service.discontinue(product.id)

                service.replace(replaceCommand(product.id, discontinued.version, name = "아메리카노(단종)"))

                val found = assertNotNull(tx { productRepository.findById(product.id) })
                assertEquals("아메리카노(단종)", found.name)
                assertEquals(ProductStatus.DISCONTINUED, found.status)
            }
    }

    @Nested
    @DisplayName("상태 전환")
    inner class StatusTransition {
        @Test
        fun `활성화하면 Active가 되고 이벤트가 발행된다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))

                service.activate(product.id)

                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product.id) }?.status)
                assertIs<ProductActivated>(recorder().published.single())
            }

        @Test
        fun `단종하면 Discontinued가 되고 이벤트가 발행된다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.activate(product.id)
                recorder().clear()

                service.discontinue(product.id)

                assertEquals(ProductStatus.DISCONTINUED, tx { productRepository.findById(product.id) }?.status)
                assertIs<ProductDiscontinued>(recorder().published.single())
            }

        @Test
        fun `단종된 상품을 다시 활성화할 수 있다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.activate(product.id)
                service.discontinue(product.id)

                service.activate(product.id)

                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product.id) }?.status)
            }

        @Test
        fun `이미 Active인 상품을 활성화하면 거부한다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.activate(product.id)
                recorder().clear()

                assertFailsWith<InvalidProductStatusTransitionException> { service.activate(product.id) }
                assertEquals(ProductStatus.ACTIVE, tx { productRepository.findById(product.id) }?.status)
                assertTrue(recorder().published.isEmpty())
            }

        @Test
        fun `Draft 상품을 단종하면 거부한다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))

                assertFailsWith<InvalidProductStatusTransitionException> { service.discontinue(product.id) }
                assertEquals(ProductStatus.DRAFT, tx { productRepository.findById(product.id) }?.status)
            }

        @Test
        fun `없는 상품을 활성화하면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.activate(ProductId(999)) }
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `Draft 상품을 삭제하면 연결도 함께 사라진다`() =
            runTest {
                val optionGroupId = insertOptionGroup()
                val product =
                    service.register(
                        command(name = "아메리카노", tagNames = listOf("신메뉴"), optionGroupIds = listOf(optionGroupId)),
                    )

                service.delete(product.id)

                assertNull(tx { productRepository.findById(product.id) })
                assertEquals(0, count("SELECT count(*) FROM product_tags"))
                assertEquals(0, count("SELECT count(*) FROM product_option_groups"))
                assertEquals(1, count("SELECT count(*) FROM tags"))
                assertEquals(1, count("SELECT count(*) FROM option_groups"))
            }

        @Test
        fun `Active와 Discontinued 상품은 삭제할 수 없다`() =
            runTest {
                val product = service.register(command(name = "아메리카노"))
                service.activate(product.id)

                assertFailsWith<ProductNotDeletableException> { service.delete(product.id) }

                service.discontinue(product.id)
                assertFailsWith<ProductNotDeletableException> { service.delete(product.id) }
                assertEquals(1, count("SELECT count(*) FROM products"))
            }

        @Test
        fun `없는 상품을 삭제하면 거부한다`() =
            runTest {
                assertFailsWith<ProductNotFoundException> { service.delete(ProductId(999)) }
            }
    }

    private fun recorder() = eventPublisher as RecordingProductEventPublisher

    private fun command(
        name: String,
        categoryId: CategoryId = coffee.id,
        tagNames: List<String> = emptyList(),
        groupIds: Set<ProductGroupId> = emptySet(),
        optionGroupIds: List<OptionGroupId> = emptyList(),
    ) = RegisterProductCommand(
        name = name,
        categoryId = categoryId,
        basePrice = Money(4500),
        tracksInventory = false,
        tagNames = tagNames,
        groupIds = groupIds,
        optionGroupIds = optionGroupIds,
    )

    private fun replaceCommand(
        productId: ProductId,
        version: Long,
        name: String,
    ) = ReplaceProductCommand(
        productId = productId,
        version = version,
        name = name,
        categoryId = coffee.id,
        basePrice = Money(4500),
    )

    private suspend fun insertOptionGroup(): OptionGroupId {
        execute("INSERT INTO option_groups (name, selection_type, required) VALUES ('사이즈', 'SINGLE', true)")
        execute(
            "INSERT INTO options (option_group_id, option_key, name, price, display_order) " +
                "VALUES ((SELECT max(id) FROM option_groups), 'TALL', '톨', 0, 0)",
        )
        return OptionGroupId(count("SELECT max(id) FROM option_groups"))
    }
}
