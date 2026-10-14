package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.product.domain.product.Product
import com.dozycoffee.catalog.product.domain.product.ProductStatus
import com.dozycoffee.catalog.product.domain.product.StoreScope
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// 상품 이벤트의 JSON 직렬화(docs/events/product.md). 사내 MSA 이벤트 형식(헤더 없이 값 JSON 하나)을 따른다.
// 필드를 코드에 명시적으로 적어 클래스·프로퍼티 이름에 기대지 않는다. 저장된 이벤트와 구독자가 계약에 기대므로 키 이름은 바꾸지 않는다.
// 값이 null이면 필드를 보내지 않는다(sku가 없거나 traceparent가 없을 때).
object ProductEventJson {
    const val TOPIC = "dozy.catalog.product"
    const val SOURCE = "catalog"
    const val EVENT_VERSION = 1

    private val json = JsonNodeFactory.instance
    private val mapper = JsonMapper.builder().build()

    // 메시지 키는 productId의 문자열이다. 같은 상품의 이벤트는 같은 파티션에서 순서가 지켜진다.
    fun messageKey(product: Product): String = product.id.value.toString()

    fun state(
        eventId: UUID,
        type: ProductEventType,
        occurredAt: OffsetDateTime,
        traceparent: String?,
        product: Product,
    ): String = envelope(eventId, type, occurredAt, traceparent, stateData(product))

    // 삭제 이벤트는 상품 ID, 순번, 삭제 표시만 담는다. 순번은 삭제 직전 버전에 1을 더한 값이다.
    fun deleted(
        eventId: UUID,
        occurredAt: OffsetDateTime,
        traceparent: String?,
        product: Product,
    ): String =
        envelope(
            eventId,
            ProductEventType.DELETED,
            occurredAt,
            traceparent,
            json
                .objectNode()
                .put("productId", product.id.value)
                .put("sequence", product.version + 1)
                .put("deleted", true),
        )

    private fun envelope(
        eventId: UUID,
        type: ProductEventType,
        occurredAt: OffsetDateTime,
        traceparent: String?,
        data: ObjectNode,
    ): String {
        val node =
            json
                .objectNode()
                .put("eventId", eventId.toString())
                .put("eventType", type.wireName)
                .put("eventVersion", EVENT_VERSION)
                // 밀리초까지만 쓴다. 오프셋을 포함한 ISO-8601이고 Kafka 발행 시각이 아니라 아웃박스에 기록한 시각이다.
                .put("occurredAt", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(occurredAt.truncatedTo(ChronoUnit.MILLIS)))
                .put("source", SOURCE)
        if (traceparent != null) node.put("traceparent", traceparent)
        node.set("data", data)
        return mapper.writeValueAsString(node)
    }

    private fun stateData(product: Product): ObjectNode {
        val data = json.objectNode().put("productId", product.id.value)
        product.sku?.let { data.put("sku", it.value) }
        data
            .put("status", statusName(product.status))
            .set("storeScope", scope(product.storeScope))
        data
            .put("tracksInventory", product.tracksInventory)
            .put("sequence", product.version)
        return data
    }

    private fun statusName(status: ProductStatus): String =
        when (status) {
            ProductStatus.DRAFT -> "DRAFT"
            ProductStatus.ACTIVE -> "ACTIVE"
            ProductStatus.DISCONTINUED -> "DISCONTINUED"
        }

    // LIMITED는 대상 매장이 비어 있어도 targetStoreIds를 항상 보낸다(빈 LIMITED는 어떤 매장도 포함하지 않는다). ALL에는 없다.
    private fun scope(scope: StoreScope): ObjectNode =
        when (scope) {
            StoreScope.All -> json.objectNode().put("kind", "ALL")
            is StoreScope.Limited ->
                json.objectNode().put("kind", "LIMITED").also { node ->
                    val ids = node.putArray("targetStoreIds")
                    scope.targetStoreIds
                        .map { it.value }
                        .sorted()
                        .forEach { ids.add(it) }
                }
        }
}
