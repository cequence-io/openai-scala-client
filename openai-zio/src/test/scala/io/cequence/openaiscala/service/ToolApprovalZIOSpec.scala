package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool, UserMessage}
import io.cequence.wsclient.stream.AsyncStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import zio.{Runtime, Unsafe, ZIO}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._

/**
 * The ZIO view's tool-approval helper: the decision is a ZIO effect, run on the view's runtime
 * when the run pauses, and its answer resumes the run - one joined ZStream.
 */
class ToolApprovalZIOSpec extends AnyWordSpec with Matchers {

  private val ask =
    ToolApprovalRequest("a", "tool_a", "{}", Some("deepwiki"), "run1", Json.obj("id" -> "a"))

  // a streamed service that pauses once for approval, then (resumed) answers
  private class Scripted extends OpenAIChatCompletionStreamedServiceExtra {
    val rounds = new ConcurrentLinkedQueue[Seq[String]]()

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): AsyncStream[ChatCompletionChunkResponse] =
      AsyncStream.empty

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): AsyncStream[ChatChunk] =
      AsyncStream.defer {
        val decisions = settings.toolApprovalDecisions
        rounds.add(decisions.map(d => s"${d.request.requestId}:${d.approve}"))
        if (decisions.isEmpty)
          AsyncStream[ChatChunk](
            Start("run1", "m"),
            ask,
            Finish(FinishReason.approval_required, Some("approval_required"))
          )
        else
          AsyncStream[ChatChunk](Text("done"), Finish(FinishReason.stop, Some("stop")))
      }

    override def close(): Unit = ()
  }

  private def run[A](effect: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe(implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    )

  "createChatToolCompletionStreamedWithApprovals on the ZIO view" should {

    "ask the ZIO callback when the run pauses and resume with its decision" in {
      val scripted = new Scripted
      val service = OpenAIChatCompletionStreamedServiceExtraZIO(scripted)
      val asked = new ConcurrentLinkedQueue[String]()

      val chunks = run(
        service
          .createChatToolCompletionStreamedWithApprovals(
            Seq(UserMessage("hi")),
            settings = CreateChatCompletionSettings("m")
          ) { request =>
            ZIO.succeed(asked.add(request.requestId)).as(request.approve)
          }
          .runCollect
      )

      asked.asScala.toList shouldBe List("a")
      scripted.rounds.asScala.toList shouldBe List(Nil, Seq("a:true"))
      chunks.toList shouldBe List(
        Start("run1", "m"),
        Text("done"),
        Finish(FinishReason.stop, Some("stop"))
      )
    }

    "fail the stream with the callback's failure" in {
      val service = OpenAIChatCompletionStreamedServiceExtraZIO(new Scripted)
      val boom = new RuntimeException("no decision")

      val exit = Unsafe.unsafe(implicit unsafe =>
        Runtime.default.unsafe.run(
          service
            .createChatToolCompletionStreamedWithApprovals(
              Seq(UserMessage("hi")),
              settings = CreateChatCompletionSettings("m")
            )(_ => ZIO.fail(boom))
            .runCollect
        )
      )

      exit.causeOption.flatMap(_.failureOption) shouldBe Some(boom)
    }
  }
}
