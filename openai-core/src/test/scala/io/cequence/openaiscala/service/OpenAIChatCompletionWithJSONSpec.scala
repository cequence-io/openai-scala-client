package io.cequence.openaiscala.service

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.OpenAIScalaJsonParseException
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

import scala.concurrent.{ExecutionContext, Future}

object OpenAIChatCompletionWithJSONSpec {
  final case class Answer(city: String)
  implicit val answerFormat: Format[Answer] = Json.format[Answer]
}

/**
 * Coverage for the sync typed path
 * `OpenAIChatCompletionExtra.createChatCompletionWithJSON(FullResponse)` - in particular that
 * both JSON failure flavors (unparseable content and JSON-to-T conversion) fail with a
 * response-carrying [[OpenAIScalaJsonParseException]], so the billed token usage of the failed
 * attempt stays accessible to the caller.
 */
class OpenAIChatCompletionWithJSONSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  import OpenAIChatCompletionWithJSONSpec._

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("chat-completion-with-json-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val usage =
    UsageInfo(prompt_tokens = 1234, total_tokens = 1301, completion_tokens = Some(67))

  private def response(content: String) = ChatCompletionResponse(
    id = "id",
    created = new java.util.Date(0L),
    model = "test-model",
    system_fingerprint = None,
    choices = Seq(ChatCompletionChoiceInfo(AssistantMessage(content), 0, Some("stop"), None)),
    usage = Some(usage),
    originalResponse = None
  )

  private class FakeChatService(content: String) extends OpenAIChatCompletionService {
    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      Future.successful(response(content))

    override def close(): Unit = ()
  }

  private val settings = CreateChatCompletionSettings(model = "test-model")

  "createChatCompletionWithJSONFullResponse" should {

    "return the parsed value and the raw response on success" in {
      val service = new FakeChatService("""{"city": "Bratislava"}""")

      val (answer, rawResponse) = service
        .createChatCompletionWithJSONFullResponse[Answer](Seq(UserMessage("hi")), settings)
        .futureValue

      answer shouldBe Answer("Bratislava")
      rawResponse.usage shouldBe Some(usage)
    }

    "fail with a response-carrying OpenAIScalaJsonParseException when the content is not parseable as JSON" in {
      val service = new FakeChatService("no json here at all")

      val exception = service
        .createChatCompletionWithJSONFullResponse[Answer](
          Seq(UserMessage("hi")),
          settings,
          // deterministic parse failure - the default parser's repair could otherwise coerce
          // arbitrary content into something parseable
          parseJson = content => throw new IllegalArgumentException(s"unparseable: $content")
        )
        .failed
        .futureValue

      exception shouldBe a[OpenAIScalaJsonParseException]
      exception.asInstanceOf[OpenAIScalaJsonParseException].response.usage shouldBe Some(usage)
    }

    "fail with a response-carrying OpenAIScalaJsonParseException when the JSON does not convert to the expected type" in {
      val service = new FakeChatService("""{"town": "Bratislava"}""")

      val exception = service
        .createChatCompletionWithJSONFullResponse[Answer](Seq(UserMessage("hi")), settings)
        .failed
        .futureValue

      exception shouldBe a[OpenAIScalaJsonParseException]
      exception.asInstanceOf[OpenAIScalaJsonParseException].response.usage shouldBe Some(usage)
    }
  }
}
