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
  ChatCompletionBatchStatus,
  UserMessage
}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class ChatCompletionBatchEmulationAdapterSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("batch-emulation-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  // a fake chat service: echoes "<tag>:<model>:<last user text>", counting the calls it gets
  private class FakeChatService(tag: String) extends OpenAIChatCompletionService {
    val callCount = new AtomicInteger(0)

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      callCount.incrementAndGet()
      val lastUserText =
        messages.reverse.collectFirst { case UserMessage(content, _) => content }.getOrElse("")
      Future.successful(response(settings.model, s"$tag:${settings.model}:$lastUserText"))
    }

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

  private def req(
    customId: String,
    text: String
  ) = ChatCompletionBatchRequest(customId, Seq(UserMessage(text)))

  "ChatCompletionBatchEmulationAdapter" should {

    "emulate a batch via synchronous chat completions and warn once" in {
      val fake = new FakeChatService("fake")
      val warnings = ListBuffer.empty[String]

      val service = OpenAIServiceAdapters.chatCompletionBatchEmulated(fake, warnings.append(_))

      val results = service
        .createChatCompletionBatchAndWaitForResults(
          Seq(req("a", "hi-a"), req("b", "hi-b")),
          CreateChatCompletionSettings("m1"),
          pollingInterval = 50.millis
        )
        .futureValue

      results.map(_.customId).toSet shouldBe Set("a", "b")
      results
        .find(_.customId == "a")
        .flatMap(_.responseOption)
        .map(_.contentHead) shouldBe Some("fake:m1:hi-a")
      results
        .find(_.customId == "b")
        .flatMap(_.responseOption)
        .map(_.contentHead) shouldBe Some("fake:m1:hi-b")

      fake.callCount.get() shouldBe 2
      warnings should have size 1
      warnings.head should include("not natively supported")
    }

    "allow retrieving results twice and keep getChatCompletionBatch working after" in {
      val emulated =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(new FakeChatService("fake"), _ => ())

      val info = emulated
        .createChatCompletionBatch(Seq(req("a", "q")), CreateChatCompletionSettings("m1"))
        .futureValue

      emulated
        .retrieveChatCompletionBatchResults(info.id, "m1")
        .futureValue
        .map(_.customId) shouldBe Seq("a")

      // a second retrieve still works - matches the stateless contract of native providers
      emulated
        .retrieveChatCompletionBatchResults(info.id, "m1")
        .futureValue
        .map(_.customId) shouldBe Seq("a")

      emulated
        .getChatCompletionBatch(info.id, "m1")
        .futureValue
        .status shouldBe ChatCompletionBatchStatus.Completed
    }

    "evict the oldest emulated batch once more than maxRetainedBatches exist" in {
      val emulated = OpenAIServiceAdapters.chatCompletionBatchEmulated(
        new FakeChatService("fake"),
        _ => (),
        maxRetainedBatches = 1
      )

      val infoA = emulated
        .createChatCompletionBatch(Seq(req("a", "q")), CreateChatCompletionSettings("m1"))
        .futureValue
      val infoB = emulated
        .createChatCompletionBatch(Seq(req("b", "q")), CreateChatCompletionSettings("m1"))
        .futureValue

      emulated
        .retrieveChatCompletionBatchResults(infoB.id, "m1")
        .futureValue
        .map(_.customId) shouldBe Seq("b")

      // infoA was evicted (FIFO) once infoB pushed the store past maxRetainedBatches = 1
      emulated
        .retrieveChatCompletionBatchResults(infoA.id, "m1")
        .failed
        .futureValue shouldBe a[OpenAIScalaClientException]
    }

    "bound the number of concurrent underlying calls per batch to maxParallelism" in {
      val inFlight = new AtomicInteger(0)
      val maxObserved = new AtomicInteger(0)

      val throttledFake = new OpenAIChatCompletionService {
        override def createChatCompletion(
          messages: Seq[BaseMessage],
          settings: CreateChatCompletionSettings
        ): Future[ChatCompletionResponse] = {
          val current = inFlight.incrementAndGet()
          maxObserved.updateAndGet(prev => math.max(prev, current))

          akka.pattern.after(50.millis, scheduler) {
            inFlight.decrementAndGet()
            Future.successful(response(settings.model, "ok"))
          }
        }

        override def close(): Unit = ()
      }

      val service = OpenAIServiceAdapters.chatCompletionBatchEmulated(
        throttledFake,
        _ => (),
        maxParallelism = 3
      )

      val requests = (1 to 9).map(i => req(s"r$i", "q"))

      service
        .createChatCompletionBatchAndWaitForResults(
          requests,
          CreateChatCompletionSettings("m1"),
          pollingInterval = 50.millis
        )
        .futureValue

      maxObserved.get() should be <= 3
    }

    "surface a per-request failure as a batch error item, not a whole-batch failure" in {
      val failing = new OpenAIChatCompletionService {
        override def createChatCompletion(
          messages: Seq[BaseMessage],
          settings: CreateChatCompletionSettings
        ): Future[ChatCompletionResponse] =
          Future.failed(new RuntimeException("boom"))
        override def close(): Unit = ()
      }

      val results = OpenAIServiceAdapters
        .chatCompletionBatchEmulated(failing, _ => ())
        .createChatCompletionBatchAndWaitForResults(
          Seq(req("x", "q")),
          CreateChatCompletionSettings("m1"),
          pollingInterval = 50.millis
        )
        .futureValue

      results.head.errorOption.map(_.message) shouldBe Some("boom")
    }

    "work as a fallback member of chatCompletionBatchRouter, routed by model" in {
      val geminiLike =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(new FakeChatService("gem"), _ => ())
      val defaultLike =
        OpenAIServiceAdapters.chatCompletionBatchEmulated(new FakeChatService("def"), _ => ())

      val router = OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouter(
        serviceModels = Map(geminiLike -> Seq("gemini-x")),
        defaultLike
      )

      // matched model -> the registered service
      router
        .createChatCompletionBatchAndWaitForResults(
          Seq(req("x", "q")),
          CreateChatCompletionSettings("gemini-x"),
          pollingInterval = 50.millis
        )
        .futureValue
        .head
        .responseOption
        .map(_.contentHead) shouldBe Some("gem:gemini-x:q")

      // unmatched model -> the default service
      router
        .createChatCompletionBatchAndWaitForResults(
          Seq(req("y", "q")),
          CreateChatCompletionSettings("other-model"),
          pollingInterval = 50.millis
        )
        .futureValue
        .head
        .responseOption
        .map(_.contentHead) shouldBe Some("def:other-model:q")
    }
  }
}
