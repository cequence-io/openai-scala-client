package io.cequence.openaiscala.domain.response

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.responsesapi.{ModelStatus, Response, TextResponseConfig}
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.MCPApprovalRequest
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration._

/** The provider-neutral pieces of human approval: the chunk, its decisions and resume key. */
class ToolApprovalSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("tool-approval-spec")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private val request = ToolApprovalRequest(
    requestId = "mcpr_1",
    toolName = "ask_wiki_question",
    arguments = """{"repoName":"a/b"}""",
    serverName = Some("deepwiki"),
    runId = "resp_1",
    raw = Json.obj("type" -> "mcp_approval_request", "id" -> "mcpr_1")
  )

  "ToolApprovalRequest" should {

    "round-trip through the chunk JSON format, with the finish reason" in {
      Json.toJson[ChatChunk](request).as[ChatChunk] shouldBe request
      (Json.toJson[ChatChunk](request) \ "type").as[String] shouldBe "tool_approval_request"

      val finish: ChatChunk = Finish(FinishReason.approval_required, Some("requires_action"))
      Json.toJson(finish).as[ChatChunk] shouldBe finish
      FinishReason.fromOpenAI("approval_required") shouldBe FinishReason.approval_required
    }

    "turn into decisions, a reason only with a denial" in {
      request.approve shouldBe ToolApprovalDecision(request, approve = true)
      request.deny("no") shouldBe ToolApprovalDecision(request, approve = false, Some("no"))
      request.deny() shouldBe ToolApprovalDecision(request, approve = false, None)
      an[IllegalArgumentException] should be thrownBy
        ToolApprovalDecision(request, approve = true, Some("why"))
    }
  }

  "the assembled result" should {

    val chunks = Seq(
      Start("resp_1", "gpt-5.4-mini"),
      Text("Let me check."),
      request,
      request.copy(requestId = "mcpr_2"),
      Finish(FinishReason.approval_required, Some("completed"))
    )

    "collect the pending requests in both folds, and answer them all" in {
      val folded = chunks.foldLeft(AssembledChatCompletion.empty)(_.add(_))
      val built = Await.result(Source(chunks.toList).assembled, 5.seconds)

      Seq(folded, built).foreach { assembled =>
        assembled.awaitingApproval shouldBe true
        assembled.toolApprovalRequests.map(_.requestId) shouldBe Seq("mcpr_1", "mcpr_2")
        assembled.finishReason shouldBe Some(FinishReason.approval_required)
        assembled.approveAll.map(_.approve) shouldBe Seq(true, true)
        assembled.denyAll(Some("no")).map(d => (d.approve, d.reason)) shouldBe
          Seq((false, Some("no")), (false, Some("no")))
      }
      Await.result(
        Source(chunks.toList).toolApprovalRequests.runFold(0)(
          (
            n,
            _
          ) => n + 1
        ),
        5.seconds
      ) shouldBe 2
    }

    "forget the requests of a restarted stream" in {
      val restarted = chunks :+ Retry(2) :+ Start("resp_2", "gpt-5.4-mini") :+ Text("done")
      restarted
        .foldLeft(AssembledChatCompletion.empty)(_.add(_))
        .awaitingApproval shouldBe false
      Await
        .result(Source(restarted.toList).assembled, 5.seconds)
        .awaitingApproval shouldBe false
    }
  }

  "ToolApprovalSettingsOps" should {

    val settings = CreateChatCompletionSettings("gpt-5.4-mini")

    "carry the decisions in the settings, and clear them with an empty list" in {
      val withDecisions = settings.setToolApprovalDecisions(Seq(request.approve))
      withDecisions.toolApprovalDecisions shouldBe Seq(request.approve)
      withDecisions.extra_params.keySet shouldBe ToolApprovalSettingsOps.knownParams
      withDecisions.setToolApprovalDecisions(Nil).extra_params shouldBe empty
      settings.toolApprovalDecisions shouldBe empty
    }

    "tell a service that cannot resume to refuse, and nothing otherwise" in {
      ToolApprovalSettingsOps.unsupportedDecisions(settings, "Gemini") shouldBe None
      ToolApprovalSettingsOps
        .unsupportedDecisions(
          settings.setToolApprovalDecisions(Seq(request.approve)),
          "Gemini"
        )
        .map(_.getMessage)
        .getOrElse("") should include("Gemini cannot resume")
    }

    "read the requests of a paused Responses API response" in {
      val response = Response(
        createdAt = new java.util.Date(),
        id = "resp_9",
        model = "gpt-5.4-mini",
        output = Seq(MCPApprovalRequest("{}", "mcpr_9", "roll", "dmcp")),
        parallelToolCalls = true,
        status = ModelStatus.Completed,
        text =
          TextResponseConfig(io.cequence.openaiscala.domain.responsesapi.ResponseFormat.Text)
      )
      val requests = ToolApprovalSettingsOps.toolApprovalRequests(Some(response))
      requests.map(r => (r.requestId, r.toolName, r.serverName, r.runId)) shouldBe
        Seq(("mcpr_9", "roll", Some("dmcp"), "resp_9"))
      (requests.head.raw \ "type").as[String] shouldBe "mcp_approval_request"
      ToolApprovalSettingsOps.toolApprovalRequests(Some("not a response")) shouldBe empty
    }
  }
}
