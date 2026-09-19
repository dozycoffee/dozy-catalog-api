package com.dozycoffee.catalog.infrastructure.persistence.product

import com.dozycoffee.catalog.domain.category.CategoryId
import com.dozycoffee.catalog.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.domain.optiongroup.OptionKey
import com.dozycoffee.catalog.domain.product.ProductRepository
import com.dozycoffee.catalog.domain.product.model.OptionOverride
import com.dozycoffee.catalog.domain.product.model.Product
import com.dozycoffee.catalog.domain.product.model.ProductId
import com.dozycoffee.catalog.domain.product.model.ProductOptionGroupLink
import com.dozycoffee.catalog.domain.product.model.ProductStatus
import com.dozycoffee.catalog.domain.product.model.Sku
import com.dozycoffee.catalog.domain.product.model.StoreScope
import com.dozycoffee.catalog.domain.productgroup.ProductGroupId
import com.dozycoffee.catalog.domain.shared.Money
import com.dozycoffee.catalog.domain.shared.StoreId
import com.dozycoffee.catalog.domain.shared.VersionConflictException
import com.dozycoffee.catalog.domain.tag.TagId
import com.dozycoffee.catalog.infrastructure.persistence.DbNow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedProductRepository : ProductRepository {
    override suspend fun findById(id: ProductId): Product? = findOne(selectRoots { ProductsTable.id eq id.value })

    // 상태 전이와 예약 적용의 검증과 쓰기 사이에 다른 변경이 끼어들지 않도록 루트 행을 잠근다(ERD 동시성 처리).
    override suspend fun findByIdForUpdate(id: ProductId): Product? = findOne(selectRoots { ProductsTable.id eq id.value }.forUpdate())

    // 여러 상품을 잠글 때 교착을 피하도록 항상 id 순서로 잠근다.
    override suspend fun findAllLinkedToForUpdate(optionGroupId: OptionGroupId): List<Product> {
        val linkedProductIds =
            ProductOptionGroupsTable
                .select(ProductOptionGroupsTable.productId)
                .where { ProductOptionGroupsTable.optionGroupId eq optionGroupId.value }
        return toProducts(
            selectRoots { ProductsTable.id inSubQuery linkedProductIds }
                .orderBy(ProductsTable.id to SortOrder.ASC)
                .forUpdate()
                .toList(),
        )
    }

    override suspend fun existsByCategory(categoryId: CategoryId): Boolean =
        ProductsTable
            .select(ProductsTable.id)
            .where { ProductsTable.categoryId eq categoryId.value }
            .limit(1)
            .firstOrNull() != null

    override suspend fun existsLinkedTo(optionGroupId: OptionGroupId): Boolean =
        ProductOptionGroupsTable
            .select(ProductOptionGroupsTable.productId)
            .where { ProductOptionGroupsTable.optionGroupId eq optionGroupId.value }
            .limit(1)
            .firstOrNull() != null

    override suspend fun insert(newProduct: Product.NewProduct): Product {
        val id =
            ProductsTable.insert {
                it[sku] = newProduct.sku?.value
                it[name] = newProduct.name
                it[categoryId] = newProduct.categoryId.value
                it[description] = newProduct.description
                it[imageUrl] = newProduct.imageUrl
                it[basePrice] = newProduct.basePrice.amount
                it[status] = ProductStatus.DRAFT.name
                it[storeScope] = STORE_SCOPE_ALL
                it[tracksInventory] = newProduct.tracksInventory
            }[ProductsTable.id]
        val product =
            Product(
                id = ProductId(id),
                sku = newProduct.sku,
                name = newProduct.name,
                categoryId = newProduct.categoryId,
                description = newProduct.description,
                imageUrl = newProduct.imageUrl,
                basePrice = newProduct.basePrice,
                tracksInventory = newProduct.tracksInventory,
                tagIds = newProduct.tagIds,
                groupIds = newProduct.groupIds,
                optionGroupLinks =
                    newProduct.optionGroupIds.mapIndexed { index, optionGroupId ->
                        ProductOptionGroupLink(optionGroupId, displayOrder = index)
                    },
                status = ProductStatus.DRAFT,
                storeScope = StoreScope.All,
                version = 0,
            )
        insertChildren(product)
        return product
    }

    // 루트는 읽었을 때의 버전과 같을 때만 저장한다(docs/adr/0013). 바뀐 행이 없으면 그 사이 다른 트랜잭션이
    // 저장한 것이므로 하위 컬렉션도 건드리지 않고 충돌로 거부한다.
    override suspend fun save(product: Product): Product {
        val id = product.id.value
        val updated =
            ProductsTable.update({ (ProductsTable.id eq id) and (ProductsTable.version eq product.version) }) {
                it.setRoot(product)
                it[version] = ProductsTable.version + 1
                it[updatedAt] = DbNow
            }
        if (updated == 0) {
            throw VersionConflictException(product.id, product.version, currentVersion = null)
        }
        deleteChildren(id)
        insertChildren(product)
        product.version += 1
        return product
    }

    override suspend fun delete(id: ProductId) {
        ProductsTable.deleteWhere { ProductsTable.id eq id.value }
    }

    private fun selectRoots(where: () -> Op<Boolean>): Query = ProductsTable.selectAll().where(where)

    private suspend fun findOne(query: Query): Product? = query.firstOrNull()?.let { toProducts(listOf(it)).single() }

    private fun UpdateBuilder<*>.setRoot(product: Product) {
        this[ProductsTable.sku] = product.sku?.value
        this[ProductsTable.name] = product.name
        this[ProductsTable.categoryId] = product.categoryId.value
        this[ProductsTable.description] = product.description
        this[ProductsTable.imageUrl] = product.imageUrl
        this[ProductsTable.basePrice] = product.basePrice.amount
        this[ProductsTable.status] = product.status.name
        this[ProductsTable.storeScope] =
            when (product.storeScope) {
                StoreScope.All -> STORE_SCOPE_ALL
                is StoreScope.Limited -> STORE_SCOPE_LIMITED
            }
        this[ProductsTable.tracksInventory] = product.tracksInventory
    }

    // 예외는 연결을 복합 FK로 참조하므로 연결보다 먼저 지운다(연결을 지우면 CASCADE로도 지워진다).
    private suspend fun deleteChildren(productId: Long) {
        ProductOptionOverridesTable.deleteWhere { ProductOptionOverridesTable.productId eq productId }
        ProductOptionGroupsTable.deleteWhere { ProductOptionGroupsTable.productId eq productId }
        ProductTargetStoresTable.deleteWhere { ProductTargetStoresTable.productId eq productId }
        ProductTagsTable.deleteWhere { ProductTagsTable.productId eq productId }
        ProductGroupsMapTable.deleteWhere { ProductGroupsMapTable.productId eq productId }
    }

    private suspend fun insertChildren(product: Product) {
        val productId = product.id.value
        val targetStoreIds = (product.storeScope as? StoreScope.Limited)?.targetStoreIds.orEmpty()
        ProductTargetStoresTable.batchInsert(targetStoreIds, shouldReturnGeneratedValues = false) { storeId ->
            this[ProductTargetStoresTable.productId] = productId
            this[ProductTargetStoresTable.storeId] = storeId.value
        }
        ProductTagsTable.batchInsert(product.tagIds, shouldReturnGeneratedValues = false) { tagId ->
            this[ProductTagsTable.productId] = productId
            this[ProductTagsTable.tagId] = tagId.value
        }
        ProductGroupsMapTable.batchInsert(product.groupIds, shouldReturnGeneratedValues = false) { groupId ->
            this[ProductGroupsMapTable.productId] = productId
            this[ProductGroupsMapTable.groupId] = groupId.value
        }
        ProductOptionGroupsTable.batchInsert(product.optionGroupLinks, shouldReturnGeneratedValues = false) { link ->
            this[ProductOptionGroupsTable.productId] = productId
            this[ProductOptionGroupsTable.optionGroupId] = link.id.value
            this[ProductOptionGroupsTable.displayOrder] = link.displayOrder
        }
        val overrides = product.optionGroupLinks.flatMap { link -> link.overrides.map { link.id to it } }
        ProductOptionOverridesTable.batchInsert(overrides, shouldReturnGeneratedValues = false) { (optionGroupId, override) ->
            this[ProductOptionOverridesTable.productId] = productId
            this[ProductOptionOverridesTable.optionGroupId] = optionGroupId.value
            this[ProductOptionOverridesTable.optionKey] = override.optionKey.value
            when (override) {
                is OptionOverride.Price -> {
                    this[ProductOptionOverridesTable.overrideType] = OVERRIDE_PRICE
                    this[ProductOptionOverridesTable.price] = override.price.amount
                }
                is OptionOverride.Exclude -> {
                    this[ProductOptionOverridesTable.overrideType] = OVERRIDE_EXCLUDE
                    this[ProductOptionOverridesTable.price] = null
                }
            }
        }
    }

    // 하위 테이블은 상품 수와 상관없이 테이블마다 한 번씩만 조회한다.
    private suspend fun toProducts(roots: List<ResultRow>): List<Product> {
        if (roots.isEmpty()) return emptyList()
        val ids = roots.map { it[ProductsTable.id] }
        val targetStores =
            ProductTargetStoresTable
                .selectAll()
                .where { ProductTargetStoresTable.productId inList ids }
                .toList()
                .groupBy({ it[ProductTargetStoresTable.productId] }, { StoreId(it[ProductTargetStoresTable.storeId]) })
        val tags =
            ProductTagsTable
                .selectAll()
                .where { ProductTagsTable.productId inList ids }
                .toList()
                .groupBy({ it[ProductTagsTable.productId] }, { TagId(it[ProductTagsTable.tagId]) })
        val groups =
            ProductGroupsMapTable
                .selectAll()
                .where { ProductGroupsMapTable.productId inList ids }
                .toList()
                .groupBy({ it[ProductGroupsMapTable.productId] }, { ProductGroupId(it[ProductGroupsMapTable.groupId]) })
        val overrides =
            ProductOptionOverridesTable
                .selectAll()
                .where { ProductOptionOverridesTable.productId inList ids }
                .orderBy(ProductOptionOverridesTable.optionKey)
                .toList()
                .groupBy(
                    { it[ProductOptionOverridesTable.productId] to it[ProductOptionOverridesTable.optionGroupId] },
                    { it.toOptionOverride() },
                )
        val links =
            ProductOptionGroupsTable
                .selectAll()
                .where { ProductOptionGroupsTable.productId inList ids }
                .orderBy(ProductOptionGroupsTable.displayOrder to SortOrder.ASC, ProductOptionGroupsTable.optionGroupId to SortOrder.ASC)
                .toList()
                .groupBy({ it[ProductOptionGroupsTable.productId] }) { row ->
                    val productId = row[ProductOptionGroupsTable.productId]
                    val optionGroupId = row[ProductOptionGroupsTable.optionGroupId]
                    ProductOptionGroupLink(
                        OptionGroupId(optionGroupId),
                        displayOrder = row[ProductOptionGroupsTable.displayOrder],
                        overrides = overrides[productId to optionGroupId].orEmpty(),
                    )
                }
        return roots.map { row ->
            val id = row[ProductsTable.id]
            Product(
                id = ProductId(id),
                sku = row[ProductsTable.sku]?.let(::Sku),
                name = row[ProductsTable.name],
                categoryId = CategoryId(row[ProductsTable.categoryId]),
                description = row[ProductsTable.description],
                imageUrl = row[ProductsTable.imageUrl],
                basePrice = Money(row[ProductsTable.basePrice]),
                tracksInventory = row[ProductsTable.tracksInventory],
                tagIds = tags[id].orEmpty().toSet(),
                groupIds = groups[id].orEmpty().toSet(),
                optionGroupLinks = links[id].orEmpty(),
                status = ProductStatus.valueOf(row[ProductsTable.status]),
                storeScope =
                    when (val scope = row[ProductsTable.storeScope]) {
                        STORE_SCOPE_ALL -> StoreScope.All
                        STORE_SCOPE_LIMITED -> StoreScope.Limited(targetStores[id].orEmpty().toSet())
                        else -> error("알 수 없는 판매 범위: $scope")
                    },
                version = row[ProductsTable.version],
            )
        }
    }

    private fun ResultRow.toOptionOverride(): OptionOverride {
        val optionKey = OptionKey(this[ProductOptionOverridesTable.optionKey])
        return when (val type = this[ProductOptionOverridesTable.overrideType]) {
            OVERRIDE_PRICE -> OptionOverride.Price(optionKey, Money(checkNotNull(this[ProductOptionOverridesTable.price])))
            OVERRIDE_EXCLUDE -> OptionOverride.Exclude(optionKey)
            else -> error("알 수 없는 옵션 예외 종류: $type")
        }
    }

    private companion object {
        const val STORE_SCOPE_ALL = "ALL"
        const val STORE_SCOPE_LIMITED = "LIMITED"
        const val OVERRIDE_PRICE = "PRICE"
        const val OVERRIDE_EXCLUDE = "EXCLUDE"
    }
}
