package io.cequence.openaiscala.service.adapter

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatCompletionBatchRequest,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * Coverage for the batch-preserving input adapter
 * (`OpenAIServiceAdapters.chatCompletionInputWithBatch` /
 * [[ChatCompletionInputBatchAdapter]]): `adaptMessages`/`adaptSettings` must apply to BOTH the
 * sync chat completion and the batch submission - the very gap the plain `chatCompletionInput`
 * wrapper has (it only intercepts the sync path, letting batch calls through unadapted).
 */
class ChatCompletionInputBatchAdapterSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("input-batch-adapter-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  // records every settings/messages pair that reaches the underlying service
  private class RecordingChatService extends OpenAIChatCompletionService {
    val seenSettings = new ListBuffer[CreateChatCompletionSettings]
    val seenMessages = new ListBuffer[Seq[BaseMessage]]

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      seenMessages += messages
      seenSettings += settings
      Future.successful(
        ChatCompletionResponse(
          id = "id",
          created = new java.util.Date(0L),
          model = settings.model,
          system_fingerprint = None,
          choices =
            Seq(ChatCompletionChoiceInfo(AssistantMessage("{}"), 0, Some("stop"), None)),
          usage = None,
          originalResponse = None
        )
      )
    }

    override def close(): Unit = ()
  }

  private val adaptSettings: CreateChatCompletionSettings => CreateChatCompletionSettings =
    settings => settings.copy(max_tokens = Some(42))

  private val adaptMessages: Seq[BaseMessage] => Seq[BaseMessage] =
    messages => messages :+ UserMessage("adapted")

  private def lastUserTexts(messages: Seq[BaseMessage]): Seq[String] =
    messages.collect { case UserMessage(content, _) => content }

  "chatCompletionInputWithBatch" should {

    "apply adaptMessages/adaptSettings to the sync chat completion" in {
      val underlyingChat = new RecordingChatService
      val underlying =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(underlyingChat, _ => ())

      val adapted =
        OpenAIServiceAdapters.forChatCompletionService.chatCompletionInputWithBatch(
          adaptMessages,
          adaptSettings
        )(underlying)

      adapted
        .createChatCompletion(Seq(UserMessage("hi")), CreateChatCompletionSettings("m1"))
        .futureValue

      underlyingChat.seenSettings.map(_.max_tokens) shouldBe Seq(Some(42))
      lastUserTexts(underlyingChat.seenMessages.head) shouldBe Seq("hi", "adapted")
    }

    "apply adaptMessages/adaptSettings to batch submissions as well" in {
      val underlyingChat = new RecordingChatService
      val underlying =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(underlyingChat, _ => ())

      val adapted =
        OpenAIServiceAdapters.forChatCompletionService.chatCompletionInputWithBatch(
          adaptMessages,
          adaptSettings
        )(underlying)

      val batchInfo = adapted
        .createChatCompletionBatch(
          Seq(
            ChatCompletionBatchRequest("a", Seq(UserMessage("question a"))),
            ChatCompletionBatchRequest("b", Seq(UserMessage("question b")))
          ),
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      // the emulated batch processes in the background after submission returns; retrieving
      // the results awaits completion, making the recorded calls safe to assert on
      adapted.retrieveChatCompletionBatchResults(batchInfo.id, "m1").futureValue

      // the emulated batch runs each request through the underlying chat service, so the
      // settings/messages it records are exactly what the batch submission carried
      underlyingChat.seenSettings.map(_.max_tokens) shouldBe Seq(Some(42), Some(42))
      underlyingChat.seenMessages.map(lastUserTexts).sortBy(_.head) shouldBe Seq(
        Seq("question a", "adapted"),
        Seq("question b", "adapted")
      )
    }
  }
}
