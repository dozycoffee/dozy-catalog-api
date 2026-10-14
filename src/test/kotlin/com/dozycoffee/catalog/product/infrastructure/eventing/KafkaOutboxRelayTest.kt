package com.dozycoffee.catalog.product.infrastructure.eventing

import com.dozycoffee.catalog.core.Money
import com.dozycoffee.catalog.product.application.category.CategoryApplicationService
import com.dozycoffee.catalog.product.application.category.command.RegisterChildCategoryCommand
import com.dozycoffee.catalog.product.application.product.ProductApplicationService
import com.dozycoffee.catalog.product.application.product.command.RegisterProductCommand
import com.dozycoffee.catalog.product.domain.category.ChildCategory
import com.dozycoffee.catalog.support.ApplicationTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 실제 Kafka로 확인하는 상품 이벤트 발행의 왕복(docs/adr/0021, docs/events/product.md).
// 릴레이를 켜고 Testcontainers의 Kafka를 연결해, 상품을 바꾸면 메시지가 같은 키로 순서대로 도착하는지 본다.
// 릴레이가 켜진 컨텍스트가 캐시에 남아 다른 테스트의 아웃박스 행을 가져가지 않도록 끝나면 컨텍스트를 닫는다.
@DisplayName("상품 이벤트 발행 (Kafka)")
@TestPropertySource(properties = ["catalog.outbox.relay.enabled=true", "catalog.outbox.relay.fixed-delay=PT0.1S"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaOutboxRelayTest : ApplicationTest() {
    @Autowired
    private lateinit var products: ProductApplicationService

    @Autowired
    private lateinit var categories: CategoryApplicationService

    private val mapper = JsonMapper.builder().build()
    private lateinit var coffee: ChildCategory
    private lateinit var consumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setUp() {
        consumer =
            KafkaConsumer(
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ),
                StringDeserializer(),
                StringDeserializer(),
            )
        consumer.subscribe(listOf(ProductEventJson.TOPIC))
        // 토픽은 테스트끼리 공유되므로 앞 테스트가 남긴 메시지를 건너뛰고 지금부터의 메시지만 읽는다.
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) consumer.poll(Duration.ofMillis(200))
        consumer.seekToEnd(consumer.assignment())
        consumer.assignment().forEach { consumer.position(it) }
        runTest {
            val beverage = categories.registerTopLevel("음료")
            coffee = categories.registerChild(RegisterChildCategoryCommand(beverage.id, "커피"))
        }
    }

    @AfterEach
    fun tearDown() = consumer.close()

    private data class Received(
        val key: String,
        val json: JsonNode,
    )

    // 기대한 건수가 올 때까지 읽는다. 기대보다 더 오면(중복 전송 등) 그대로 모두 돌려준다.
    private suspend fun receive(
        expected: Int,
        extraWait: Duration = Duration.ZERO,
    ): List<Received> {
        val received = mutableListOf<Received>()
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (received.size < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach { received += Received(it.key(), mapper.readTree(it.value())) }
        }
        if (!extraWait.isZero) {
            val until = System.nanoTime() + extraWait.toNanos()
            while (System.nanoTime() < until) {
                consumer.poll(Duration.ofMillis(200)).forEach { received += Received(it.key(), mapper.readTree(it.value())) }
            }
        }
        return received
    }

    private fun registerCommand(name: String) =
        RegisterProductCommand(
            name = name,
            categoryId = coffee.id,
            basePrice = Money(4500),
            tracksInventory = false,
            tagNames = emptyList(),
            groupIds = emptySet(),
            optionGroupIds = emptyList(),
        )

    @Test
    fun `상품을 바꾸면 같은 키로 순서대로 도착하고 전송 완료로 기록된다`() =
        runTest {
            val product = products.register(registerCommand("아메리카노"))
            products.activate(product.id)
            products.discontinue(product.id)

            val messages = receive(3)

            assertEquals(3, messages.size)
            assertEquals(setOf(product.id.value.toString()), messages.map { it.key }.toSet())
            assertEquals(
                listOf("PRODUCT_REGISTERED", "PRODUCT_ACTIVATED", "PRODUCT_DISCONTINUED"),
                messages.map { it.json["eventType"].asString() },
            )
            assertEquals(listOf(0L, 1L, 2L), messages.map { it.json["data"]["sequence"].asLong() })
            assertEquals(listOf("DRAFT", "ACTIVE", "DISCONTINUED"), messages.map { it.json["data"]["status"].asString() })
            assertTrue(messages.all { it.json["source"].asString() == "catalog" && it.json["eventVersion"].asInt() == 1 })
            waitUntilAllPublished()
        }

    @Test
    fun `삭제하면 삭제 이벤트가 도착한다`() =
        runTest {
            val product = products.register(registerCommand("아메리카노"))
            products.delete(product.id)

            val messages = receive(2)

            assertEquals(listOf("PRODUCT_REGISTERED", "PRODUCT_DELETED"), messages.map { it.json["eventType"].asString() })
            assertEquals(listOf(0L, 1L), messages.map { it.json["data"]["sequence"].asLong() })
            assertTrue(messages.last().json["data"]["deleted"].asBoolean())
        }

    @Test
    fun `같은 이벤트를 다시 보내면 같은 eventId가 중복으로 도착한다`() =
        runTest {
            val product = products.register(registerCommand("아메리카노"))
            val first = receive(1).single()
            waitUntilAllPublished()

            // 전송은 했지만 완료 기록 전에 죽은 상황: 완료 표시를 지워 릴레이가 다시 보내게 한다.
            execute("UPDATE outbox_events SET published_at = NULL")

            val duplicates = receive(1)

            assertEquals(1, duplicates.size)
            assertEquals(first.json["eventId"].asString(), duplicates.single().json["eventId"].asString())
            assertEquals(product.id.value.toString(), duplicates.single().key)
        }

    @Test
    fun `상품마다 자기 이벤트의 순서가 지켜진다`() =
        runTest {
            val a = products.register(registerCommand("아메리카노"))
            val b = products.register(registerCommand("라떼"))
            products.activate(a.id)
            products.activate(b.id)

            val messages = receive(4)

            assertEquals(4, messages.size)
            for (product in listOf(a, b)) {
                val sequences = messages.filter { it.key == product.id.value.toString() }.map { it.json["data"]["sequence"].asLong() }
                assertEquals(listOf(0L, 1L), sequences)
            }
        }

    private suspend fun waitUntilAllPublished() {
        repeat(100) {
            if (count("SELECT count(*) FROM outbox_events WHERE published_at IS NULL") == 0L) return
            // runTest의 가상 시간이 아니라 실제 시간을 기다린다.
            withContext(Dispatchers.Default) { delay(100) }
        }
        assertEquals(0, count("SELECT count(*) FROM outbox_events WHERE published_at IS NULL"))
    }

    companion object {
        @JvmStatic
        @ServiceConnection
        val kafka: KafkaContainer = KafkaContainer("apache/kafka:3.9.1").apply { start() }
    }
}
