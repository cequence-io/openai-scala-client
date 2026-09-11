package io.cequence.openaiscala.vertexai.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.google.cloud.vertexai.api.{
  Candidate,
  CodeExecutionResult => VertexCodeExecutionResult,
  Content,
  ExecutableCode,
  FunctionCall,
  GenerateContentResponse,
  GroundingChunk,
  GroundingMetadata,
  Part
}
import com.google.protobuf.{ByteString, Struct}
import com.google.protobuf.util.JsonFormat
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.util.Base64

/** [[VertexAIChatChunks.toChatChunks]]: streamed protobuf responses -> typed chunks. */
class VertexAIStreamedToChatChunksSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("vertexai-streamed-to-chat-chunks")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def response(
    parts: Seq[Part],
    finishReason: Option[Candidate.FinishReason] = None,
    grounding: Option[GroundingMetadata] = None
  ): GenerateContentResponse = {
    val candidate = Candidate.newBuilder().setIndex(0)
    val content = Content.newBuilder().setRole("model")
    parts.foreach(content.addParts)
    candidate.setContent(content)
    finishReason.foreach(candidate.setFinishReason)
    grounding.foreach(candidate.setGroundingMetadata)

    val builder = GenerateContentResponse.newBuilder().addCandidates(candidate)
    builder.getUsageMetadataBuilder
      .setPromptTokenCount(10)
      .setCandidatesTokenCount(5)
      .setTotalTokenCount(15)
    builder.build()
  }

  private def struct(json: String): Struct = {
    val b = Struct.newBuilder()
    JsonFormat.parser().merge(json, b)
    b.build()
  }

  private def run(responses: GenerateContentResponse*): Seq[ChatChunk] =
    Source(responses.toList)
      .via(VertexAIChatChunks.toChatChunks("gemini-test"))
      .runWith(Sink.seq)
      .futureValue

  "VertexAIChatChunks.toChatChunks" should {

    "map thought parts, text, a function call and STOP" in {
      val out = run(
        response(Seq(Part.newBuilder().setText("hmm").setThought(true).build())),
        response(Seq(Part.newBuilder().setText("Sure.").build())),
        response(
          Seq(
            Part
              .newBuilder()
              .setFunctionCall(
                FunctionCall
                  .newBuilder()
                  .setName("get_weather")
                  .setArgs(struct("""{"location":"Oslo"}"""))
              )
              .setThoughtSignature(ByteString.copyFromUtf8("sig"))
              .build()
          ),
          finishReason = Some(Candidate.FinishReason.STOP)
        )
      )

      out.head shouldBe Start("vertexai", "gemini-test")
      out(1) shouldBe Thinking("hmm")
      out(2) shouldBe Text("Sure.")
      out(3) match {
        case ToolCallStart(0, _, "get_weather", false) => succeed
        case other                                     => fail(s"Unexpected $other")
      }
      val callId = out(4) match {
        case ToolCall(0, id, "get_weather", args, false) =>
          Json.parse(args) shouldBe Json.obj("location" -> "Oslo")
          id
        case other => fail(s"Unexpected $other")
      }
      // the function call's signature is paired with its call id
      out(5) shouldBe ThinkingSignature(
        Base64.getEncoder.encodeToString("sig".getBytes("UTF-8")),
        Some(callId)
      )
      out(6) shouldBe Finish(FinishReason.tool_calls, Some("STOP"))
      out(7) match {
        case Usage(u) => u.prompt_tokens shouldBe 10
        case other    => fail(s"Unexpected $other")
      }
    }

    "pair executable code with its result, and map grounding to web search + citations" in {
      val grounding = GroundingMetadata
        .newBuilder()
        .addWebSearchQueries("jupiter news")
        .addGroundingChunks(
          GroundingChunk
            .newBuilder()
            .setWeb(
              GroundingChunk.Web.newBuilder().setUri("https://ex.com").setTitle("Example")
            )
        )
        .build()

      val out = run(
        response(
          Seq(
            Part
              .newBuilder()
              .setExecutableCode(
                ExecutableCode
                  .newBuilder()
                  .setLanguage(ExecutableCode.Language.PYTHON)
                  .setCode("print(1)")
              )
              .setThoughtSignature(ByteString.copyFromUtf8("code-sig"))
              .build()
          )
        ),
        response(
          Seq(
            Part
              .newBuilder()
              .setCodeExecutionResult(
                VertexCodeExecutionResult
                  .newBuilder()
                  .setOutcome(VertexCodeExecutionResult.Outcome.OUTCOME_OK)
                  .setOutput("1\n")
              )
              .build(),
            Part.newBuilder().setText("Done.").build()
          ),
          finishReason = Some(Candidate.FinishReason.MAX_TOKENS),
          grounding = Some(grounding)
        )
      )

      val callId = out.collectFirst { case ToolCallStart(0, id, "code_execution", true) =>
        id
      }.get
      out(2) shouldBe ToolCall(
        0,
        callId,
        "code_execution",
        "{\"language\":\"python\",\"code\":\"print(1)\"}",
        serverSide = true
      )
      out(3) shouldBe CodeExecution(callId, Some("python"), "print(1)")
      // a signature on a non-text / non-function-call part is surfaced too (no call id)
      out(4) shouldBe ThinkingSignature(
        Base64.getEncoder.encodeToString("code-sig".getBytes("UTF-8")),
        None
      )
      out(5) shouldBe ToolResult(
        callId,
        "code_execution",
        Json.obj("outcome" -> "OUTCOME_OK", "output" -> "1\n"),
        Some("1\n"),
        isError = false
      )
      out(6) shouldBe CodeExecutionResult(
        callId,
        Some("1\n"),
        isError = false,
        Json.obj("outcome" -> "OUTCOME_OK", "output" -> "1\n")
      )
      out(7) shouldBe Text("Done.")
      out(8) shouldBe WebSearch("", Seq("jupiter news"))
      out(9) shouldBe WebSearchResult(
        "",
        Seq(WebSearchResultItem(Some("Example"), "https://ex.com")),
        Json.arr(Json.obj("uri" -> "https://ex.com", "title" -> "Example"))
      )
      out(10) shouldBe Citation(
        None,
        Some("https://ex.com"),
        Some("Example"),
        Json.obj("uri" -> "https://ex.com", "title" -> "Example")
      )
      out(11) shouldBe Finish(FinishReason.length, Some("MAX_TOKENS"))
      out(12) shouldBe a[Usage]
    }
  }
}
