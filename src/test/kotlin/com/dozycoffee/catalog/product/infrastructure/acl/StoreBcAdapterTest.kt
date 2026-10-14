package com.dozycoffee.catalog.product.infrastructure.acl

import com.dozycoffee.catalog.core.StoreId
import com.dozycoffee.catalog.product.application.port.UnassignableReason
import com.dozycoffee.catalog.product.domain.product.exception.StoreServiceUnavailableException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Store 서비스 호출(StoreBcAdapter). 실제 WebClient로 로컬 HTTP 서버를 부르므로 요청 형식과 응답 해석, 분할, 시간 초과를
// 그대로 확인한다. Docker가 필요 없다. 토큰 발급은 dozy-auth 스타터의 몫이라 여기서는 다루지 않는다.
@DisplayName("StoreBcAdapter (Store 서비스 매장 확인, 요구사항 1.5)")
class StoreBcAdapterTest {
    private val mapper = JsonMapper.builder().build()
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()

    // 서버가 돌려줄 응답. 요청마다 호출된다.
    private var respond: (HttpExchange, List<Long>) -> Unit = { exchange, _ -> reply(exchange, 200, """{"unassignableStores": []}""") }

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            requests += "${exchange.requestMethod} ${exchange.requestURI.path}" to body
            val ids = mapper.readTree(body)["storeIds"].values().map { it.asLong() }
            respond(exchange, ids)
        }
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    private fun adapter(timeout: Duration = Duration.ofSeconds(3)) =
        StoreBcAdapter(
            WebClient.builder(),
            CatalogStoreProperties(baseUrl = "http://127.0.0.1:${server.address.port}", timeout = timeout),
        )

    private fun reply(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `지정할 수 없는 매장을 사유와 함께 돌려주고 계약대로 요청한다`() =
        runTest {
            respond = { exchange, _ ->
                reply(exchange, 200, """{"unassignableStores":[{"storeId":999,"reason":"NOT_FOUND"},{"storeId":87,"reason":"CLOSED"}]}""")
            }

            val result = adapter().findUnassignable(setOf(StoreId(101), StoreId(999), StoreId(87)))

            assertEquals(mapOf(StoreId(999) to UnassignableReason.NOT_FOUND, StoreId(87) to UnassignableReason.CLOSED), result)
            val (call, body) = requests.single()
            assertEquals("POST /api/v1/internal/stores/assignability-check", call)
            assertEquals(listOf(87L, 101L, 999L), mapper.readTree(body)["storeIds"].values().map { it.asLong() })
        }

    @Test
    fun `모두 지정할 수 있으면 빈 결과다`() =
        runTest {
            assertEquals(emptyMap(), adapter().findUnassignable(setOf(StoreId(1), StoreId(2))))
        }

    @Test
    fun `확인할 매장이 없으면 Store 서비스를 부르지 않는다`() =
        runTest {
            assertEquals(emptyMap(), adapter().findUnassignable(emptySet()))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `1000개를 넘으면 1000개씩 나눠 부르고 결과를 합친다`() =
        runTest {
            respond = { exchange, ids ->
                // 각 요청의 첫 매장을 폐점으로 답한다.
                reply(exchange, 200, """{"unassignableStores":[{"storeId":${ids.first()},"reason":"CLOSED"}]}""")
            }

            val result = adapter().findUnassignable((1L..2500L).map(::StoreId).toSet())

            assertEquals(listOf(1000, 1000, 500), requests.map { mapper.readTree(it.second)["storeIds"].size() })
            assertEquals(setOf(StoreId(1), StoreId(1001), StoreId(2001)), result.keys)
            assertTrue(result.values.all { it == UnassignableReason.CLOSED })
        }

    @Test
    fun `Store 서비스가 오류를 돌려주면 확인하지 못한 것으로 거부한다`() =
        runTest {
            listOf(500, 503, 403, 400).forEach { status ->
                respond = { exchange, _ -> reply(exchange, status, """{"code":"X"}""") }

                assertFailsWith<StoreServiceUnavailableException>("상태 $status") { adapter().findUnassignable(setOf(StoreId(1))) }
            }
        }

    @Test
    fun `시간 안에 응답하지 않으면 확인하지 못한 것으로 거부한다`() =
        runTest {
            respond = { exchange, _ ->
                Thread.sleep(1000)
                runCatching { reply(exchange, 200, """{"unassignableStores": []}""") }
            }

            assertFailsWith<StoreServiceUnavailableException> {
                adapter(timeout = Duration.ofMillis(200)).findUnassignable(setOf(StoreId(1)))
            }
        }

    @Test
    fun `알 수 없는 사유는 지정할 수 있는 것으로 보지 않고 거부한다`() =
        runTest {
            respond = { exchange, _ -> reply(exchange, 200, """{"unassignableStores":[{"storeId":1,"reason":"SUSPENDED"}]}""") }

            assertFailsWith<StoreServiceUnavailableException> { adapter().findUnassignable(setOf(StoreId(1))) }
        }

    @Test
    fun `서버에 연결하지 못해도 확인하지 못한 것으로 거부한다`() =
        runTest {
            val unreachable =
                StoreBcAdapter(WebClient.builder(), CatalogStoreProperties(baseUrl = "http://127.0.0.1:1", timeout = Duration.ofSeconds(1)))

            assertFailsWith<StoreServiceUnavailableException> { unreachable.findUnassignable(setOf(StoreId(1))) }
        }
}
