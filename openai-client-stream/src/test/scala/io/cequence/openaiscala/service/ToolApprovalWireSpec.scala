package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.response.{
  AssembledChatCompletion,
  ChatChunk,
  ToolApprovalDecision,
  UsageInfo
}
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{
  MCPAllowedTools,
  MCPRequireApproval,
  MCPTool,
  MCPToolFilter
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  FunctionCallSpec,
  ModelId,
  SystemMessage,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

/**
 * Human approval on OpenAI (Responses API MCP tools with `requireApproval`), end to end
 * against a local server replaying streams recorded live (2026-09-29, `gpt-5.4-mini` with
 * reasoning + DeepWiki): a run that pauses TWICE (read_wiki_structure, then
 * ask_wiki_question), resumed statefully (stored, continued by `previous_response_id` with
 * only the answers), plus the stateless replay used with an explicit `store = false`.
 */
class ToolApprovalWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("tool-approval-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private def resource(name: String): String = {
    val source = Source.fromResource(s"tool-approval/$name")
    try source.mkString
    finally source.close()
  }

  // stateful run: pause 1 (read_wiki_structure) -> pause 2 (ask_wiki_question) -> the answer
  private val stateful1 = resource("openai-mcp-stateful-1.sse")
  private val stateful2 = resource("openai-mcp-stateful-2.sse")
  private val stateful3 = resource("openai-mcp-stateful-3.sse")
  private val run1 = "resp_08f104d4ec2b32e4006abb8d35c64c87d28cccc7aa5bea0049"
  private val run2 = "resp_08f104d4ec2b32e4006abb8d38863087d2a684ca4a88576260"

  // stateless (store = false) run: one pause, resumed by replay
  private val statelessPaused = resource("openai-mcp-paused.sse")
  private val statelessResumed = resource("openai-mcp-resumed.sse")
  private val pausedSync = resource("openai-mcp-paused.json")

  private val chatBody =
    """{"id":"chatcmpl_1","object":"chat.completion","created":1700000000,"model":"gpt-5.4",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  // every request received, in order (GETs of stored responses separately)
  @volatile private var requests: Vector[(String, JsObject)] = Vector.empty
  @volatile private var gets: Vector[String] = Vector.empty
  // client function calls added to the stored (paused) responses GET returns
  @volatile private var pausedFunctionCalls: Seq[String] = Nil

  // a stored response as GET /responses/{id} returns it - the one its stream completed with
  private def storedResponse(sse: String): JsObject = {
    val completed = sse
      .split("\n")
      .collect { case line if line.startsWith("data: ") => Json.parse(line.drop(6)) }
      .find(event => (event \ "type").asOpt[String].contains("response.completed"))
      .getOrElse(fail("no response.completed event"))
    val response = (completed \ "response").as[JsObject]
    val calls = pausedFunctionCalls.map(id =>
      Json.obj(
        "type" -> "function_call",
        "id" -> s"fc_$id",
        "call_id" -> id,
        "name" -> "get_time",
        "arguments" -> "{}",
        "status" -> "completed"
      )
    )
    response ++ Json.obj("output" -> ((response \ "output").as[Seq[JsObject]] ++ calls))
  }

  private def lastBody(pathSuffix: String): JsObject =
    requests.reverse
      .find(_._1.endsWith(pathSuffix))
      .map(_._2)
      .getOrElse(fail(s"no $pathSuffix call"))

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  private lazy val full = OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  private lazy val chatOnly =
    OpenAIChatCompletionServiceFactory.withEngine(engine, coreUrl, context)
  private lazy val chatOnlyStreamed =
    OpenAIChatCompletionServiceFactory.withStreaming.withEngine(engine, coreUrl, context)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit =
          if (exchange.getRequestMethod == "GET") {
            val path = exchange.getRequestURI.getPath
            synchronized { gets = gets :+ path }
            val stored = path.split("/").last match {
              case `run1` => storedResponse(stateful1)
              case `run2` => storedResponse(stateful2)
              case other  => fail(s"unexpected GET of $other")
            }
            val bytes = Json.stringify(stored).getBytes(StandardCharsets.UTF_8)
            exchange.getResponseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
            exchange.close()
          } else handlePost(exchange)

        private def handlePost(exchange: HttpExchange): Unit = {
          val body = Json
            .parse(new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8))
            .as[JsObject]
          val path = exchange.getRequestURI.getPath
          synchronized { requests = requests :+ (path -> body) }

          val streamed = (body \ "stream").asOpt[Boolean].contains(true)
          val stored = !(body \ "store").asOpt[Boolean].contains(false)
          val replaying = (body \ "input")
            .asOpt[Seq[JsValue]]
            .exists(_.exists(i => (i \ "type").asOpt[String].contains("mcp_approval_request")))

          val (contentType, response) =
            if (!path.endsWith("/responses")) ("application/json", chatBody)
            else if (!streamed) ("application/json", pausedSync)
            else
              (
                "text/event-stream",
                (body \ "previous_response_id").asOpt[String] match {
                  case Some(`run1`) => stateful2
                  case Some(`run2`) => stateful3
                  case _ if !stored => if (replaying) statelessResumed else statelessPaused
                  case _            => stateful1
                }
              )

          exchange.getResponseHeaders.add("Content-Type", contentType)
          val bytes = response.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    fullStreamed.close()
    full.close()
    chatOnly.close()
    chatOnlyStreamed.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private val deepwiki = MCPServerTool(
    "deepwiki",
    "https://mcp.deepwiki.com/mcp",
    allowedTools = Seq("read_wiki_structure", "ask_wiki_question"),
    requireApproval = true
  )
  private val messages: Seq[BaseMessage] = Seq(
    SystemMessage("Use the deepwiki tools one at a time."),
    UserMessage("List the wiki topics, then ask which ws-client version the project uses.")
  )
  private val settings = CreateChatCompletionSettings("gpt-5.4-mini")

  private def typedCall(
    decisions: Seq[ToolApprovalDecision] = Nil,
    callSettings: CreateChatCompletionSettings = settings,
    callMessages: Seq[BaseMessage] = messages
  ) =
    await(
      fullStreamed
        .createChatToolCompletionStreamed(
          callMessages,
          Seq(deepwiki),
          None,
          callSettings.setToolApprovalDecisions(decisions)
        )
        .assembled
    )

  "a typed tool stream on OpenAI" should {

    "surface a paused run as a ToolApprovalRequest, then Finish(approval_required)" in {
      val chunks = await(
        fullStreamed
          .createChatToolCompletionStreamed(messages, Seq(deepwiki), None, settings)
          .runWith(Sink.seq)
      )

      val pending = chunks.collect { case r: ChatChunk.ToolApprovalRequest => r }
      pending.map(r => (r.toolName, r.serverName, r.runId)) shouldBe
        Seq(("read_wiki_structure", Some("deepwiki"), run1))
      pending.head.requestId should startWith("mcpr_")
      chunks.collect { case s: ChatChunk.Start => s.id } shouldBe Seq(run1)
      // the pending call is not on the tool layer - it runs in the resumed stream
      chunks.collect { case c: ChatChunk.ToolCall => c } shouldBe empty
      chunks.collectFirst { case f: ChatChunk.Finish => f.reason } shouldBe
        Some(FinishReason.approval_required)

      // a tool that may ask for approval makes the run stored, so it can be continued by id
      val sent = lastBody("/responses")
      (sent \ "store").as[Boolean] shouldBe true
      (sent \ "previous_response_id").toOption shouldBe None
      ((sent \ "tools").as[Seq[JsObject]].head \ "require_approval")
        .as[String] shouldBe "always"
    }

    "resume by response id with only the answers, through a second pause, to the answer" in {
      val paused1 = typedCall()
      paused1.awaitingApproval shouldBe true

      val paused2 = typedCall(paused1.approveAll)
      val resume1 = lastBody("/responses")
      (resume1 \ "previous_response_id").as[String] shouldBe run1
      (resume1 \ "store").as[Boolean] shouldBe true
      (resume1 \ "input").as[Seq[JsObject]] shouldBe Seq(
        Json.obj(
          "approval_request_id" -> paused1.toolApprovalRequests.head.requestId,
          "approve" -> true,
          "type" -> "mcp_approval_response"
        )
      )
      // tools are re-sent, system messages become instructions, the history is not re-sent
      (resume1 \ "tools").as[Seq[JsObject]] should not be empty
      (resume1 \ "instructions").as[String] shouldBe "Use the deepwiki tools one at a time."

      // the first approved call ran; the run paused again on the second one
      paused2.toolCalls.map(_.toolName) shouldBe Seq("read_wiki_structure")
      paused2.toolResults.map(_.toolName) shouldBe Seq("read_wiki_structure")
      paused2.toolApprovalRequests.map(r => (r.toolName, r.runId)) shouldBe
        Seq(("ask_wiki_question", run2))
      paused2.finishReason shouldBe Some(FinishReason.approval_required)

      val done = typedCall(paused2.approveAll)
      (lastBody("/responses") \ "previous_response_id").as[String] shouldBe run2
      done.awaitingApproval shouldBe false
      done.finishReason shouldBe Some(FinishReason.stop)
      done.toolCalls.map(_.toolName) shouldBe Seq("ask_wiki_question")
      done.text should include("wiki topics")
    }

    "join both pauses into one stream when a callback answers them" in {
      val asked = new CopyOnWriteArrayList[ChatChunk.ToolApprovalRequest]()
      val before = requests.size

      val chunks = await(
        fullStreamed
          .createChatToolCompletionStreamedWithApprovals(
            messages,
            Seq(deepwiki),
            settings = settings
          ) { request =>
            asked.add(request)
            Future.successful(request.approve)
          }
          .runWith(Sink.seq)
      )

      asked.size shouldBe 2
      (asked.get(0).toolName, asked.get(0).runId) shouldBe (("read_wiki_structure", run1))
      (asked.get(1).toolName, asked.get(1).runId) shouldBe (("ask_wiki_question", run2))

      // on the wire: the fresh run, then each pause continued by its response id
      val sent = requests.drop(before).map(_._2)
      sent.map(body => (body \ "previous_response_id").asOpt[String]) shouldBe
        Seq(None, Some(run1), Some(run2))
      sent.drop(1).map(body => (body \ "input").as[Seq[JsObject]]) shouldBe Seq(
        Seq(asked.get(0)),
        Seq(asked.get(1))
      ).map(
        _.map(r =>
          Json.obj(
            "approval_request_id" -> r.requestId,
            "approve" -> true,
            "type" -> "mcp_approval_response"
          )
        )
      )

      // one run: one Start, no pause, the ordinals continuing, the usage of all three
      chunks.collect { case s: ChatChunk.Start => s.id } shouldBe Seq(run1)
      chunks.collect { case r: ChatChunk.ToolApprovalRequest => r } shouldBe empty
      chunks.collect { case f: ChatChunk.Finish => f.reason } shouldBe Seq(FinishReason.stop)
      chunks.collect { case c: ChatChunk.ToolCall => (c.index, c.toolName) } shouldBe
        Seq((0, "read_wiki_structure"), (1, "ask_wiki_question"))

      val paused1 = typedCall()
      val paused2 = typedCall(paused1.approveAll)
      val done = typedCall(paused2.approveAll)
      val assembled = chunks.foldLeft(AssembledChatCompletion.empty)(_ add _)
      assembled.text shouldBe done.text
      assembled.toolResults.map(_.toolName) shouldBe
        Seq("read_wiki_structure", "ask_wiki_question")
      assembled.usage shouldBe
        Some(Seq(paused1, paused2, done).flatMap(_.usage).reduce(UsageInfo.sum))
      assembled.awaitingApproval shouldBe false
    }

    "send the outputs of the paused response's own client function calls with the answers" in {
      val paused = typedCall()
      val withToolOutputs = messages ++ Seq(
        AssistantToolMessage(
          content = None,
          name = None,
          tool_calls = Seq(
            "call_1" -> FunctionCallSpec("get_time", "{}"),
            "call_2" -> FunctionCallSpec("get_time", "{}")
          )
        ),
        ToolMessage(Some("12:00"), "call_1", "get_time"),
        ToolMessage(Some("12:01"), "call_2", "get_time")
      )

      // the paused response called call_1 only (call_2 is an output it already had)
      pausedFunctionCalls = Seq("call_1")
      try typedCall(paused.approveAll, callMessages = withToolOutputs)
      finally pausedFunctionCalls = Nil

      gets.last shouldBe s"/responses/$run1"
      val input = (lastBody("/responses") \ "input").as[Seq[JsObject]]
      input.map(i => (i \ "type").as[String]) shouldBe
        Seq("function_call_output", "mcp_approval_response")
      (input.head \ "call_id").as[String] shouldBe "call_1"
    }

    "not send again the tool outputs of the turn that paused (a tool-loop turn)" in {
      val loopTurn = messages ++ Seq(
        AssistantToolMessage(
          content = None,
          name = None,
          tool_calls = Seq("call_0" -> FunctionCallSpec("get_time", "{}"))
        ),
        ToolMessage(Some("12:00"), "call_0", "get_time")
      )
      val paused = typedCall(callMessages = loopTurn)
      (lastBody("/responses") \ "input")
        .as[Seq[JsObject]]
        .map(i => (i \ "type").asOpt[String].getOrElse("message")) should contain(
        "function_call_output"
      )

      typedCall(paused.approveAll, callMessages = loopTurn)
      (lastBody("/responses") \ "input")
        .as[Seq[JsObject]]
        .map(i => (i \ "type").as[String]) shouldBe
        Seq("mcp_approval_response")
    }

    "send a denial's reason, and never a reason with an approval" in {
      val paused = typedCall()
      typedCall(paused.denyAll(Some("not allowed")))

      val answer = (lastBody("/responses") \ "input").as[Seq[JsObject]].last
      (answer \ "approve").as[Boolean] shouldBe false
      (answer \ "reason").as[String] shouldBe "not allowed"

      an[IllegalArgumentException] should be thrownBy
        ToolApprovalDecision(paused.toolApprovalRequests.head, approve = true, Some("x"))
    }

    "replay the pending requests after the history when the caller opted out of storing" in {
      val noStore = settings.copy(store = Some(false))
      val paused = typedCall(callSettings = noStore)
      (lastBody("/responses") \ "store").as[Boolean] shouldBe false

      val resumed = typedCall(paused.approveAll, callSettings = noStore)
      val sent = lastBody("/responses")
      val input = (sent \ "input").as[Seq[JsObject]]
      input.map(i => (i \ "type").asOpt[String].getOrElse("message")) shouldBe
        Seq("message", "mcp_approval_request", "mcp_approval_response")
      (input(1) \ "id").as[String] shouldBe paused.toolApprovalRequests.head.requestId
      (sent \ "previous_response_id").toOption shouldBe None
      (sent \ "store").as[Boolean] shouldBe false

      resumed.finishReason shouldBe Some(FinishReason.stop)
      resumed.toolResults should not be empty
    }

    "refuse a resume without tools, mixing paused runs, or with another provider's request" in {
      val paused = typedCall()
      val decisions = settings.setToolApprovalDecisions(paused.approveAll)

      intercept[OpenAIScalaClientException](
        await(
          fullStreamed
            .createChatCompletionStreamedTyped(messages, decisions)
            .runWith(Sink.ignore)
        )
      ).getMessage should include("same tools")

      val request = paused.toolApprovalRequests.head
      intercept[OpenAIScalaClientException](
        typedCall(
          Seq(request.approve, request.copy(requestId = "mcpr_x", runId = run2).approve)
        )
      ).getMessage should include("one paused response")

      val foreign = request.copy(raw = Json.obj("type" -> "agent.tool_use"))
      intercept[OpenAIScalaClientException](typedCall(Seq(foreign.approve))).getMessage should
        include("not an OpenAI MCP approval request")
    }
  }

  "a sync tool completion on OpenAI" should {

    "report a paused run as finish_reason approval_required with its requests" in {
      val response =
        await(full.createChatToolCompletion(messages, Seq(deepwiki), None, settings))

      response.choices.head.finish_reason shouldBe Some("approval_required")
      val pending = response.toolApprovalRequests
      pending.map(_.toolName) shouldBe Seq("ask_wiki_question")
      pending.head.runId shouldBe response.id

      await(
        full.createChatToolCompletion(
          messages,
          Seq(deepwiki),
          None,
          settings.setToolApprovalDecisions(pending.map(_.deny("no")))
        )
      )
      val sent = lastBody("/responses")
      (sent \ "previous_response_id").as[String] shouldBe response.id
      (sent \ "input").as[Seq[JsObject]].map(i => (i \ "type").as[String]) shouldBe
        Seq("mcp_approval_response")
    }
  }

  "Responses-native tools (setResponsesTools)" should {

    "route to the Responses API on the full service, and fail on a chat-only one" in {
      val rawMcp = settings.setResponsesTools(
        Seq(
          MCPTool(serverLabel = "deepwiki", serverUrl = Some("https://mcp.deepwiki.com/mcp"))
        )
      )
      await(full.createChatToolCompletion(messages, Nil, None, rawMcp))
      requests.map(_._1).last should endWith("/responses")
      // a raw MCP tool without require_approval asks by default - the run is stored
      (lastBody("/responses") \ "store").as[Boolean] shouldBe true

      intercept[OpenAIScalaClientException](
        await(chatOnly.createChatToolCompletion(messages, Nil, None, rawMcp))
      ).getMessage should include("setResponsesTools")
      intercept[OpenAIScalaClientException](
        await(
          chatOnlyStreamed
            .createChatCompletionStreamedTyped(messages, rawMcp)
            .runWith(Sink.ignore)
        )
      ).getMessage should include("setResponsesTools")

      // createChatCompletion routes them too - a paused run reports approval_required
      val routed = await(full.createChatCompletion(messages, rawMcp))
      requests.map(_._1).last should endWith("/responses")
      routed.choices.head.finish_reason shouldBe Some("approval_required")

      // the entry points that cannot carry them refuse instead of dropping them
      val before = requests.size
      Seq[() => Future[_]](
        () => chatOnly.createChatCompletion(messages, rawMcp),
        () => fullStreamed.createChatCompletionStreamed(messages, rawMcp).runWith(Sink.ignore),
        () => full.createChatFunCompletion(messages, Nil, None, rawMcp),
        () =>
          chatOnlyStreamed.createChatCompletionStreamed(messages, rawMcp).runWith(Sink.ignore)
      ).foreach { call =>
        intercept[OpenAIScalaClientException](await(call())).getMessage should
          include("setResponsesTools")
      }
      requests.size shouldBe before
    }

    "store a run only when a raw MCP tool may ask for approval" in {
      def raw(requireApproval: Option[MCPRequireApproval]) =
        settings.setResponsesTools(
          Seq(
            MCPTool(
              serverLabel = "deepwiki",
              serverUrl = Some("https://mcp.deepwiki.com/mcp"),
              allowedTools = Some(MCPAllowedTools.ToolNames(Seq("read_wiki_structure"))),
              requireApproval = requireApproval
            )
          )
        )
      def stored(requireApproval: Option[MCPRequireApproval]) = {
        await(full.createChatToolCompletion(messages, Nil, None, raw(requireApproval)))
        (lastBody("/responses") \ "store").as[Boolean]
      }
      val never = MCPToolFilter(toolNames = Some(Seq("read_wiki_structure")))

      stored(None) shouldBe true // the API default is 'always'
      stored(Some(MCPRequireApproval.Setting.Always)) shouldBe true
      stored(Some(MCPRequireApproval.Setting.Never)) shouldBe false
      // 'never' covers every allowed tool - no call can ask
      stored(Some(MCPRequireApproval.Filter(never = Some(never)))) shouldBe false
      stored(
        Some(
          MCPRequireApproval.Filter(never = Some(MCPToolFilter(toolNames = Some(Seq("x")))))
        )
      ) shouldBe true
    }
  }

  "a chat-only service" should {

    "refuse approval decisions instead of starting a fresh run" in {
      val paused = typedCall()
      val decisions =
        CreateChatCompletionSettings(ModelId.gpt_5_4)
          .setToolApprovalDecisions(paused.approveAll)
      val before = requests.size

      Seq[() => Future[_]](
        () =>
          chatOnlyStreamed
            .createChatCompletionStreamedTyped(messages, decisions)
            .runWith(Sink.ignore),
        () =>
          chatOnlyStreamed
            .createChatCompletionStreamed(messages, decisions)
            .runWith(Sink.ignore),
        () => chatOnly.createChatToolCompletion(messages, Nil, None, decisions),
        () => chatOnly.createChatCompletion(messages, decisions)
      ).foreach { call =>
        intercept[OpenAIScalaClientException](await(call())).getMessage should
          include("cannot resume a run paused for tool approval")
      }
      requests.size shouldBe before
    }

    "never send the adapter-only settings keys to the chat completions API" in {
      await(
        full.createChatCompletion(
          messages,
          CreateChatCompletionSettings(ModelId.gpt_5_4).copy(extra_params =
            Map("responses_reasoning_summary" -> true, "custom_flag" -> 1)
          )
        )
      )
      val sent = lastBody("/chat/completions")
      sent.keys should not contain "responses_reasoning_summary"
      (sent \ "custom_flag").as[Int] shouldBe 1
    }
  }
}
