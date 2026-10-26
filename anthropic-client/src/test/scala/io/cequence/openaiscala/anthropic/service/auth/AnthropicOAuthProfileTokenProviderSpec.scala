package io.cequence.openaiscala.anthropic.service.auth

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.service.{
  AnthropicScalaClientException,
  AnthropicScalaClientTimeoutException
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.{CompletableFuture, ConcurrentLinkedQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests of the token refresh of [[AnthropicOAuthProfileTokenProvider]] over real HTTP, against
 * loopback servers that answer, never answer, or stall after the response headers (dummy
 * tokens, no network): a stalled token endpoint must not block the requests of a service
 * beyond the configured bounds.
 */
class AnthropicOAuthProfileTokenProviderSpec extends AnyWordSpec with Matchers {

  // a fixed "current time" (arbitrary epoch seconds), so token windows do not depend on speed
  private val now = 1700000000L

  private val profileName = "default"

  // ---------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------

  private def deleteRecursively(path: Path): Unit =
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        val children = Files.list(path)
        try children.forEach(child => deleteRecursively(child))
        finally children.close()
      }
      Files.deleteIfExists(path)
      ()
    }

  /**
   * Writes a profile (with a `client_id`, so refreshing is possible) whose token endpoint is
   * at `baseUrl` and whose cached access token expires at `expiresAt`, runs `test` with its
   * config dir, and deletes the tree.
   */
  private def withProfile(
    baseUrl: String,
    accessToken: String,
    expiresAt: Long
  )(
    test: Path => Any
  ): Unit = {
    val configDir = Files.createTempDirectory("anthropic-oauth-token-provider-spec")
    try {
      val configsDir = Files.createDirectories(configDir.resolve("configs"))
      val configJson =
        s"""{"authentication": {"type": "user_oauth", "client_id": "client-abc"}, "base_url": "$baseUrl"}"""
      Files.write(
        configsDir.resolve(s"$profileName.json"),
        configJson.getBytes(StandardCharsets.UTF_8)
      )

      val credentialsDir = Files.createDirectories(configDir.resolve("credentials"))
      val credentialsJson =
        s"""{
           |  "type": "oauth_token",
           |  "access_token": "$accessToken",
           |  "refresh_token": "rt-old",
           |  "expires_at": $expiresAt,
           |  "scope": "user:inference"
           |}""".stripMargin
      Files.write(
        credentialsDir.resolve(s"$profileName.json"),
        credentialsJson.getBytes(StandardCharsets.UTF_8)
      )

      test(configDir)
      ()
    } finally {
      deleteRecursively(configDir)
    }
  }

  /** The real `postRefresh`, with a fixed clock. */
  private class FixedClockProvider(
    configDir: Path,
    connectTimeout: Duration,
    refreshTimeout: Duration,
    lockWaitTimeout: Duration
  ) extends AnthropicOAuthProfileTokenProvider(
        Some(configDir),
        Some(profileName),
        connectTimeout,
        refreshTimeout,
        lockWaitTimeout
      ) {
    override protected def nowSeconds(): Long = now
  }

  private def providerWithBounds(
    configDir: Path,
    refreshMs: Long,
    lockWaitMs: Long = 5000
  ): AnthropicOAuthProfileTokenProvider =
    new FixedClockProvider(
      configDir,
      connectTimeout = Duration.ofSeconds(2),
      refreshTimeout = Duration.ofMillis(refreshMs),
      lockWaitTimeout = Duration.ofMillis(lockWaitMs)
    )

  private def providerWithDefaults(configDir: Path): AnthropicOAuthProfileTokenProvider =
    new FixedClockProvider(
      configDir,
      AnthropicOAuthProfileTokenProvider.defaultConnectTimeout,
      AnthropicOAuthProfileTokenProvider.defaultRefreshTimeout,
      AnthropicOAuthProfileTokenProvider.defaultLockWaitTimeout
    )

  private def elapsedMsSince(startNanos: Long): Long =
    (System.nanoTime() - startNanos) / 1000000L

  private def waitUntil(
    what: String,
    timeoutMs: Long = 5000
  )(
    condition: => Boolean
  ): Unit = {
    val deadline = System.nanoTime() + timeoutMs * 1000000L
    while (!condition) {
      if (System.nanoTime() > deadline) {
        fail(s"Timed out waiting for $what")
      }
      Thread.sleep(10)
    }
  }

  private case class Outcome(
    result: Either[Throwable, String],
    elapsedMs: Long,
    interruptStatusKept: Boolean
  ) {
    def token: String =
      result match {
        case Right(token) => token
        case Left(e)      => fail(s"Expected a token, got $e")
      }

    def failure: Throwable =
      result match {
        case Left(e)      => e
        case Right(token) => fail(s"Expected a failure, got the token '$token'")
      }
  }

  /** `provider.accessToken()` on a thread of its own. */
  private class AsyncCall(provider: AnthropicOAuthProfileTokenProvider) {
    val outcome = new CompletableFuture[Outcome]()

    val thread = new Thread(new Runnable {
      def run(): Unit = {
        val started = System.nanoTime()
        val result: Either[Throwable, String] =
          try {
            Right(provider.accessToken())
          } catch {
            case e: Exception => Left(e)
          }

        outcome.complete(
          Outcome(result, elapsedMsSince(started), Thread.currentThread().isInterrupted)
        )
      }
    })
    thread.setDaemon(true)
    thread.start()

    def await(): Outcome = outcome.get(15, TimeUnit.SECONDS)
  }

  /**
   * A token endpoint that accepts connections and never answers - or, with
   * `sendPartialResponse`, sends the status line, the headers and 8 of 200 body bytes and then
   * stalls.
   */
  private class StallingServer(sendPartialResponse: Boolean) {
    private val serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val sockets = new ConcurrentLinkedQueue[Socket]()

    val accepted = new AtomicInteger(0)

    def baseUrl: String = s"http://127.0.0.1:${serverSocket.getLocalPort}"

    private val acceptor = new Thread(new Runnable {
      def run(): Unit =
        try {
          while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            sockets.add(socket)
            accepted.incrementAndGet()

            if (sendPartialResponse) {
              val out = socket.getOutputStream
              out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 200\r\n\r\n" +
                  "{\"access\"").getBytes(StandardCharsets.UTF_8)
              )
              out.flush()
            }
          }
        } catch {
          case _: IOException => // closed
        }
    })
    acceptor.setDaemon(true)
    acceptor.start()

    def close(): Unit = {
      serverSocket.close()
      val it = sockets.iterator()
      while (it.hasNext) {
        try it.next().close()
        catch {
          case _: IOException =>
        }
      }
    }
  }

  private def withStallingServer(
    sendPartialResponse: Boolean
  )(
    test: StallingServer => Any
  ): Unit = {
    val server = new StallingServer(sendPartialResponse)
    try {
      test(server)
      ()
    } finally {
      server.close()
    }
  }

  private case class SeenRequest(
    method: String,
    path: String,
    betaHeader: Option[String],
    body: String
  )

  /** A token endpoint that answers every request with `status` and `responseBody`. */
  private class AnsweringServer(
    status: Int,
    responseBody: String
  ) {
    private val server =
      HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)

    val requests = new ConcurrentLinkedQueue[SeenRequest]()

    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          requests.add(
            SeenRequest(
              exchange.getRequestMethod,
              exchange.getRequestURI.getPath,
              Option(exchange.getRequestHeaders.getFirst("anthropic-beta")),
              body
            )
          )

          val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          val out = exchange.getResponseBody
          out.write(bytes)
          out.close()
          exchange.close()
        }
      }
    )
    server.start()

    def baseUrl: String = s"http://127.0.0.1:${server.getAddress.getPort}"

    def stop(): Unit = server.stop(0)
  }

  private def withAnsweringServer(
    status: Int,
    responseBody: String
  )(
    test: AnsweringServer => Any
  ): Unit = {
    val server = new AnsweringServer(status, responseBody)
    try {
      test(server)
      ()
    } finally {
      server.stop()
    }
  }

  private val refreshedTokenJson =
    """{"access_token": "at-new", "expires_in": 3600, "refresh_token": "rt-new"}"""

  // ---------------------------------------------------------------------------------------
  // Advisory window: a stalled refresh never blocks the other callers
  // ---------------------------------------------------------------------------------------

  Seq(
    ("a silent token endpoint", false),
    ("a token endpoint stalling after the response headers", true)
  ).foreach { case (endpoint, sendPartialResponse) =>
    s"AnthropicOAuthProfileTokenProvider in the advisory window with $endpoint" should {

      "serve the cached token to a concurrent caller at once, end the refresh at its deadline and back off" in {
        withStallingServer(sendPartialResponse) { server =>
          withProfile(server.baseUrl, "at-cached", expiresAt = now + 100) { configDir =>
            val provider = providerWithBounds(configDir, refreshMs = 700)

            // the first caller starts the refresh, which then stalls
            val refreshing = new AsyncCall(provider)
            waitUntil("the refresh connection")(server.accepted.get() >= 1)

            // the second one does not wait for it
            val started = System.nanoTime()
            provider.accessToken() shouldBe "at-cached"
            elapsedMsSince(started) should be < 300L
            refreshing.outcome.isDone shouldBe false

            // the refreshing call gives up at its deadline and still serves the cached token
            val outcome = refreshing.await()
            outcome.token shouldBe "at-cached"
            outcome.elapsedMs should (be >= 500L and be < 6000L)

            // single flight: the second caller did not start a refresh of its own
            server.accepted.get() shouldBe 1

            // the failure was recorded, so the next callers do not retry (5 s backoff)
            val afterStarted = System.nanoTime()
            provider.accessToken() shouldBe "at-cached"
            elapsedMsSince(afterStarted) should be < 300L
            server.accepted.get() shouldBe 1
          }
        }
      }
    }
  }

  "AnthropicOAuthProfileTokenProvider in the advisory window" should {

    "keep the interrupt status of a thread interrupted during the refresh and serve the cached token" in {
      withStallingServer(sendPartialResponse = false) { server =>
        withProfile(server.baseUrl, "at-cached", expiresAt = now + 100) { configDir =>
          // a long deadline: only the interrupt can end the refresh early
          val provider = providerWithBounds(configDir, refreshMs = 10000)

          val refreshing = new AsyncCall(provider)
          waitUntil("the refresh connection")(server.accepted.get() >= 1)
          refreshing.thread.interrupt()

          val outcome = refreshing.await()
          outcome.token shouldBe "at-cached"
          outcome.elapsedMs should be < 5000L
          outcome.interruptStatusKept shouldBe true
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Mandatory window: callers fail within their bounds instead of hanging
  // ---------------------------------------------------------------------------------------

  Seq(
    ("a silent token endpoint", false),
    ("a token endpoint stalling after the response headers", true)
  ).foreach { case (endpoint, sendPartialResponse) =>
    s"AnthropicOAuthProfileTokenProvider in the mandatory window with $endpoint" should {

      "fail the refreshing caller at the deadline and a queued caller at the lock wait bound" in {
        withStallingServer(sendPartialResponse) { server =>
          withProfile(server.baseUrl, "at-expiring", expiresAt = now + 10) { configDir =>
            val provider = providerWithBounds(configDir, refreshMs = 1000, lockWaitMs = 250)

            // the first caller gets the lock, and its refresh stalls
            val refreshing = new AsyncCall(provider)
            waitUntil("the refresh connection")(server.accepted.get() >= 1)

            // the second one waits for it only up to the bound
            val started = System.nanoTime()
            val waiter = intercept[AnthropicScalaClientException] {
              provider.accessToken()
            }
            val waited = elapsedMsSince(started)
            waiter.getMessage should include("waiting for an in-flight")
            waited should (be >= 200L and be < 700L)
            server.accepted.get() shouldBe 1

            // the first one fails with a timeout, not a hang
            val outcome = refreshing.await()
            outcome.failure shouldBe an[AnthropicScalaClientTimeoutException]
            outcome.failure.getMessage should include("timed out")
            outcome.elapsedMs should (be >= 800L and be < 6000L)

            // the lock was released: the next caller refreshes (and times out) on its own
            val next = intercept[AnthropicScalaClientTimeoutException] {
              provider.accessToken()
            }
            next.getMessage should include("timed out")
            server.accepted.get() shouldBe 2
          }
        }
      }
    }
  }

  "AnthropicOAuthProfileTokenProvider in the mandatory window" should {

    "fail a caller interrupted during the refresh, keeping its interrupt status" in {
      withStallingServer(sendPartialResponse = false) { server =>
        withProfile(server.baseUrl, "at-expiring", expiresAt = now + 10) { configDir =>
          val provider = providerWithBounds(configDir, refreshMs = 10000)

          val refreshing = new AsyncCall(provider)
          waitUntil("the refresh connection")(server.accepted.get() >= 1)
          refreshing.thread.interrupt()

          val outcome = refreshing.await()
          outcome.failure shouldBe an[AnthropicScalaClientException]
          outcome.failure.getMessage should include("interrupted")
          outcome.elapsedMs should be < 5000L
          outcome.interruptStatusKept shouldBe true
        }
      }
    }

    "fail a caller interrupted while it waits for an in-flight refresh, keeping its interrupt status" in {
      withStallingServer(sendPartialResponse = false) { server =>
        withProfile(server.baseUrl, "at-expiring", expiresAt = now + 10) { configDir =>
          // long bounds: only the interrupt can end the wait early
          val provider = providerWithBounds(configDir, refreshMs = 10000, lockWaitMs = 10000)

          val refreshing = new AsyncCall(provider)
          waitUntil("the refresh connection")(server.accepted.get() >= 1)

          val waiting = new AsyncCall(provider)
          Thread.sleep(100)
          waiting.thread.interrupt()

          val outcome = waiting.await()
          outcome.failure shouldBe an[AnthropicScalaClientException]
          outcome.failure.getMessage should include("Interrupted while waiting")
          outcome.elapsedMs should be < 5000L
          outcome.interruptStatusKept shouldBe true

          // the refresh of the first caller is not affected
          refreshing.outcome.isDone shouldBe false
          refreshing.thread.interrupt()
          refreshing.await().failure shouldBe an[AnthropicScalaClientException]
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // The happy path still refreshes
  // ---------------------------------------------------------------------------------------

  "AnthropicOAuthProfileTokenProvider" should {

    "have the default bounds of 10 s (connect), 30 s (refresh) and 45 s (lock wait)" in {
      AnthropicOAuthProfileTokenProvider.defaultConnectTimeout shouldBe Duration.ofSeconds(10)
      AnthropicOAuthProfileTokenProvider.defaultRefreshTimeout shouldBe Duration.ofSeconds(30)
      AnthropicOAuthProfileTokenProvider.defaultLockWaitTimeout shouldBe Duration.ofSeconds(45)
    }

    "refresh over HTTP in the advisory window and persist the rotated tokens" in {
      withAnsweringServer(200, refreshedTokenJson) { server =>
        withProfile(server.baseUrl, "at-old", expiresAt = now + 100) { configDir =>
          val provider = providerWithDefaults(configDir)

          provider.accessToken() shouldBe "at-new"

          server.requests.size() shouldBe 1
          val request = server.requests.peek()
          request.method shouldBe "POST"
          request.path shouldBe AnthropicOAuthConsts.tokenEndpointPath
          request.betaHeader shouldBe Some(AnthropicOAuthConsts.oauthBetaValue)

          val body = Json.parse(request.body)
          (body \ "grant_type").as[String] shouldBe AnthropicOAuthConsts.grantTypeRefreshToken
          (body \ "refresh_token").as[String] shouldBe "rt-old"
          (body \ "client_id").as[String] shouldBe "client-abc"

          val onDisk = AnthropicOAuthProfile.loadCredentials(
            configDir.resolve("credentials").resolve(s"$profileName.json")
          )
          onDisk.accessToken shouldBe "at-new"
          onDisk.refreshToken shouldBe Some("rt-new")
          onDisk.expiresAt shouldBe Some(now + 3600)
        }
      }
    }

    "refresh over HTTP in the mandatory window" in {
      withAnsweringServer(200, refreshedTokenJson) { server =>
        withProfile(server.baseUrl, "at-old", expiresAt = now + 10) { configDir =>
          val provider = providerWithDefaults(configDir)

          provider.accessToken() shouldBe "at-new"
          // fresh now: a second call does not refresh again
          provider.accessToken() shouldBe "at-new"
          server.requests.size() shouldBe 1
        }
      }
    }

    "fail with the status and body of a non-2xx answer in the mandatory window" in {
      withAnsweringServer(500, """{"error": "boom"}""") { server =>
        withProfile(server.baseUrl, "at-old", expiresAt = now + 10) { configDir =>
          val provider = providerWithDefaults(configDir)

          val ex = intercept[AnthropicScalaClientException] {
            provider.accessToken()
          }
          ex.getMessage should include("status 500")
          ex.getMessage should include("boom")
        }
      }
    }

    "serve the cached token when a refresh in the advisory window is answered with an error" in {
      withAnsweringServer(500, """{"error": "boom"}""") { server =>
        withProfile(server.baseUrl, "at-cached", expiresAt = now + 100) { configDir =>
          val provider = providerWithDefaults(configDir)

          provider.accessToken() shouldBe "at-cached"
          server.requests.size() shouldBe 1

          // backed off
          provider.accessToken() shouldBe "at-cached"
          server.requests.size() shouldBe 1
        }
      }
    }
  }
}
