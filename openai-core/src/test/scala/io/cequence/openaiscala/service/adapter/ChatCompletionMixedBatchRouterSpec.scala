package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.OpenAIScalaClientException
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
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ExecutionContext, Future}

/**
 * Coverage for the mixed-capability batch router - `OpenAIChatCompletionServiceRouter`'s
 * `applyWithBatchMixed`/`applyWithBatchMixedMapped` and
 * `OpenAIServiceAdapters.chatCompletionBatchRouterMixed`/`chatCompletionBatchRouterMixedMapped`
 * \- built from a single `Map[OpenAIChatCompletionService, ...]` that combines batch-capable
 * and plain (non-batch) chat-completion services.
 */
class ChatCompletionMixedBatchRouterSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  // a fake chat service that just echoes "<tag>:<model>"
  private class FakeChatService(tag: String) extends OpenAIChatCompletionService {
    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      Future.successful(response(settings.model, s"$tag:${settings.model}"))

    override def close(): Unit = ()
  }

  private def response(
    model: String,
    content: String
  ) = ChatCompletionResponse(
    id = "id",
    created = new java.util.Date(0L),
    model = model,
    system_fingerprint = None,
    choices = Seq(ChatCompletionChoiceInfo(AssistantMessage(content), 0, Some("stop"), None)),
    usage = None,
    originalResponse = None
  )

  private def req(customId: String) =
    ChatCompletionBatchRequest(customId, Seq(UserMessage("hi")))

  // m1 -> batch-capable, m2 -> plain chat-only, m3 -> unmatched (falls through to the default)
  private class Fixture {
    val batchCapableFake = new FakeChatService("batch")
    val batchCapable: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
      OpenAIServiceAdapters.chatCompletionBatchEmulated(batchCapableFake, _ => ())

    val plainChat: OpenAIChatCompletionService = new FakeChatService("plain")

    val defaultFake = new FakeChatService("default")
    val default: OpenAIChatCompletionServiceRouter.BatchChatService =
      OpenAIServiceAdapters.chatCompletionBatchEmulated(defaultFake, _ => ())

    val router: OpenAIChatCompletionServiceRouter.BatchChatService =
      OpenAIChatCompletionServiceRouter.applyWithBatchMixedMapped(
        Map(
          batchCapable -> Seq(MappedModel("m1", "m1")),
          plainChat -> Seq(MappedModel("m2", "m2"))
        ),
        default
      )
  }

  "OpenAIChatCompletionServiceRouter.applyWithBatchMixedMapped" should {

    "route createChatCompletionBatch to a registered batch-capable service" in {
      val f = new Fixture

      val info = f.router
        .createChatCompletionBatch(Seq(req("a")), CreateChatCompletionSettings("m1"))
        .futureValue

      f.router
        .retrieveChatCompletionBatchResults(info.id, "m1")
        .futureValue
        .flatMap(_.responseOption)
        .map(_.contentHead) shouldBe Seq("batch:m1")
    }

    "fail fast with a helpful message when the model is mapped to a non-batch service" in {
      val f = new Fixture

      val error = f.router
        .createChatCompletionBatch(Seq(req("a")), CreateChatCompletionSettings("m2"))
        .failed
        .futureValue

      error shouldBe a[OpenAIScalaClientException]
      error.getMessage should include("not supported")
    }

    "route an unmatched model to the (batch-capable) default" in {
      val f = new Fixture

      val info = f.router
        .createChatCompletionBatch(Seq(req("a")), CreateChatCompletionSettings("m3"))
        .futureValue

      f.router
        .retrieveChatCompletionBatchResults(info.id, "m3")
        .futureValue
        .flatMap(_.responseOption)
        .map(_.contentHead) shouldBe Seq("default:m3")
    }

    "still route createChatCompletion to a model mapped to a non-batch service" in {
      val f = new Fixture

      f.router
        .createChatCompletion(Seq(UserMessage("hi")), CreateChatCompletionSettings("m2"))
        .futureValue
        .contentHead shouldBe "plain:m2"
    }
  }

  "OpenAIServiceAdapters.chatCompletionBatchRouterMixedMapped" should {

    "wire the same routing semantics through the adapter entry point" in {
      val f = new Fixture

      val wired =
        OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouterMixedMapped(
          serviceModels = Map(
            f.batchCapable -> Seq(MappedModel("m1", "m1")),
            f.plainChat -> Seq(MappedModel("m2", "m2"))
          ),
          service = f.default
        )

      wired
        .createChatCompletionBatch(Seq(req("a")), CreateChatCompletionSettings("m1"))
        .futureValue
        .status shouldBe io.cequence.openaiscala.domain.ChatCompletionBatchStatus.InProgress

      wired
        .createChatCompletionBatch(Seq(req("a")), CreateChatCompletionSettings("m2"))
        .failed
        .futureValue
        .getMessage should include("not supported")
    }
  }
}
