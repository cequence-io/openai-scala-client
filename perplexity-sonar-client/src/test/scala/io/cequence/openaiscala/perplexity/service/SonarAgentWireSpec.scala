package io.cequence.openaiscala.perplexity.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.perplexity.AgentJsonFormats
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source
import scala.util.Try

/**
 * The Agent API calls of [[SonarService]] over the wire, through the real streaming engine
 * against a local HTTP server: paths, methods, query, the bearer header, request bodies,
 * response parsing, SSE decoding, 404 -> None, error surfacing, binary download.
 */
class SonarAgentWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("sonar-agent-wire-spec")
  private implicit val materializer: Materializer = Materializer(system)

  private case class Received(
    method: String,
    path: String,
    query: Option[String],
    headers: Map[String, String],
    body: String
  )

  @volatile private var received: Option[Received] = None
  @volatile private var extraHeaders: Map[String, String] = Map.empty
  // status, body, content type
  @volatile private var reply: (Int, Array[Byte], String) =
    (200, "{}".getBytes, "application/json")

  private def replyJson(
    status: Int,
    body: String
  ): Unit = reply = (status, body.getBytes(StandardCharsets.UTF_8), "application/json")

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}/"

  private lazy val service =
    SonarServiceFactory.withEngine(engine, apiKey = "test-key", baseUrl = Some(baseUrl))

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          received = Some(
            Received(
              exchange.getRequestMethod,
              exchange.getRequestURI.getPath,
              Option(exchange.getRequestURI.getRawQuery),
              exchange.getRequestHeaders
                .keySet()
                .toArray
                .map { key =>
                  key.toString.toLowerCase -> exchange.getRequestHeaders.getFirst(key.toString)
                }
                .toMap,
              body
            )
          )
          val (status, bytes, contentType) = reply
          exchange.getResponseHeaders.add("Content-Type", contentType)
          extraHeaders.foreach { case (k, v) => exchange.getResponseHeaders.add(k, v) }
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private def lastRequest: Received = received.getOrElse(fail("no request received"))

  private val responseExample: String = {
    val source = Source.fromResource("agent-docs-examples/response-10-quickstart.json")
    try source.mkString
    finally source.close()
  }

  private val settings = CreateAgentResponseSettings(
    preset = Some(AgentPreset.fast),
    tools = Seq(AgentTool.WebSearch(maxResults = Some(3))),
    maxOutputTokens = Some(500)
  )

  // the SSE of a short streamed run (event shapes from the spec)
  private val sse: String = Seq(
    """{"type":"response.created","sequence_number":0,"response":{"id":"resp_1","object":"response","created_at":1790000000,"model":"openai/gpt-6-luna","status":"in_progress","output":[]}}""",
    """{"type":"response.reasoning.started","sequence_number":1,"thought":"Searching"}""",
    """{"type":"response.reasoning.search_queries","sequence_number":2,"queries":["capital of norway"]}""",
    """{"type":"response.reasoning.search_results","sequence_number":3,"results":[{"id":1,"url":"https://en.wikipedia.org/wiki/Oslo","title":"Oslo","snippet":"Oslo is the capital"}]}""",
    """{"type":"response.output_item.added","sequence_number":4,"output_index":1,"item":{"type":"message","id":"msg_1","status":"in_progress","role":"assistant","content":[]}}""",
    """{"type":"response.output_text.delta","sequence_number":5,"item_id":"msg_1","output_index":1,"content_index":0,"delta":"Os"}""",
    """{"type":"response.output_text.delta","sequence_number":6,"item_id":"msg_1","output_index":1,"content_index":0,"delta":"lo"}""",
    """{"type":"response.output_text.done","sequence_number":7,"item_id":"msg_1","output_index":1,"content_index":0,"text":"Oslo"}""",
    """{"type":"response.completed","sequence_number":8,"response":{"id":"resp_1","object":"response","created_at":1790000000,"model":"openai/gpt-6-luna","status":"completed","output":[{"type":"message","id":"msg_1","status":"completed","role":"assistant","content":[{"type":"output_text","text":"Oslo","annotations":[]}]}],"usage":{"input_tokens":10,"output_tokens":2,"total_tokens":12,"cost":{"currency":"USD","input_cost":0.001,"output_cost":0.0002,"total_cost":0.0012}}}}"""
  ).map(json => s"event: ${(Json.parse(json) \ "type").as[String]}\ndata: $json\n\n")
    .mkString + "data: [DONE]\n\n"

  "createAgentResponse" should {

    "POST the request body to v1/agent with the bearer key and parse the response" in {
      replyJson(200, responseExample)

      val response = await(
        service.createAgentResponse(AgentInput("What is the capital of Norway?"), settings)
      )
      val request = lastRequest

      request.method shouldBe "POST"
      request.path shouldBe "/v1/agent"
      request.headers("authorization") shouldBe "Bearer test-key"
      Json.parse(request.body) shouldBe
        AgentJsonFormats.createAgentRequestBody(
          AgentInput("What is the capital of Norway?"),
          settings,
          stream = false
        )

      response.id shouldBe (Json.parse(responseExample) \ "id").as[String]
      response.outputText should not be empty
    }

    "use the fast preset by default" in {
      replyJson(200, responseExample)
      await(service.createAgentResponse(AgentInput("hi")))

      Json.parse(lastRequest.body) shouldBe Json.obj(
        "input" -> "hi",
        "preset" -> "fast",
        "stream" -> false
      )
    }

    "surface an API error with its status and message" in {
      replyJson(
        400,
        """{"error":{"message":"Invalid preset: nope","type":"invalid_request_error","code":"400"}}"""
      )

      val error = intercept[PerplexityScalaInvalidRequestException](
        await(
          service.createAgentResponse(
            AgentInput("hi"),
            CreateAgentResponseSettings(preset = Some("nope"))
          )
        )
      )
      error.getMessage should include("400")
      error.getMessage should include("Invalid preset: nope")
    }
  }

  "the error mapping" should {

    "classify statuses and error.type into the Perplexity exceptions, with the request id" in {
      replyJson(
        429,
        """{"error":{"message":"Rate limit exceeded, please try again later.","type":"request_rate_limit_exceeded","code":429}}"""
      )
      val rateLimit =
        intercept[PerplexityScalaRateLimitException](await(service.listAgentModels))
      rateLimit.getMessage shouldBe "Code 429 : Rate limit exceeded, please try again later."
      rateLimit.httpCode shouldBe Some(429)
      rateLimit.errorType shouldBe Some("request_rate_limit_exceeded")
      PerplexityRetryable(rateLimit) shouldBe true

      replyJson(
        400,
        """{"error":{"message":"Invalid model 'nope'.","type":"invalid_model","code":400}}"""
      )
      val invalidModel = intercept[PerplexityScalaInvalidModelException](
        await(
          service.createAgentResponse(
            AgentInput("x"),
            CreateAgentResponseSettings(preset = Some("nope"))
          )
        )
      )
      invalidModel shouldBe a[PerplexityScalaInvalidRequestException]
      PerplexityRetryable(invalidModel) shouldBe false

      replyJson(
        400,
        """{"error":{"message":"validation failed: input array cannot be empty","type":"invalid_request","code":400}}"""
      )
      intercept[PerplexityScalaInvalidRequestException](
        await(service.createAgentResponse(AgentInput("x")))
      ).errorType shouldBe Some("invalid_request")

      replyJson(
        401,
        """{"error":{"message":"Invalid API key provided.","type":"invalid_api_key","code":401}}"""
      )
      intercept[PerplexityScalaUnauthorizedException](
        await(service.cancelAgentResponse("resp_1"))
      )

      replyJson(
        404,
        """{"error":{"message":"resource not found","type":"not_found","code":404}}"""
      )
      intercept[PerplexityScalaNotFoundException](
        await(service.listAgentResponseFiles("resp_1"))
      )

      replyJson(503, "Service Unavailable")
      intercept[PerplexityScalaEngineOverloadedException](await(service.listAgentModels))

      replyJson(502, "<html>Bad Gateway</html>")
      val gateway = intercept[PerplexityScalaServerErrorException](
        await(service.retrieveAgentResponse("resp_1"))
      )
      gateway.getMessage should include("Bad Gateway")
      PerplexityRetryable(gateway) shouldBe true
    }

    "carry Perplexity's x-request-id" in {
      extraHeaders = Map("x-request-id" -> "req-123")
      replyJson(
        429,
        """{"error":{"message":"Rate limit exceeded","type":"request_rate_limit_exceeded","code":429}}"""
      )
      try {
        val error =
          intercept[PerplexityScalaRateLimitException](await(service.listAgentModels))
        error.requestId shouldBe Some("req-123")
        error.getMessage should endWith("[request req-123]")
        io.cequence.openaiscala.ProviderErrorDetails
          .unapply(error)
          .flatMap(_.requestId) shouldBe Some("req-123")
      } finally extraHeaders = Map.empty
    }

    "classify the error body of a streamed request by its code" in {
      replyJson(
        429,
        """{"error":{"message":"Rate limit exceeded","type":"request_rate_limit_exceeded","code":429}}"""
      )
      intercept[PerplexityScalaRateLimitException](
        await(service.createAgentResponseStreamed(AgentInput("x"), settings).runWith(Sink.seq))
      ).errorType shouldBe Some("request_rate_limit_exceeded")

      replyJson(
        401,
        """{"error":{"message":"Invalid API key provided.","type":"invalid_api_key","code":"401"}}"""
      )
      intercept[PerplexityScalaUnauthorizedException](
        await(service.resumeAgentResponseStream("resp_1").runWith(Sink.seq))
      )
    }

    "classify the Sonar chat completions errors too" in {
      replyJson(
        401,
        """{"error":{"message":"Invalid API key provided.","type":"invalid_api_key","code":401}}"""
      )
      intercept[PerplexityScalaUnauthorizedException](
        await(
          service.createChatCompletion(
            Seq(io.cequence.openaiscala.perplexity.domain.Message.UserMessage("hi"))
          )
        )
      )
    }
  }

  "createAgentResponseStreamed" should {

    "POST with stream=true and decode the typed events" in {
      reply = (200, sse.getBytes(StandardCharsets.UTF_8), "text/event-stream")

      val events = await(
        service
          .createAgentResponseStreamed(AgentInput("Capital of Norway?"), settings)
          .runWith(Sink.seq)
      )
      val request = lastRequest

      request.method shouldBe "POST"
      request.path shouldBe "/v1/agent"
      request.headers("accept") shouldBe "text/event-stream"
      (Json.parse(request.body) \ "stream").as[Boolean] shouldBe true

      events.map(_.sequenceNumber) shouldBe (0 to 8)
      events.collect { case d: AgentStreamEvent.OutputTextDelta =>
        d.delta
      }.mkString shouldBe "Oslo"
      events.collect { case q: AgentStreamEvent.SearchQueries =>
        q.queries
      }.flatten shouldBe Seq("capital of norway")
      val completed = events.collect { case c: AgentStreamEvent.ResponseCompleted =>
        c.response
      }.flatten
      completed.map(_.outputText) shouldBe Seq("Oslo")
      completed.flatMap(_.usage.flatMap(_.cost)).map(_.totalCost) shouldBe Seq(0.0012)
      events.collect { case u: AgentStreamEvent.Unknown => u } shouldBe empty
    }

    "decode a CRLF-framed stream the same way" in {
      reply =
        (200, sse.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8), "text/event-stream")

      val events =
        await(service.createAgentResponseStreamed(AgentInput("x"), settings).runWith(Sink.seq))
      events.map(_.sequenceNumber) shouldBe (0 to 8)
    }

    "fail the stream on an API error" in {
      replyJson(
        401,
        """{"error":{"message":"Invalid API key","type":"authentication_error"}}"""
      )

      // a body without `code` still fails the stream, as the base exception
      val error = intercept[PerplexityScalaClientException](
        await(service.createAgentResponseStreamed(AgentInput("x"), settings).runWith(Sink.seq))
      )
      error.getMessage should include("Invalid API key")
    }
  }

  "resumeAgentResponseStream" should {

    "GET v1/agent/{id} with stream=true and starting_after" in {
      reply = (200, sse.getBytes(StandardCharsets.UTF_8), "text/event-stream")

      val events = await(
        service.resumeAgentResponseStream("resp_1", startingAfter = Some(5)).runWith(Sink.seq)
      )
      val request = lastRequest

      request.method shouldBe "GET"
      request.path shouldBe "/v1/agent/resp_1"
      request.query.map(_.split('&').toSet) shouldBe Some(
        Set("stream=true", "starting_after=5")
      )
      events should not be empty
    }

    "leave starting_after out when not given" in {
      reply = (200, sse.getBytes(StandardCharsets.UTF_8), "text/event-stream")
      await(service.resumeAgentResponseStream("resp_1").runWith(Sink.seq))

      lastRequest.query shouldBe Some("stream=true")
    }
  }

  "retrieveAgentResponse" should {

    "GET v1/agent/{id}" in {
      replyJson(200, responseExample)

      val response = await(service.retrieveAgentResponse("resp_1"))
      lastRequest.method shouldBe "GET"
      lastRequest.path shouldBe "/v1/agent/resp_1"
      response.map(_.outputText).getOrElse("") should not be empty
    }

    "return None on 404" in {
      replyJson(404, """{"error":{"message":"Response not found"}}""")

      await(service.retrieveAgentResponse("resp_missing")) shouldBe None
    }
  }

  "cancelAgentResponse" should {

    "POST v1/agent/{id}/cancel" in {
      replyJson(200, """{"response_id":"resp_1","status":"cancelling"}""")

      await(service.cancelAgentResponse("resp_1")) shouldBe AgentCancelResponse(
        "resp_1",
        "cancelling"
      )
      lastRequest.method shouldBe "POST"
      lastRequest.path shouldBe "/v1/agent/resp_1/cancel"
    }

    "fail with the API message for a finished response" in {
      replyJson(400, """{"error":{"message":"Response is already completed"}}""")

      intercept[PerplexityScalaInvalidRequestException](
        await(service.cancelAgentResponse("resp_1"))
      ).getMessage should include(
        "already completed"
      )
    }
  }

  "the response files" should {

    "be listed from v1/agent/{id}/files" in {
      replyJson(
        200,
        """{"object":"list","data":[{"id":"file_1","object":"file","filename":"ai_news.csv","bytes":8002,"created_at":1780923289}]}"""
      )

      await(service.listAgentResponseFiles("resp_1")) shouldBe Seq(
        AgentResponseFile("file_1", "ai_news.csv", 8002L, 1780923289L)
      )
      lastRequest.path shouldBe "/v1/agent/resp_1/files"
    }

    "download as raw bytes from v1/agent/{id}/files/{file_id}/content" in {
      val bytes = Array[Byte](0, 1, 2, -1, 80, 75, 3, 4)
      reply = (200, bytes, "application/octet-stream")

      val source =
        await(service.downloadAgentResponseFile("resp_1", "file_1")).getOrElse(fail("no file"))
      val downloaded = await(source.runWith(Sink.fold(akka.util.ByteString.empty)(_ ++ _)))

      downloaded.toArray shouldBe bytes
      lastRequest.path shouldBe "/v1/agent/resp_1/files/file_1/content"
    }

    "download as None on 404" in {
      replyJson(404, """{"error":{"message":"File not found"}}""")

      await(service.downloadAgentResponseFile("resp_1", "file_missing")) shouldBe None
    }
  }

  "listAgentModels" should {

    "GET v1/models" in {
      replyJson(
        200,
        """{"object":"list","data":[{"id":"openai/gpt-6-luna","object":"model","created":1790000000,"owned_by":"openai"}]}"""
      )

      await(service.listAgentModels) shouldBe Seq(
        AgentModel("openai/gpt-6-luna", 1790000000L, "openai")
      )
      lastRequest.path shouldBe "/v1/models"
    }
  }

  "agentAsOpenAI" should {

    // verbatim shape of Perplexity's /v1/responses (live 2026-09-25): empty truncation,
    // a provider-specific search_results item next to the message
    val perplexityResponse =
      """{"background":false,"created_at":1790349468,"error":null,"id":"resp_1","incomplete_details":null,
        |"instructions":null,"max_output_tokens":300,"metadata":{},"model":"openai/gpt-5.4-mini","object":"response",
        |"output":[{"type":"search_results","queries":["q"],"results":[{"id":1,"url":"https://e.com","title":"t","snippet":"s"}]},
        |{"type":"function_call","id":"fc_1","call_id":"call_1","name":"get_weather","arguments":"{\"city\":\"Oslo\"}","status":"completed"},
        |{"type":"message","id":"msg_1","role":"assistant","status":"completed","content":[{"type":"output_text","text":"Oslo.","annotations":[]}]}],
        |"parallel_tool_calls":true,"previous_response_id":null,"reasoning":null,"service_tier":"default","status":"completed",
        |"store":false,"temperature":1,"text":{"format":{"type":"text"}},"tool_choice":"auto","tools":[],"top_logprobs":0,
        |"top_p":1,"truncation":"","usage":{"input_tokens":128,"input_tokens_details":{"cached_tokens":0},"output_tokens":12,
        |"output_tokens_details":{"reasoning_tokens":0},"total_tokens":140},"user":null}""".stripMargin
        .replace("\n", "")

    "send chat and tool completions to v1/responses and read Perplexity's response" in {
      val chat =
        SonarServiceFactory.agentAsOpenAI(apiKey = "test-key", baseUrl = Some(baseUrl))
      try {
        replyJson(200, perplexityResponse)

        val response = await(
          chat.createChatToolCompletion(
            Seq(io.cequence.openaiscala.domain.UserMessage("Weather in Oslo?")),
            Seq(
              io.cequence.openaiscala.domain.AssistantTool.FunctionTool(
                name = "get_weather",
                parameters = io.cequence.openaiscala.domain.JsonSchema.Object(
                  properties =
                    Seq("city" -> io.cequence.openaiscala.domain.JsonSchema.String()),
                  required = Seq("city")
                )
              )
            ),
            settings = io.cequence.openaiscala.domain.settings
              .CreateChatCompletionSettings("openai/gpt-5.4-mini")
          )
        )
        val request = lastRequest

        request.method shouldBe "POST"
        request.path shouldBe "/v1/responses"
        request.headers("authorization") shouldBe "Bearer test-key"
        (Json.parse(request.body) \ "model").as[String] shouldBe "openai/gpt-5.4-mini"
        (Json.parse(request.body) \ "tools" \ 0 \ "name").as[String] shouldBe "get_weather"

        response.choices.head.message.tool_calls.map(_._2.toString).mkString should include(
          "get_weather"
        )
        response.usage.map(_.total_tokens) shouldBe Some(140)
      } finally chat.close()
    }

    "repack Perplexity errors onto the shared OpenAI exceptions, the Perplexity one as the cause" in {
      val chat =
        SonarServiceFactory.agentAsOpenAI(apiKey = "test-key", baseUrl = Some(baseUrl))
      try {
        extraHeaders = Map("x-request-id" -> "req-9")
        replyJson(
          429,
          """{"error":{"message":"Rate limit exceeded","type":"request_rate_limit_exceeded","code":429}}"""
        )
        val error = intercept[io.cequence.openaiscala.OpenAIScalaRateLimitException](
          await(
            chat.createChatCompletion(
              Seq(io.cequence.openaiscala.domain.UserMessage("hi")),
              io.cequence.openaiscala.domain.settings
                .CreateChatCompletionSettings("openai/gpt-5.4-mini")
            )
          )
        )
        error.getCause shouldBe a[PerplexityScalaRateLimitException]
        io.cequence.openaiscala.ProviderErrorDetails
          .unapply(error)
          .flatMap(_.errorType) shouldBe
          Some("request_rate_limit_exceeded")
      } finally {
        extraHeaders = Map.empty
        chat.close()
      }
    }
  }

  "closing a service built on a shared engine" should {

    "leave the engine usable" in {
      val other =
        SonarServiceFactory.withEngine(engine, apiKey = "k2", baseUrl = Some(baseUrl))
      other.close()

      replyJson(200, """{"object":"list","data":[]}""")
      Try(await(service.listAgentModels)).isSuccess shouldBe true
    }
  }
}
