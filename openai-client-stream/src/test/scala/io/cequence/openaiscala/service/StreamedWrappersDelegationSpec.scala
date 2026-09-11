package io.cequence.openaiscala.service

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool, UserMessage}
import io.cequence.openaiscala.service.adapter.MappedModel
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

/**
 * The streamed wrappers must delegate `createChatToolCompletionStreamed` to the wrapped
 * service (and not fall back to the trait default, which would lose provider-native thinking /
 * tool support).
 */
class StreamedWrappersDelegationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("streamed-wrappers-delegation")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  /** A stub whose typed stream reports its name plus the model / tools / messages it saw. */
  private class Stub(name: String) extends OpenAIChatCompletionStreamedServiceExtra {
    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      Source.failed(new IllegalStateException("legacy path must not be used"))

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): Source[ChatChunk, NotUsed] =
      Source(
        List[ChatChunk](
          Start(name, settings.model),
          Text(
            s"tools=${tools.size} messages=${messages.size} choice=${responseToolChoice.getOrElse("-")}"
          )
        )
      )

    override def close(): Unit = ()
  }

  private val messages = Seq(UserMessage("hi"))

  private def collect(source: Source[ChatChunk, NotUsed]): Seq[ChatChunk] =
    source.runWith(Sink.seq).futureValue

  "the streamed router" should {
    "dispatch by model and apply the mapped model name" in {
      val a = new Stub("a")
      val default = new Stub("default")

      val router = OpenAIChatCompletionStreamedServiceRouter.applyMapped(
        Map(a -> Seq(MappedModel("alias-a", "real-a"))),
        default
      )

      collect(
        router.createChatToolCompletionStreamed(
          messages,
          Nil,
          Some("f"),
          CreateChatCompletionSettings("alias-a")
        )
      ) shouldBe Seq(Start("a", "real-a"), Text("tools=0 messages=1 choice=f"))

      collect(
        router.createChatToolCompletionStreamed(
          messages,
          Nil,
          None,
          CreateChatCompletionSettings("other")
        )
      ).head shouldBe Start("default", "other")
    }
  }

  "the round-robin service" should {
    "alternate between the underlying services" in {
      val rr = OpenAIChatCompletionStreamedRoundRobinService(Seq(new Stub("a"), new Stub("b")))
      val names = (1 to 4).map { _ =>
        collect(
          rr.createChatToolCompletionStreamed(
            messages,
            Nil,
            None,
            CreateChatCompletionSettings("m")
          )
        ).head
      }
      names shouldBe Seq(Start("a", "m"), Start("b", "m"), Start("a", "m"), Start("b", "m"))
    }
  }

  "the chat-shaped Responses API view" should {
    "resolve on a streamed OpenAI service and carry Responses-native tools in the settings" in {
      import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
      import io.cequence.openaiscala.domain.responsesapi.tools.WebSearchTool
      import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._

      val service = OpenAIServiceFactory.withStreaming(apiKey = "dummy")
      try {
        val view = service.responsesAsChatCompletion
        view should not be null

        // never materialized - only the wiring / implicit resolution is exercised here
        val source = service.createChatToolCompletionStreamedViaResponses(
          messages,
          Nil,
          None,
          CreateChatCompletionSettings("gpt-5.4").setResponsesTools(Seq(WebSearchTool()))
        )
        source should not be null

        CreateChatCompletionSettings("m")
          .setResponsesTools(Seq(WebSearchTool()))
          .responsesTools shouldBe
          Seq(WebSearchTool())
        CreateChatCompletionSettings("m").responsesTools shouldBe Nil
        CreateChatCompletionSettings("m")
          .setResponsesReasoningSummary(false)
          .responsesReasoningSummary shouldBe
          Some(false)
      } finally service.close()
    }
  }

  "the conversion adapters" should {
    "convert inputs and transform the typed output" in {
      val converted = OpenAIChatCompletionStreamedConversionAdapter(
        new Stub("conv"),
        messagesConversion = ms => ms ++ ms,
        settingsConversion = s => s.copy(model = s.model + "-converted")
      )

      val transformed = OpenAIChatCompletionStreamedOutputConversionAdapter(
        converted,
        Flow[Seq[io.cequence.openaiscala.domain.response.ChunkMessageSpec]],
        Flow[ChatChunk].map {
          case Text(t) => Text(t.toUpperCase)
          case other   => other
        }
      )

      collect(
        transformed.createChatToolCompletionStreamed(
          messages,
          Nil,
          None,
          CreateChatCompletionSettings("m")
        )
      ) shouldBe Seq(Start("conv", "m-converted"), Text("TOOLS=0 MESSAGES=2 CHOICE=-"))
    }
  }
}
