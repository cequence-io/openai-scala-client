package consumer

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.wsclient.domain.WsRequestContext

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/** An OpenAI-shaped local server streaming "Hello world" as two chat chunks. */
final class LocalChatServer private (server: HttpServer) {

  val coreUrl: String = s"http://localhost:${server.getAddress.getPort}/v1/"

  val requestContext: WsRequestContext =
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer local"))

  def stop(): Unit = server.stop(0)
}

object LocalChatServer {

  val expected = "Hello world"

  private def chunk(text: String) =
    s"""data: {"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"local","choices":[{"index":0,"delta":{"content":"$text"},"finish_reason":null}]}\n\n"""

  def start(): LocalChatServer = {
    val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    server.createContext(
      "/v1/chat/completions",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val body = (chunk("Hello") + chunk(" world") + "data: [DONE]\n\n")
            .getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
          exchange.sendResponseHeaders(200, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        }
      }
    )
    server.start()
    new LocalChatServer(server)
  }

  /** Fails the JVM (a non-zero exit for run.sh) unless the run streamed the text over `bridges`. */
  def check(
    consumer: String,
    text: String,
    bridges: Long,
    expectedBridges: Long
  ): Unit =
    if (text == expected && bridges == expectedBridges)
      println(s"PASS  $consumer: '$text', $bridges bridge(s)")
    else {
      println(s"FAIL  $consumer: '$text', $bridges bridge(s) - expected '$expected', $expectedBridges")
      sys.exit(1)
    }
}
