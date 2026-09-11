package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.responseStreamEventReads
import io.cequence.openaiscala.domain.responsesapi.ResponseStreamEvent
import io.cequence.openaiscala.domain.responsesapi.ResponseStreamEvent._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.ExecutionContext

/**
 * Parsing of streamed Responses API events ([[ResponseStreamEvent]]) - fixtures mirror the
 * live SSE payloads captured on 2026-09-10 - and [[ChatChunks.fromResponseEvents]].
 */
class ResponseStreamChunksSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("response-stream-chunks")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def event(json: String): ResponseStreamEvent =
    Json.parse(json).as[ResponseStreamEvent]

  private val created = event(
    """{"type":"response.created","sequence_number":0,"response":{"id":"resp_1","object":"response","model":"gpt-5.4-2026-03-05","status":"in_progress","output":[]}}"""
  )
  private val inProgress = event(
    """{"type":"response.in_progress","sequence_number":1,"response":{"id":"resp_1","status":"in_progress"}}"""
  )
  private val reasoningAdded = event(
    """{"type":"response.output_item.added","output_index":0,"sequence_number":2,"item":{"id":"rs_1","type":"reasoning","content":[],"summary":[]}}"""
  )
  private val summaryDelta = event(
    """{"type":"response.reasoning_summary_text.delta","delta":"**Planning**","item_id":"rs_1","output_index":0,"sequence_number":4,"summary_index":0}"""
  )
  private val reasoningDone = event(
    """{"type":"response.output_item.done","output_index":0,"sequence_number":5,"item":{"id":"rs_1","type":"reasoning","encrypted_content":"gAAAA","content":[],"summary":[{"type":"summary_text","text":"**Planning**"}]}}"""
  )
  private val webSearchAdded = event(
    """{"type":"response.output_item.added","output_index":1,"sequence_number":6,"item":{"id":"ws_1","type":"web_search_call","status":"in_progress"}}"""
  )
  private val webSearchSearching = event(
    """{"type":"response.web_search_call.searching","item_id":"ws_1","output_index":1,"sequence_number":7}"""
  )
  private val webSearchDone = event(
    """{"type":"response.output_item.done","output_index":1,"sequence_number":8,"item":{"id":"ws_1","type":"web_search_call","status":"completed","action":{"type":"search","queries":["jupiter news","juice mission"],"query":"jupiter news"}}}"""
  )
  private val codeAdded = event(
    """{"type":"response.output_item.added","output_index":2,"sequence_number":9,"item":{"id":"ci_1","type":"code_interpreter_call","status":"in_progress","code":"","container_id":"cntr_1","outputs":null}}"""
  )
  private val codeDelta = event(
    """{"type":"response.code_interpreter_call_code.delta","delta":"fib(30)","item_id":"ci_1","output_index":2,"sequence_number":10}"""
  )
  private val codeDone = event(
    """{"type":"response.code_interpreter_call_code.done","code":"fib(30)","item_id":"ci_1","output_index":2,"sequence_number":11}"""
  )
  private val codeItemDone = event(
    """{"type":"response.output_item.done","output_index":2,"sequence_number":12,"item":{"id":"ci_1","type":"code_interpreter_call","status":"completed","code":"fib(30)","container_id":"cntr_1","outputs":[{"type":"logs","logs":"832040"}]}}"""
  )
  private val functionAdded = event(
    """{"type":"response.output_item.added","output_index":3,"sequence_number":13,"item":{"id":"fc_1","type":"function_call","status":"in_progress","arguments":"","call_id":"call_1","name":"get_weather"}}"""
  )
  private val argsDelta = event(
    """{"type":"response.function_call_arguments.delta","delta":"{\"location\":\"Oslo\"}","item_id":"fc_1","output_index":3,"sequence_number":14}"""
  )
  private val argsDone = event(
    """{"type":"response.function_call_arguments.done","arguments":"{\"location\":\"Oslo\"}","item_id":"fc_1","output_index":3,"sequence_number":15}"""
  )
  private val functionDone = event(
    """{"type":"response.output_item.done","output_index":3,"sequence_number":16,"item":{"id":"fc_1","type":"function_call","status":"completed","arguments":"{\"location\":\"Oslo\"}","call_id":"call_1","name":"get_weather"}}"""
  )
  private val textDelta = event(
    """{"type":"response.output_text.delta","delta":"Jupiter","item_id":"msg_1","output_index":4,"content_index":0,"sequence_number":17}"""
  )
  private val annotation = event(
    """{"type":"response.output_text.annotation.added","item_id":"msg_1","output_index":4,"content_index":0,"annotation_index":0,"sequence_number":18,"annotation":{"type":"url_citation","start_index":0,"end_index":7,"url":"https://ex.com","title":"Example"}}"""
  )
  private val completed = event(
    """{"type":"response.completed","sequence_number":19,"response":{"id":"resp_1","status":"completed","output":[],"usage":{"input_tokens":100,"input_tokens_details":{"cached_tokens":0},"output_tokens":40,"output_tokens_details":{"reasoning_tokens":30},"total_tokens":140}}}"""
  )

  "ResponseStreamEvent parsing" should {

    "dispatch on the JSON type and keep unknown events" in {
      created shouldBe a[ResponseCreated]
      created.asInstanceOf[ResponseCreated].model shouldBe "gpt-5.4-2026-03-05"
      inProgress shouldBe a[ResponseInProgress]
      summaryDelta shouldBe ReasoningSummaryTextDelta("rs_1", 0, 0, "**Planning**")
      webSearchSearching shouldBe a[ToolCallStatus]
      webSearchSearching.eventType shouldBe "response.web_search_call.searching"
      codeDelta shouldBe CodeInterpreterCodeDelta("ci_1", 2, "fib(30)")
      argsDone shouldBe FunctionCallArgumentsDone("fc_1", 3, "{\"location\":\"Oslo\"}")
      textDelta shouldBe OutputTextDelta("msg_1", 4, 0, "Jupiter")

      functionAdded match {
        case OutputItemAdded(3, "function_call", Some("fc_1"), item, _) =>
          item shouldBe defined
        case other => fail(s"Unexpected $other")
      }
      // outputs: null does not prevent the raw item from being carried along
      codeAdded match {
        case OutputItemAdded(2, "code_interpreter_call", Some("ci_1"), _, raw) =>
          (raw \ "item" \ "container_id").as[String] shouldBe "cntr_1"
        case other => fail(s"Unexpected $other")
      }

      event("""{"type":"response.something_new","sequence_number":99,"foo":1}""") match {
        case UnknownEvent("response.something_new", raw) => (raw \ "foo").as[Int] shouldBe 1
        case other                                       => fail(s"Unexpected $other")
      }

      event(
        """{"type":"error","code":"rate_limit","message":"slow down","sequence_number":3}"""
      ) shouldBe
        ErrorEvent(
          Some("rate_limit"),
          "slow down",
          None,
          Json.parse(
            """{"type":"error","code":"rate_limit","message":"slow down","sequence_number":3}"""
          )
        )

      completed match {
        case ResponseCompleted("resp_1", Some(usage), _) => usage.totalTokens shouldBe 140
        case other                                       => fail(s"Unexpected $other")
      }
    }
  }

  private def run(events: ResponseStreamEvent*): Seq[ChatChunk] =
    Source(events.toList).via(ChatChunks.fromResponseEvents).runWith(Sink.seq).futureValue

  "ChatChunks.fromResponseEvents" should {

    "map a full reasoning + web search + code interpreter + function call + text stream" in {
      val out = run(
        created,
        inProgress,
        reasoningAdded,
        summaryDelta,
        reasoningDone,
        webSearchAdded,
        webSearchSearching,
        webSearchDone,
        codeAdded,
        codeDelta,
        codeDone,
        codeItemDone,
        functionAdded,
        argsDelta,
        argsDone,
        functionDone,
        textDelta,
        annotation,
        completed
      )

      val logsJson: JsValue = Json.parse("""[{"type":"logs","logs":"832040"}]""")

      out.take(4) shouldBe Seq(
        Start("resp_1", "gpt-5.4-2026-03-05"),
        Thinking("**Planning**"),
        ThinkingSignature("gAAAA"),
        ToolCallStart(0, "ws_1", "web_search", serverSide = true)
      )
      out(4) match {
        case Other("response.web_search_call.searching", _) => succeed
        case other                                          => fail(s"Unexpected $other")
      }
      out(5) match {
        case ToolCall(0, "ws_1", "web_search", args, true) =>
          (Json.parse(args) \ "queries")
            .as[Seq[String]] shouldBe Seq("jupiter news", "juice mission")
        case other => fail(s"Unexpected $other")
      }
      out(6) shouldBe WebSearch("ws_1", Seq("jupiter news", "juice mission"))
      out.slice(7, 15) shouldBe Seq(
        ToolCallStart(1, "ci_1", "code_interpreter", serverSide = true),
        ToolCallDelta(1, "fib(30)"),
        ToolCall(1, "ci_1", "code_interpreter", "{\"code\":\"fib(30)\"}", serverSide = true),
        CodeExecution("ci_1", Some("python"), "fib(30)"),
        ToolResult("ci_1", "code_interpreter", logsJson, Some("832040"), isError = false),
        CodeExecutionResult("ci_1", Some("832040"), isError = false, logsJson),
        ToolCallStart(2, "call_1", "get_weather", serverSide = false),
        ToolCallDelta(2, "{\"location\":\"Oslo\"}")
      )
      out(15) shouldBe ToolCall(
        2,
        "call_1",
        "get_weather",
        "{\"location\":\"Oslo\"}",
        serverSide = false
      )
      // output_item.done for the function call does not emit a second ToolCall
      out(16) shouldBe Text("Jupiter")
      out(17) match {
        case Citation(None, Some("https://ex.com"), Some("Example"), _) => succeed
        case other => fail(s"Unexpected $other")
      }
      out(18) shouldBe Finish(FinishReason.tool_calls, Some("completed"))
      out(19) match {
        case Usage(u) =>
          u.prompt_tokens shouldBe 100
          u.completion_tokens shouldBe Some(40)
          u.completion_tokens_details.flatMap(_.reasoning_tokens) shouldBe Some(30)
        case other => fail(s"Unexpected $other")
      }
      out.size shouldBe 20
    }

    "finish with stop for a text-only response and with length when incomplete" in {
      run(created, textDelta, completed).last shouldBe a[Usage]
      run(created, textDelta, completed).dropRight(1).last shouldBe
        Finish(FinishReason.stop, Some("completed"))

      val incomplete = event(
        """{"type":"response.incomplete","sequence_number":5,"response":{"id":"resp_1","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}}"""
      )
      run(created, textDelta, incomplete).last shouldBe
        Finish(FinishReason.length, Some("max_output_tokens"))
    }

    "fail the stream on response.failed and error events" in {
      val failed = event(
        """{"type":"response.failed","sequence_number":5,"response":{"id":"resp_1","status":"failed","error":{"code":"server_error","message":"boom"},"output":[]}}"""
      )
      val ex = Source(List(created, failed))
        .via(ChatChunks.fromResponseEvents)
        .runWith(Sink.seq)
        .failed
        .futureValue
      ex shouldBe an[OpenAIScalaClientException]
      ex.getMessage should include("boom")

      val err = event(
        """{"type":"error","code":"rate_limit","message":"slow down","sequence_number":3}"""
      )
      Source(List(created, err))
        .via(ChatChunks.fromResponseEvents)
        .runWith(Sink.seq)
        .failed
        .futureValue
        .getMessage should include("slow down")
    }

    "pass refusals, partial images and unknown items through as typed chunks or Other" in {
      val refusal = event(
        """{"type":"response.refusal.delta","delta":"I can't","item_id":"msg_1","output_index":0,"content_index":0,"sequence_number":2}"""
      )
      val partialImage = event(
        """{"type":"response.image_generation_call.partial_image","item_id":"ig_1","output_index":1,"partial_image_index":0,"partial_image_b64":"AAAA","sequence_number":3}"""
      )
      val weirdItem = event(
        """{"type":"response.output_item.added","output_index":2,"sequence_number":4,"item":{"id":"x_1","type":"hologram_call"}}"""
      )

      run(created, refusal, partialImage, weirdItem).drop(1) shouldBe Seq(
        Refusal("I can't"),
        Image(None, Some("AAAA"), None),
        Other(
          "output_item.hologram_call",
          Json.parse(
            """{"type":"response.output_item.added","output_index":2,"sequence_number":4,"item":{"id":"x_1","type":"hologram_call"}}"""
          )
        )
      )
    }

    "key id-less tool-call items by output index so two of them do not collide" in {
      val first = event(
        """{"type":"response.output_item.added","output_index":0,"sequence_number":1,"item":{"type":"function_call","call_id":"call_a","name":"f","arguments":""}}"""
      )
      val second = event(
        """{"type":"response.output_item.added","output_index":1,"sequence_number":2,"item":{"type":"function_call","call_id":"call_b","name":"g","arguments":""}}"""
      )
      val firstDone = event(
        """{"type":"response.output_item.done","output_index":0,"sequence_number":3,"item":{"type":"function_call","call_id":"call_a","name":"f","arguments":"{\"a\":1}"}}"""
      )
      val secondDone = event(
        """{"type":"response.output_item.done","output_index":1,"sequence_number":4,"item":{"type":"function_call","call_id":"call_b","name":"g","arguments":"{\"b\":2}"}}"""
      )

      run(created, first, second, firstDone, secondDone).drop(1) shouldBe Seq(
        ToolCallStart(0, "call_a", "f", serverSide = false),
        ToolCallStart(1, "call_b", "g", serverSide = false),
        ToolCall(0, "call_a", "f", "{\"a\":1}", serverSide = false),
        ToolCall(1, "call_b", "g", "{\"b\":2}", serverSide = false)
      )
    }
  }

  "responseStreamEventReads" should {

    "degrade a recognized event type with a missing field to UnknownEvent instead of failing" in {
      val json = Json.parse(
        """{"type":"response.output_text.delta","delta":"hi","item_id":"msg_1","output_index":0}"""
      )

      json.as[ResponseStreamEvent] shouldBe UnknownEvent("response.output_text.delta", json)
    }
  }
}
