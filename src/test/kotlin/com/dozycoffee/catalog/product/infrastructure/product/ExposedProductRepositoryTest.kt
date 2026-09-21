package com.dozycoffee.catalog.product.infrastructure.product

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.core.VersionConflictException
import com.dozycoffee.catalog.fixture.exclude
import com.dozycoffee.catalog.fixture.priceOverride
import com.dozycoffee.catalog.product.domain.category.CategoryId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.product.domain.product.OptionOverride
import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.product.domain.product.ProductRepository
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.Sku
import com.dozycoffee.catalog.product.domain.product.StoreScope
import com.dozycoffee.catalog.product.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.support.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 준비 데이터(카테고리·태그·상품 그룹·옵션 그룹)는 검증 대상이 아니므로 SQL로 직접 넣는다.
// ID는 테스트마다 1부터 다시 매겨진다(IntegrationTest가 RESTART IDENTITY로 비움).
@DisplayName("ExposedProductRepository")
class ExposedProductRepositoryTest : IntegrationTest() {
    @Autowired
    private lateinit var tx: TransactionRunner

    @Autowired
    private lateinit var repository: ProductRepository

    @BeforeEach
    fun prepareReferences() =
        runTest {
            execute("INSERT INTO categories (name) VALUES ('음료')")
            execute("INSERT INTO categories (name, parent_category_id) VALUES ('커피', 1), ('차', 1)")
            execute("INSERT INTO tags (name) VALUES ('신메뉴'), ('베스트'), ('시즌한정')")
            execute("INSERT INTO product_groups (name) VALUES ('여름 기획'), ('원두 음료')")
            execute(
                "INSERT INTO option_groups (name, selection_type, required) " +
                    "VALUES ('사이즈', 'SINGLE', true), ('샷 추가', 'MULTI', false), ('온도', 'SINGLE', true)",
            )
            execute(
                "INSERT INTO options (option_group_id, option_key, name, price, display_order) VALUES " +
                    "(1, 'small', '스몰', 0, 0), (1, 'large', '라지', 1000, 1), " +
                    "(2, 'shot', '샷', 500, 0), (3, 'hot', '뜨거운', 0, 0), (3, 'iced', '차가운', 0, 1)",
            )
        }

    @Nested
    @DisplayName("등록과 조회")
    inner class RoundTrip {
        @Test
        fun `등록하면 생성된 ID와 버전 0을 돌려주고 DRAFT·ALL로 저장한다`() =
            runTest {
                val inserted = insert(newProduct(optionGroupIds = listOf(SIZE, SHOT)))

                assertEquals(ProductId(1), inserted.id)
                assertEquals(0L, inserted.version)
                val found = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })
                assertEquals(ProductStatus.DRAFT, found.status)
                assertEquals(StoreScope.All, found.storeScope)
                assertEquals(0L, found.version)
            }

        @Test
        fun `등록한 상품을 루트 필드·태그·그룹·옵션 그룹 연결 순서 그대로 복원한다`() =
            runTest {
                val inserted =
                    insert(
                        newProduct(
                            sku = Sku("AMR-001"),
                            description = "진한 에스프레소에 물을 더한 커피",
                            imageUrl = "https://cdn.example.com/americano.png",
                            tracksInventory = true,
                            tagIds = setOf(TagId(1), TagId(2)),
                            groupIds = setOf(ProductGroupId(2)),
                            optionGroupIds = listOf(TEMPERATURE, SIZE, SHOT),
                        ),
                    )

                val found = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })

                assertEquals(Sku("AMR-001"), found.sku)
                assertEquals("아메리카노", found.name)
                assertEquals(CategoryId(COFFEE), found.categoryId)
                assertEquals("진한 에스프레소에 물을 더한 커피", found.description)
                assertEquals("https://cdn.example.com/americano.png", found.imageUrl)
                assertEquals(Money(4500), found.basePrice)
                assertEquals(true, found.tracksInventory)
                assertEquals(setOf(TagId(1), TagId(2)), found.tagIds)
                assertEquals(setOf(ProductGroupId(2)), found.groupIds)
                assertEquals(listOf(TEMPERATURE to 0, SIZE to 1, SHOT to 2), found.linkSummary())
            }

        @Test
        fun `SKU·설명·이미지가 없으면 null로 복원한다`() =
            runTest {
                val inserted = insert(newProduct(sku = null, description = null, imageUrl = null))

                val found = assertNotNull(tx.inTransaction { repository.findById(inserted.id) })

                assertNull(found.sku)
                assertNull(found.description)
                assertNull(found.imageUrl)
                assertEquals(emptySet(), found.tagIds)
                assertEquals(emptyList(), found.optionGroupLinks)
            }

        @Test
        fun `판매 범위 LIMITED는 대상 매장과 함께 복원한다`() =
            runTest {
                val id = insert(newProduct()).id

                update(id) { it.changeStoreScope(StoreScope.Limited(setOf(StoreId(10), StoreId(20)))) }

                assertEquals(StoreScope.Limited(setOf(StoreId(10), StoreId(20))), find(id).storeScope)
            }

        @Test
        fun `대상 매장이 빈 LIMITED도 ALL과 구분해 복원한다`() =
            runTest {
                val id = insert(newProduct()).id

                update(id) { it.changeStoreScope(StoreScope.Limited(emptySet())) }

                assertEquals(StoreScope.Limited(emptySet()), find(id).storeScope)
            }

        @Test
        fun `옵션 가격 예외와 제외를 연결별로 복원한다`() =
            runTest {
                val id = insert(newProduct(optionGroupIds = listOf(SIZE, TEMPERATURE))).id

                update(id) {
                    it.overrideOptionPrice(SIZE, setOf(key("small"), key("large")), key("large"), Money(800))
                    it.excludeOption(TEMPERATURE, setOf(key("hot"), key("iced")), key("hot"))
                }

                val found = find(id)
                assertEquals(listOf(priceOverride("large", 800)), found.overridesOf(SIZE))
                assertEquals(listOf(exclude("hot")), found.overridesOf(TEMPERATURE))
            }

        @Test
        fun `잠금 조회도 같은 상품을 복원한다`() =
            runTest {
                val inserted = insert(newProduct(optionGroupIds = listOf(SIZE)))

                val found = tx.inTransaction { repository.findByIdForUpdate(inserted.id) }

                assertEquals(listOf(SIZE to 0), assertNotNull(found).linkSummary())
            }

        @Test
        fun `없는 상품은 null이다`() =
            runTest {
                assertNull(tx.inTransaction { repository.findById(ProductId(99)) })
            }
    }

    @Nested
    @DisplayName("하위 컬렉션 교체 저장")
    inner class ReplaceChildren {
        @Test
        fun `하위 컬렉션을 바꿔 저장하면 이전 행이 남지 않는다`() =
            runTest {
                val id =
                    insert(
                        newProduct(
                            tagIds = setOf(TagId(1), TagId(2)),
                            groupIds = setOf(ProductGroupId(1), ProductGroupId(2)),
                            optionGroupIds = listOf(SIZE, TEMPERATURE),
                        ),
                    ).id
                update(id) {
                    it.changeStoreScope(StoreScope.Limited(setOf(StoreId(10), StoreId(20))))
                    it.overrideOptionPrice(SIZE, setOf(key("small"), key("large")), key("large"), Money(800))
                    it.excludeOption(TEMPERATURE, setOf(key("hot"), key("iced")), key("hot"))
                }

                update(id) {
                    it.changeTags(setOf(TagId(3)))
                    it.changeGroups(emptySet())
                    it.changeStoreScope(StoreScope.Limited(setOf(StoreId(30))))
                    it.unlinkOptionGroup(TEMPERATURE)
                    it.linkOptionGroup(SHOT, displayOrder = 1)
                    it.removeOverride(SIZE, key("large"))
                }

                val found = find(id)
                assertEquals(setOf(TagId(3)), found.tagIds)
                assertEquals(emptySet(), found.groupIds)
                assertEquals(StoreScope.Limited(setOf(StoreId(30))), found.storeScope)
                assertEquals(listOf(SIZE to 0, SHOT to 1), found.linkSummary())
                assertEquals(emptyList(), found.overridesOf(SIZE))
                assertEquals(1L, count("SELECT count(*) FROM product_tags"))
                assertEquals(0L, count("SELECT count(*) FROM product_groups_map"))
                assertEquals(1L, count("SELECT count(*) FROM product_target_stores"))
                assertEquals(2L, count("SELECT count(*) FROM product_option_groups"))
                assertEquals(0L, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `판매 범위를 ALL로 바꾸면 대상 매장 행을 지운다`() =
            runTest {
                val id = insert(newProduct()).id
                update(id) { it.changeStoreScope(StoreScope.Limited(setOf(StoreId(10)))) }

                update(id) { it.changeStoreScope(StoreScope.All) }

                assertEquals(StoreScope.All, find(id).storeScope)
                assertEquals(0L, count("SELECT count(*) FROM product_target_stores"))
            }

        @Test
        fun `루트 필드와 상태 변경을 저장한다`() =
            runTest {
                val id = insert(newProduct()).id

                update(id) {
                    it.rename("콜드브루")
                    it.changeCategory(CategoryId(TEA))
                    it.changeDescription("차갑게 우려낸 커피")
                    it.changeImage("https://cdn.example.com/coldbrew.png")
                    it.changeBasePrice(Money(5000))
                    it.activate()
                }

                val found = find(id)
                assertEquals("콜드브루", found.name)
                assertEquals(CategoryId(TEA), found.categoryId)
                assertEquals("차갑게 우려낸 커피", found.description)
                assertEquals("https://cdn.example.com/coldbrew.png", found.imageUrl)
                assertEquals(Money(5000), found.basePrice)
                assertEquals(ProductStatus.ACTIVE, found.status)
            }
    }

    @Nested
    @DisplayName("낙관적 잠금")
    inner class OptimisticLocking {
        @Test
        fun `저장에 성공하면 DB와 객체의 버전이 1 오른다`() =
            runTest {
                val id = insert(newProduct()).id

                val saved = update(id) { it.rename("콜드브루") }

                assertEquals(1L, saved.version)
                assertEquals(1L, find(id).version)
            }

        @Test
        fun `오래된 버전으로 저장하면 충돌로 거부하고 DB 상태는 바뀌지 않는다`() =
            runTest {
                val id = insert(newProduct(tagIds = setOf(TagId(1)), optionGroupIds = listOf(SIZE))).id
                val stale = find(id)
                update(id) {
                    it.rename("콜드브루")
                    it.overrideOptionPrice(SIZE, setOf(key("small"), key("large")), key("large"), Money(800))
                }

                stale.rename("오래된 화면의 이름")
                stale.changeTags(setOf(TagId(2), TagId(3)))
                stale.changeStoreScope(StoreScope.Limited(setOf(StoreId(10))))
                stale.unlinkOptionGroup(SIZE)
                val exception = assertFailsWith<VersionConflictException> { tx.inTransaction { repository.save(stale) } }

                assertEquals(0L, exception.expectedVersion)
                assertEquals(0L, stale.version)
                val current = find(id)
                assertEquals("콜드브루", current.name)
                assertEquals(setOf(TagId(1)), current.tagIds)
                assertEquals(StoreScope.All, current.storeScope)
                assertEquals(listOf(priceOverride("large", 800)), current.overridesOf(SIZE))
                assertEquals(1L, current.version)
            }
    }

    @Nested
    @DisplayName("DB 제약")
    inner class Constraints {
        @Test
        fun `연결되지 않은 옵션 그룹의 예외는 DB가 거부한다`() =
            runTest {
                val id = insert(newProduct(optionGroupIds = listOf(SIZE))).id

                assertFails {
                    execute(
                        "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type) " +
                            "VALUES (${id.value}, ${SHOT.value}, 'shot', 'EXCLUDE')",
                    )
                }
                assertEquals(0L, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `PRICE 예외에 가격이 없으면 DB가 거부한다`() =
            runTest {
                val id = insert(newProduct(optionGroupIds = listOf(SIZE))).id

                assertFails {
                    execute(
                        "INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type) " +
                            "VALUES (${id.value}, ${SIZE.value}, 'large', 'PRICE')",
                    )
                }
                assertEquals(0L, count("SELECT count(*) FROM product_option_overrides"))
            }

        @Test
        fun `같은 SKU는 DB가 거부한다`() =
            runTest {
                insert(newProduct(sku = Sku("AMR-001")))

                assertFails { insert(newProduct(sku = Sku("AMR-001"))) }
                assertEquals(1L, count("SELECT count(*) FROM products"))
            }
    }

    @Nested
    @DisplayName("삭제")
    inner class Deletion {
        @Test
        fun `상품을 삭제하면 하위 데이터와 매장 설정이 함께 삭제된다`() =
            runTest {
                val id =
                    insert(
                        newProduct(
                            tagIds = setOf(TagId(1)),
                            groupIds = setOf(ProductGroupId(1)),
                            optionGroupIds = listOf(SIZE),
                        ),
                    ).id
                update(id) {
                    it.changeStoreScope(StoreScope.Limited(setOf(StoreId(10))))
                    it.excludeOption(SIZE, setOf(key("small"), key("large")), key("small"))
                }
                execute("INSERT INTO store_display_settings (store_id, product_id) VALUES (10, ${id.value})")
                execute(
                    "INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status) " +
                        "VALUES (10, ${id.value}, 'OWNER', 'SOLD_OUT')",
                )

                tx.inTransaction { repository.delete(id) }

                assertNull(tx.inTransaction { repository.findById(id) })
                listOf(
                    "product_target_stores",
                    "product_tags",
                    "product_groups_map",
                    "product_option_groups",
                    "product_option_overrides",
                    "store_display_settings",
                    "store_product_availabilities",
                ).forEach { table ->
                    assertEquals(0L, count("SELECT count(*) FROM $table"), "$table 행이 남았습니다")
                }
            }

        @Test
        fun `상품을 삭제해도 참조하던 카테고리·태그·그룹·옵션 그룹은 남는다`() =
            runTest {
                val id = insert(newProduct(tagIds = setOf(TagId(1)), groupIds = setOf(ProductGroupId(1)), optionGroupIds = listOf(SIZE))).id

                tx.inTransaction { repository.delete(id) }

                assertEquals(3L, count("SELECT count(*) FROM categories"))
                assertEquals(3L, count("SELECT count(*) FROM tags"))
                assertEquals(2L, count("SELECT count(*) FROM product_groups"))
                assertEquals(3L, count("SELECT count(*) FROM option_groups"))
            }
    }

    @Nested
    @DisplayName("여러 상품 조회")
    inner class FindAllByIds {
        @Test
        fun `주어진 ID의 상품을 상품 id 순으로 복원하고 없는 ID는 뺀다`() =
            runTest {
                val first = insert(newProduct(name = "첫째", tagIds = setOf(TagId(1)), optionGroupIds = listOf(SIZE))).id
                insert(newProduct(name = "둘째"))
                val third = insert(newProduct(name = "셋째", tagIds = setOf(TagId(2)))).id

                val found = tx.inTransaction { repository.findAllByIds(listOf(third, ProductId(99), first)) }

                assertEquals(listOf(first, third), found.map { it.id })
                assertEquals(setOf(TagId(1)), found[0].tagIds)
                assertEquals(listOf(SIZE to 0), found[0].linkSummary())
                assertEquals(setOf(TagId(2)), found[1].tagIds)
            }

        @Test
        fun `빈 목록이면 빈 목록이다`() =
            runTest {
                insert(newProduct())

                assertEquals(emptyList(), tx.inTransaction { repository.findAllByIds(emptyList()) })
            }
    }

    @Nested
    @DisplayName("옵션 그룹을 연결한 상품 잠금 조회")
    inner class FindAllLinkedToForUpdate {
        @Test
        fun `상태와 상관없이 옵션 그룹을 연결한 상품을 모두 돌려주고 연결하지 않은 상품은 뺀다`() =
            runTest {
                val draft = insert(newProduct(name = "초안", optionGroupIds = listOf(SIZE))).id
                val active = insert(newProduct(name = "판매중", optionGroupIds = listOf(SHOT, SIZE))).id
                val discontinued = insert(newProduct(name = "단종", optionGroupIds = listOf(SIZE))).id
                insert(newProduct(name = "다른 옵션 그룹", optionGroupIds = listOf(SHOT)))
                insert(newProduct(name = "연결 없음"))
                execute("UPDATE products SET status = 'ACTIVE' WHERE id = ${active.value}")
                execute("UPDATE products SET status = 'DISCONTINUED' WHERE id = ${discontinued.value}")

                val linked = tx.inTransaction { repository.findAllLinkedToForUpdate(SIZE) }

                assertEquals(listOf(draft, active, discontinued), linked.map { it.id })
                assertEquals(
                    listOf(ProductStatus.DRAFT, ProductStatus.ACTIVE, ProductStatus.DISCONTINUED),
                    linked.map { it.status },
                )
                assertEquals(listOf(SHOT to 0, SIZE to 1), linked[1].linkSummary())
            }

        @Test
        fun `여러 상품의 하위 컬렉션이 서로 섞이지 않는다`() =
            runTest {
                val first = insert(newProduct(name = "첫째", tagIds = setOf(TagId(1)), optionGroupIds = listOf(SIZE))).id
                val second = insert(newProduct(name = "둘째", tagIds = setOf(TagId(2)), optionGroupIds = listOf(SIZE))).id
                update(first) { it.overrideOptionPrice(SIZE, setOf(key("small"), key("large")), key("large"), Money(800)) }
                update(second) {
                    it.excludeOption(SIZE, setOf(key("small"), key("large")), key("small"))
                    it.changeStoreScope(StoreScope.Limited(setOf(StoreId(20))))
                }

                val linked = tx.inTransaction { repository.findAllLinkedToForUpdate(SIZE) }.associateBy { it.id }

                assertEquals(setOf(TagId(1)), linked.getValue(first).tagIds)
                assertEquals(listOf(priceOverride("large", 800)), linked.getValue(first).overridesOf(SIZE))
                assertEquals(StoreScope.All, linked.getValue(first).storeScope)
                assertEquals(setOf(TagId(2)), linked.getValue(second).tagIds)
                assertEquals(listOf(exclude("small")), linked.getValue(second).overridesOf(SIZE))
                assertEquals(StoreScope.Limited(setOf(StoreId(20))), linked.getValue(second).storeScope)
            }

        @Test
        fun `연결한 상품이 없으면 빈 목록이다`() =
            runTest {
                insert(newProduct(optionGroupIds = listOf(SIZE)))

                assertEquals(emptyList(), tx.inTransaction { repository.findAllLinkedToForUpdate(TEMPERATURE) })
            }
    }

    @Nested
    @DisplayName("매장에서 판매 가능한 상품 조회")
    inner class FindAllSellableAt {
        @Test
        fun `Active이고 판매 범위에 든 상품만 상품 id 순으로 가져온다`() =
            runTest {
                val active = insert(newProduct(name = "아메리카노"))
                update(active.id) { it.activate() }
                insert(newProduct(name = "신메뉴")) // DRAFT
                val discontinued = insert(newProduct(name = "단종 예정"))
                update(discontinued.id) {
                    it.activate()
                    it.discontinue()
                }
                val limited = insert(newProduct(name = "강남 한정"))
                update(limited.id) {
                    it.activate()
                    it.changeStoreScope(StoreScope.Limited(setOf(GANGNAM)))
                }

                assertEquals(
                    listOf(active.id, limited.id),
                    tx.inTransaction { repository.findAllSellableAt(GANGNAM) }.map { it.id },
                )
                assertEquals(
                    listOf(active.id),
                    tx.inTransaction { repository.findAllSellableAt(HONGDAE) }.map { it.id },
                )
            }

        @Test
        fun `대상 매장이 비어 있는 한정 판매 상품은 어느 매장에서도 나오지 않는다`() =
            runTest {
                val limited = insert(newProduct(name = "미배정 상품"))
                update(limited.id) {
                    it.activate()
                    it.changeStoreScope(StoreScope.Limited(emptySet()))
                }

                assertEquals(emptyList(), tx.inTransaction { repository.findAllSellableAt(GANGNAM) })
            }
    }

    @Nested
    @DisplayName("참조 여부 조회")
    inner class Exists {
        @Test
        fun `existsByCategory는 그 카테고리를 참조하는 상품이 있을 때만 참이다`() =
            runTest {
                insert(newProduct(categoryId = COFFEE))

                assertTrue(tx.inTransaction { repository.existsByCategory(CategoryId(COFFEE)) })
                assertFalse(tx.inTransaction { repository.existsByCategory(CategoryId(TEA)) })
            }

        @Test
        fun `existsLinkedTo는 그 옵션 그룹을 연결한 상품이 있을 때만 참이다`() =
            runTest {
                insert(newProduct(optionGroupIds = listOf(SIZE)))

                assertTrue(tx.inTransaction { repository.existsLinkedTo(SIZE) })
                assertFalse(tx.inTransaction { repository.existsLinkedTo(SHOT) })
            }
    }

    private suspend fun insert(newProduct: Product.NewProduct): Product = tx.inTransaction { repository.insert(newProduct) }

    private suspend fun find(id: ProductId): Product = assertNotNull(tx.inTransaction { repository.findById(id) })

    // 화면에서 불러와 바꾼 뒤 저장하는 흐름을 한 트랜잭션으로 흉내 낸다.
    private suspend fun update(
        id: ProductId,
        change: (Product) -> Unit,
    ): Product =
        tx.inTransaction {
            val product = assertNotNull(repository.findByIdForUpdate(id))
            change(product)
            repository.save(product)
        }

    private fun newProduct(
        name: String = "아메리카노",
        sku: Sku? = null,
        categoryId: Long = COFFEE,
        description: String? = null,
        imageUrl: String? = null,
        tracksInventory: Boolean = false,
        tagIds: Set<TagId> = emptySet(),
        groupIds: Set<ProductGroupId> = emptySet(),
        optionGroupIds: List<OptionGroupId> = emptyList(),
    ) = Product.NewProduct.of(
        sku = sku,
        name = name,
        categoryId = CategoryId(categoryId),
        description = description,
        imageUrl = imageUrl,
        basePrice = Money(4500),
        tracksInventory = tracksInventory,
        tagIds = tagIds,
        groupIds = groupIds,
        optionGroupIds = optionGroupIds,
    )

    private fun key(value: String) = OptionKey(value)

    // 연결은 옵션 그룹 ID로만 같음을 판단하므로 순서와 노출 순서를 값으로 비교한다.
    private fun Product.linkSummary(): List<Pair<OptionGroupId, Int>> = optionGroupLinks.map { it.id to it.displayOrder }

    private fun Product.overridesOf(optionGroupId: OptionGroupId): List<OptionOverride> =
        optionGroupLinks.single { it.id == optionGroupId }.overrides

    private companion object {
        const val COFFEE = 2L
        const val TEA = 3L
        val GANGNAM = StoreId(10)
        val HONGDAE = StoreId(20)
        val SIZE = OptionGroupId(1)
        val SHOT = OptionGroupId(2)
        val TEMPERATURE = OptionGroupId(3)
    }
}
