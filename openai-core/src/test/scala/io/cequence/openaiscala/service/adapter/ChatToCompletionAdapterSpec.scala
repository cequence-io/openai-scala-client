package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  TextCompletionChoiceInfo,
  TextCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateCompletionSettings
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ReasoningBlock,
  ReasoningMessage,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAICompletionService}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.{util => ju}
import scala.concurrent.{ExecutionContext, Future}

/**
 * A `ReasoningMessage` (carried reasoning) is no part of the legacy completion prompt: a
 * history carrying one - e.g. after a router failed over from a GPT-6 model - becomes the same
 * prompt as the one without it.
 */
class ChatToCompletionAdapterSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  // a legacy completion service recording the prompt of its last call
  private class RecordingService
      extends OpenAICompletionService
      with OpenAIChatCompletionService {
    @volatile var lastPrompt: Option[String] = None

    override def createCompletion(
      prompt: String,
      settings: CreateCompletionSettings
    ): Future[TextCompletionResponse] = {
      lastPrompt = Some(prompt)
      Future.successful(
        TextCompletionResponse(
          id = "cmpl_1",
          created = new ju.Date(0L),
          model = settings.model,
          system_fingerprint = None,
          choices = Seq(TextCompletionChoiceInfo("7", 0, None, Some("stop"))),
          usage = None
        )
      )
    }

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      Future.failed(new UnsupportedOperationException("the adapter must not call this"))

    override def close(): Unit = ()
  }

  private val settings = CreateChatCompletionSettings("text-davinci-003")

  private val reasoning =
    ReasoningMessage(
      Seq(ReasoningBlock(id = Some("rs_1"), encrypted = Some("enc")))
    )

  "chatToCompletion" should {

    "leave a ReasoningMessage out of the prompt" in {
      val underlying = new RecordingService
      val adapter = OpenAIServiceAdapters.forChatCompletionService.chatToCompletion(underlying)

      def promptOf(messages: Seq[BaseMessage]): Option[String] = {
        adapter.createChatCompletion(messages, settings).futureValue
        underlying.lastPrompt
      }

      val conversation: Seq[BaseMessage] = Seq(
        SystemMessage("Be brief."),
        UserMessage("Remember 7."),
        AssistantMessage("ok"),
        UserMessage("What was it?")
      )
      val expected = promptOf(conversation)
      expected shouldBe Some("Be brief.\nQ: Remember 7.\nA: ok\nQ: What was it?")

      promptOf(conversation.patch(2, Seq(reasoning), 0)) shouldBe expected
      promptOf(conversation :+ reasoning) shouldBe expected
    }
  }
}
