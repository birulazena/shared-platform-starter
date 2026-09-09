package com.hz.logistics.parentservice.autoconfigure.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Small in-process HTTP collector for deterministic OTLP request tests.
 *
 * The fixture records request bytes and headers without depending on an OTLP
 * protobuf implementation. It can acknowledge or reject requests and is
 * safe to use with try-with-resources/use blocks.
 */
class ControlledOtlpCollector(
    responseStatus: Int = 200,
    private val responseBody: ByteArray = ByteArray(0),
    private val path: String = "/v1/traces",
    private val responseDelay: Duration = Duration.ZERO,
) : AutoCloseable {

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "controlled-otlp-collector").apply { isDaemon = true }
    }
    private val receivedLatch = CountDownLatch(1)
    private val _requests = CopyOnWriteArrayList<ReceivedRequest>()
    @Volatile
    private var status: Int = responseStatus
    @Volatile
    private var server: HttpServer? = null

    val requests: List<ReceivedRequest>
        get() = _requests.toList()

    val requestCount: Int
        get() = _requests.size

    val endpoint: URI
        get() = URI.create("http://127.0.0.1:${requireNotNull(server) { "Collector is not started" }.address.port}$path")

    val traceEndpoint: URI
        get() = endpoint

    @Synchronized
    fun start(): ControlledOtlpCollector {
        check(server == null) { "Collector has already been started or closed" }
        val started = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        started.createContext(path) { exchange ->
            val body = exchange.requestBody.use { it.readBytes() }
            val headers = exchange.requestHeaders.entries.associate { (name, values) ->
                name to values.toList()
            }
            _requests += ReceivedRequest(
                method = exchange.requestMethod,
                uri = exchange.requestURI,
                headers = headers,
                body = body,
            )
            receivedLatch.countDown()

            if (!responseDelay.isZero) {
                try {
                    Thread.sleep(responseDelay.toMillis())
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val reply = responseBody.copyOf()
            exchange.sendResponseHeaders(status, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        started.executor = executor
        started.start()
        server = started
        return this
    }

    fun respondWith(status: Int): ControlledOtlpCollector {
        require(status in 100..599) { "status must be a valid HTTP status" }
        this.status = status
        return this
    }

    fun reject(status: Int = 503): ControlledOtlpCollector = respondWith(status)

    fun awaitRequest(timeout: Duration = Duration.ofSeconds(5)): Boolean =
        receivedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)

    @Synchronized
    override fun close() {
        server?.stop(0)
        server = null
        executor.shutdownNow()
    }

    data class ReceivedRequest(
        val method: String,
        val uri: URI,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    )
}
