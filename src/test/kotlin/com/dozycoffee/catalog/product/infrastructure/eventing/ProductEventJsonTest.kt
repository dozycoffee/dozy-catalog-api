package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.fixture.product
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// docs/events/product.md의 계약대로 직렬화되는지 확인한다. 구독자가 이 JSON에 기대므로 키와 모양을 그대로 고정한다.
@DisplayName("상품 이벤트 JSON (docs/events/product.md)")
class ProductEventJsonTest {
    private val mapper = JsonMapper.builder().build()
    private val eventId = UUID.fromString("0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f")
    private val occurredAt = OffsetDateTime.parse("2026-10-14T10:00:00.123456789+09:00")
    private val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

    private fun state(
        product: com.dozycoffee.catalog.product.domain.product.Product,
        type: ProductEventType = ProductEventType.STORE_SCOPE_CHANGED,
        traceparent: String? = this.traceparent,
    ): JsonNode = mapper.readTree(ProductEventJson.state(eventId, type, occurredAt, traceparent, product))

    @Test
    fun `계약 문서의 예시와 같은 모양이다`() {
        val product =
            product(
                id = 1024,
                sku = "DZ-00001024",
                status = ProductStatus.ACTIVE,
                storeScope = StoreScope.Limited(setOf(StoreId(107), StoreId(12), StoreId(35))),
                version = 42,
            )

        val expected =
            mapper.readTree(
                """
                {
                  "eventId": "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f",
                  "eventType": "PRODUCT_STORE_SCOPE_CHANGED",
                  "eventVersion": 1,
                  "occurredAt": "2026-10-14T10:00:00.123+09:00",
                  "source": "catalog",
                  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                  "data": {
                    "productId": 1024,
                    "sku": "DZ-00001024",
                    "status": "ACTIVE",
                    "storeScope": { "kind": "LIMITED", "targetStoreIds": [12, 35, 107] },
                    "tracksInventory": false,
                    "sequence": 42
                  }
                }
                """.trimIndent(),
            )
        assertEquals(expected, state(product))
    }

    @Test
    fun `메시지 키는 상품 ID 문자열이다`() {
        assertEquals("1024", ProductEventJson.messageKey(product(id = 1024)))
    }

    @Test
    fun `SKU가 없으면 필드를 보내지 않는다`() {
        val data = state(product(sku = null))["data"]

        assertFalse(data.has("sku"))
    }

    @Test
    fun `traceparent가 없으면 필드를 보내지 않는다`() {
        assertFalse(state(product(), traceparent = null).has("traceparent"))
    }

    @Test
    fun `전체 판매 범위에는 대상 매장이 없다`() {
        val scope = state(product(storeScope = StoreScope.All))["data"]["storeScope"]

        assertEquals("ALL", scope["kind"].asString())
        assertFalse(scope.has("targetStoreIds"))
    }

    @Test
    fun `대상 매장이 빈 한정 판매는 빈 배열을 보낸다`() {
        val scope = state(product(storeScope = StoreScope.Limited(emptySet())))["data"]["storeScope"]

        assertEquals("LIMITED", scope["kind"].asString())
        assertTrue(scope["targetStoreIds"].isArray)
        assertEquals(0, scope["targetStoreIds"].size())
    }

    @Test
    fun `상태와 재고 추적 여부를 그대로 담는다`() {
        val data = state(product(status = ProductStatus.DISCONTINUED, tracksInventory = true))["data"]

        assertEquals("DISCONTINUED", data["status"].asString())
        assertTrue(data["tracksInventory"].asBoolean())
    }

    @Test
    fun `종류마다 계약의 이벤트 이름을 쓴다`() {
        assertEquals(
            listOf(
                "PRODUCT_REGISTERED",
                "PRODUCT_ACTIVATED",
                "PRODUCT_DISCONTINUED",
                "PRODUCT_STORE_SCOPE_CHANGED",
                "PRODUCT_DELETED",
            ),
            ProductEventType.entries.map { it.wireName },
        )
    }

    @Test
    fun `삭제 이벤트는 상품 ID와 순번과 삭제 표시만 담고 순번은 직전 버전에 1을 더한다`() {
        val json = mapper.readTree(ProductEventJson.deleted(eventId, occurredAt, traceparent, product(id = 7, version = 3)))

        assertEquals("PRODUCT_DELETED", json["eventType"].asString())
        val data = json["data"]
        assertEquals(setOf("productId", "sequence", "deleted"), data.propertyNames().toSet())
        assertEquals(7, data["productId"].asLong())
        assertEquals(4, data["sequence"].asLong())
        assertTrue(data["deleted"].asBoolean())
    }
}
