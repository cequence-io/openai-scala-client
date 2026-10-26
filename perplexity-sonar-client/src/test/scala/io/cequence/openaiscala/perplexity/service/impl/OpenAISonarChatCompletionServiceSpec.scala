package io.cequence.openaiscala.perplexity.service.impl

import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.response.ChatCompletionChoiceInfo
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ReasoningBlock,
  ReasoningMessage,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.perplexity.domain.Message
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.service.SonarService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.{util => ju}
import scala.concurrent.{ExecutionContext, Future}

/**
 * A `ReasoningMessage` (carried reasoning) means nothing to Sonar: a history carrying one -
 * e.g. after a router failed over from a GPT-6 model - is sent exactly as the one without it,
 * and the call does not fail.
 */
class OpenAISonarChatCompletionServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val reasoning =
    ReasoningMessage(
      Seq(ReasoningBlock(id = Some("rs_1"), encrypted = Some("enc")))
    )

  private val settings = CreateChatCompletionSettings("sonar")

  private val conversation: Seq[BaseMessage] = Seq(
    SystemMessage("Be brief."),
    UserMessage("Remember 7."),
    AssistantMessage("ok"),
    UserMessage("What was it?")
  )

  private val sonarConversation: Seq[Message] = Seq(
    Message.SystemMessage("Be brief."),
    Message.UserMessage("Remember 7."),
    Message.AssistantMessage("ok"),
    Message.UserMessage("What was it?")
  )

  private val answer = SonarChatCompletionResponse(
    id = "resp_1",
    created = new ju.Date(0L),
    model = "sonar",
    citations = Nil,
    choices = Seq(ChatCompletionChoiceInfo(AssistantMessage("7"), 0, Some("stop"), None)),
    usage = None
  )

  // a Sonar service answering with a text and recording the messages it was asked with
  private class Recording {
    @volatile var messages: Seq[Message] = Nil
    val underlying: SonarService = mock(classOf[SonarService])

    when(underlying.createChatCompletion(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        messages = invocation.getArgument[Seq[Message]](0)
        Future.successful(answer)
    }
    when(underlying.createChatCompletionStreamed(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        messages = invocation.getArgument[Seq[Message]](0)
        Source.empty[SonarChatCompletionChunkResponse]
    }

    val adapter = new OpenAISonarChatCompletionService(underlying)
  }

  "The Sonar adapter" should {

    "send a conversation like the one without the ReasoningMessage" in {
      def sent(messages: Seq[BaseMessage]): Seq[Message] = {
        val recording = new Recording
        recording.adapter.createChatCompletion(messages, settings).futureValue
        recording.messages
      }

      sent(conversation) shouldBe sonarConversation
      sent(conversation.patch(2, Seq(reasoning), 0)) shouldBe sonarConversation
      sent(conversation :+ reasoning) shouldBe sonarConversation
    }

    "send a streamed conversation like the one without the ReasoningMessage" in {
      def sent(messages: Seq[BaseMessage]): Seq[Message] = {
        val recording = new Recording
        recording.adapter.createChatCompletionStreamed(messages, settings)
        recording.messages
      }

      sent(conversation) shouldBe sonarConversation
      sent(conversation.patch(2, Seq(reasoning), 0)) shouldBe sonarConversation
    }
  }
}
