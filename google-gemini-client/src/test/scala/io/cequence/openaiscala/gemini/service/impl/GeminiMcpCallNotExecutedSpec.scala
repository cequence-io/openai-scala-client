package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.{
  FunctionCallSpec,
  JsonSchema,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.domain.ChatRole.Model
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  GenerateContentResponse,
  UsageMetadata,
  FinishReason => GeminiFinishReason
}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.{
  Content,
  McpServer,
  Part,
  StreamableHttpTransport,
  Tool
}
import io.cequence.openaiscala.gemini.service.{
  GeminiScalaMcpCallNotExecutedException,
  GeminiService
}
import io.cequence.openaiscala.{OpenAIScalaServerErrorException, Retryable}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}

/**
 * The plain path's answer to a `Tool.McpServers` call Gemini never ran: a typed, retryable
 * failure rather than an empty answer, so a retry adapter re-issues the request.
 */
class GeminiMcpCallNotExecutedSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val settings =
    CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash).setGeminiTools(
      Seq(
        Tool.McpServers(
          Seq(
            McpServer("github", StreamableHttpTransport("https://api.githubcopilot.com/mcp"))
          )
        )
      )
    )

  private def response(parts: Seq[Part]): GenerateContentResponse =
    GenerateContentResponse(
      candidates = Seq(
        Candidate(
          content = Content(parts, Some(Model)),
          finishReason = Some(GeminiFinishReason.STOP),
          index = Some(0)
        )
      ),
      usageMetadata = UsageMetadata(promptTokenCount = 10, totalTokenCount = 12),
      modelVersion = "gemini-2.5-flash"
    )

  private def adapterReturning(r: GenerateContentResponse)
    : OpenAIGeminiChatCompletionService = {
    val underlying = mock(classOf[GeminiService])
    when(underlying.generateContent(any(), any())).thenReturn(Future.successful(r))
    new OpenAIGeminiChatCompletionService(underlying)
  }

  private val danglingCall =
    Part.FunctionCall(None, "github_search_repositories", Map("query" -> "org:x"))

  "createChatCompletion" should {

    "fail with a retryable server error when the MCP call was never run" in {
      val e = adapterReturning(response(Seq(danglingCall)))
        .createChatCompletion(Seq(UserMessage("list repos")), settings)
        .failed
        .futureValue

      e shouldBe an[OpenAIScalaServerErrorException]
      Retryable(e.asInstanceOf[OpenAIScalaServerErrorException]) shouldBe true
      e.getCause shouldBe a[GeminiScalaMcpCallNotExecutedException]
      e.getMessage should include("github_search_repositories")
    }

    "not fail when the call got its response" in {
      val answered = response(
        Seq(
          danglingCall,
          Part.FunctionResponse(None, "github_search_repositories", Map("result" -> "app")),
          Part.Text("The repository is app.")
        )
      )

      val out = adapterReturning(answered)
        .createChatCompletion(Seq(UserMessage("list repos")), settings)
        .futureValue

      out.contentHead shouldBe "The repository is app."
    }

    "fail the same way for a bare-named call - createChatCompletion declares no client tools" in {
      val e = adapterReturning(
        response(Seq(Part.FunctionCall(None, "search_repositories", Map("query" -> "org:x"))))
      ).createChatCompletion(Seq(UserMessage("list repos")), settings).failed.futureValue

      e shouldBe an[OpenAIScalaServerErrorException]
      e.getCause shouldBe a[GeminiScalaMcpCallNotExecutedException]
    }

    "keep a declared client tool a plain tool call on createChatToolCompletion" in {
      val weather = FunctionTool(
        name = "get_weather",
        parameters = JsonSchema.Object(
          properties = Seq("location" -> JsonSchema.String()),
          required = Seq("location")
        )
      )
      val out = adapterReturning(
        response(Seq(Part.FunctionCall(Some("c1"), "get_weather", Map("location" -> "Oslo"))))
      ).createChatToolCompletion(Seq(UserMessage("weather?")), Seq(weather), None, settings)
        .futureValue

      out.choices.head.message.tool_calls
        .map(_._2.asInstanceOf[FunctionCallSpec].name) shouldBe
        Seq("get_weather")
    }

    "not fail without MCP servers - a function call is then always the caller's" in {
      val out = adapterReturning(
        response(Seq(Part.FunctionCall(Some("c1"), "get_weather", Map("location" -> "Oslo"))))
      ).createChatCompletion(
        Seq(UserMessage("weather?")),
        CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash)
      ).futureValue

      out.contentHead shouldBe ""
    }
  }

  "GeminiScalaMcpCallNotExecutedException" should {

    "repack to the retryable OpenAI server error" in {
      val repacked = repackAsOpenAIException[Unit]
        .apply(new GeminiScalaMcpCallNotExecutedException("boom"))
        .failed
        .futureValue

      repacked shouldBe an[OpenAIScalaServerErrorException]
      Retryable(repacked.asInstanceOf[OpenAIScalaServerErrorException]) shouldBe true
    }
  }
}
