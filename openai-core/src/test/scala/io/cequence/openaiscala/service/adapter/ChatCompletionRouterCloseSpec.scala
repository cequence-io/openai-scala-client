package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

/**
 * Regression coverage for `OpenAIChatCompletionServiceRouter`'s `close()`: it must close each
 * registered service exactly once and must NOT close the default - the default is closed by
 * the wrapping adapter when the router is built via `OpenAIServiceAdapters`'s
 * `chatCompletionRouter` / `chatCompletionBatchRouter`, and is otherwise the caller's own
 * responsibility.
 */
class ChatCompletionRouterCloseSpec extends AnyWordSpecLike with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  // a fake chat service that just counts how many times it was closed
  private class FakeChatService(tag: String) extends OpenAIChatCompletionService {
    val closeCount = new AtomicInteger(0)

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      Future.successful(response(settings.model, s"$tag:${settings.model}"))

    override def close(): Unit = {
      closeCount.incrementAndGet()
      ()
    }
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

  "OpenAIChatCompletionServiceRouter.close" should {

    "close the registered and the default service exactly once via chatCompletionRouter" in {
      val registered = new FakeChatService("registered")
      val default = new FakeChatService("default")

      val router = OpenAIServiceAdapters.forChatCompletionService
        .chatCompletionRouter(Map(registered -> Seq("m1")), default)

      router.close()

      registered.closeCount.get() shouldBe 1
      default.closeCount.get() shouldBe 1
    }

    "close the registered and the default exactly once via chatCompletionBatchRouter" in {
      val registeredFake = new FakeChatService("registered")
      val defaultFake = new FakeChatService("default")

      val registered =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(registeredFake, _ => ())
      val default =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(defaultFake, _ => ())

      val router = OpenAIServiceAdapters.forChatCompletionService
        .chatCompletionBatchRouter(Map(registered -> Seq("m1")), default)

      router.close()

      registeredFake.closeCount.get() shouldBe 1
      defaultFake.closeCount.get() shouldBe 1
    }

    "NOT close the default when the router is constructed directly (standalone)" in {
      val registered = new FakeChatService("registered")
      val default = new FakeChatService("default")

      val router = OpenAIChatCompletionServiceRouter(Map(registered -> Seq("m1")), default)

      router.close()

      registered.closeCount.get() shouldBe 1
      default.closeCount.get() shouldBe 0
    }
  }
}
