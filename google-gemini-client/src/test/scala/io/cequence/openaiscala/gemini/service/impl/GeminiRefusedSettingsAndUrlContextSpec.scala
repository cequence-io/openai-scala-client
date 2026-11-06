package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.ChatRole.Model
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  GenerateContentResponse,
  UrlContextMetadata,
  UrlMetadata,
  UsageMetadata,
  FinishReason => GeminiFinishReason
}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.{
  GenerateContentSettings,
  GenerationConfig
}
import io.cequence.openaiscala.gemini.domain.{Content, Part, Tool}
import io.cequence.openaiscala.gemini.service.GeminiService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * The Gemini adapter drops the settings Gemini refuses (live 2026-11-04: a non-zero penalty is
 * a 400 on every model, more than one candidate on every model but 2.x) and supports the URL
 * context tool - sent as `{"urlContext": {}}`, its `urlContextMetadata` read and passed on the
 * typed stream.
 */
class GeminiRefusedSettingsAndUrlContextSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-refused-settings")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit = {
    system.terminate()
    ()
  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val urlMetadata = UrlContextMetadata(
    Seq(
      UrlMetadata("https://example.com", "URL_RETRIEVAL_STATUS_SUCCESS"),
      UrlMetadata("https://nowhere.invalid/page", "URL_RETRIEVAL_STATUS_ERROR")
    )
  )

  private val answer = GenerateContentResponse(
    candidates = Seq(
      Candidate(
        content = Content(Seq(Part.Text("ok")), Some(Model)),
        finishReason = Some(GeminiFinishReason.STOP),
        index = Some(0),
        urlContextMetadata = Some(urlMetadata)
      )
    ),
    usageMetadata = UsageMetadata(promptTokenCount = 10, totalTokenCount = 12),
    modelVersion = "gemini-3.8-flash"
  )

  // a Gemini service answering with a text and recording the settings it was asked with
  private class Recording {
    val requests = mutable.ArrayBuffer.empty[GenerateContentSettings]
    val underlying: GeminiService = mock(classOf[GeminiService])

    when(underlying.generateContent(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized(requests += invocation.getArgument[GenerateContentSettings](1))
        Future.successful(answer)
    }

    when(underlying.generateContentStreamed(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized(requests += invocation.getArgument[GenerateContentSettings](1))
        Source.single(answer)
    }

    val adapter = new OpenAIGeminiChatCompletionService(underlying)

    def lastConfig: GenerationConfig =
      requests.synchronized(requests.last).generationConfig.get
  }

  private def settings(
    model: String,
    n: Option[Int]
  ) =
    CreateChatCompletionSettings(
      model,
      presence_penalty = Some(0.5),
      frequency_penalty = Some(0d),
      n = n,
      temperature = Some(0.3)
    )

  private val messages = Seq(UserMessage("hi"))

  "The Gemini adapter" should {

    "drop the penalties and several candidates on a newer model - sync and typed stream" in {
      val recording = new Recording
      import recording._

      adapter.createChatCompletion(messages, settings("gemini-3.8-flash", Some(2))).futureValue
      lastConfig.presencePenalty shouldBe None
      lastConfig.frequencyPenalty shouldBe None
      lastConfig.candidateCount shouldBe None
      lastConfig.temperature shouldBe Some(0.3)

      adapter
        .createChatToolCompletionStreamed(
          messages,
          Nil,
          None,
          settings(NonOpenAIModelId.gemini_4_argon, Some(3))
        )
        .runWith(Sink.ignore)
        .futureValue
      lastConfig.presencePenalty shouldBe None
      lastConfig.candidateCount shouldBe None
    }

    "keep several candidates on Gemini 2.x, and apply the rules to Gemma too" in {
      val recording = new Recording
      import recording._

      adapter
        .createChatCompletion(messages, settings(NonOpenAIModelId.gemini_2_5_flash, Some(2)))
        .futureValue
      lastConfig.candidateCount shouldBe Some(2)
      lastConfig.presencePenalty shouldBe None

      // on Google's API Gemma 4 refuses both as well (live)
      adapter.createChatCompletion(messages, settings("gemma-4-31b-it", Some(2))).futureValue
      lastConfig.candidateCount shouldBe None
      lastConfig.presencePenalty shouldBe None
    }

    "send the URL context tool and pass its metadata on the typed stream" in {
      val recording = new Recording
      import recording._

      val chunks = adapter
        .createChatToolCompletionStreamed(
          messages,
          Nil,
          None,
          CreateChatCompletionSettings("gemini-3.8-flash").setGeminiTools(Seq(Tool.UrlContext))
        )
        .runWith(Sink.seq)
        .futureValue

      requests.synchronized(requests.last).tools shouldBe Some(Seq(Tool.UrlContext))
      chunks.collect {
        case other: ChatChunk.Other if other.kind == "urlContextMetadata" =>
          other.raw
      } shouldBe Seq(Json.toJson(urlMetadata))

      // the sync reply keeps the whole response
      val reply = adapter
        .createChatCompletion(
          messages,
          CreateChatCompletionSettings("gemini-3.8-flash").setGeminiTools(Seq(Tool.UrlContext))
        )
        .futureValue
      reply.originalResponse.collect { case response: GenerateContentResponse => response }
        .flatMap(_.candidates.head.urlContextMetadata) shouldBe Some(urlMetadata)
    }
  }

  "The URL context JSON" should {

    "write the tool as Gemini expects and read it back" in {
      val tool: Tool = Tool.UrlContext
      Json.toJson(tool) shouldBe Json.obj("urlContext" -> Json.obj())
      Json.obj("urlContext" -> Json.obj()).as[Tool] shouldBe Tool.UrlContext
    }

    "read a candidate's urlContextMetadata as Gemini sends it, a new status included" in {
      // the shape of a live gemini-2.5-flash answer (2026-11-04), plus an unknown status
      val candidate = Json
        .parse(
          """{
            |  "content": {"parts": [{"text": "ok"}], "role": "model"},
            |  "finishReason": "STOP",
            |  "index": 0,
            |  "groundingMetadata": {
            |    "groundingChunks": [{"web": {"uri": "https://example.com", "title": "Example Domain"}}]
            |  },
            |  "urlContextMetadata": {
            |    "urlMetadata": [
            |      {"retrievedUrl": "https://example.com", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_SUCCESS"},
            |      {"retrievedUrl": "https://nowhere.invalid/page", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_ERROR"},
            |      {"retrievedUrl": "https://example.org", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_SOMETHING_NEW"}
            |    ]
            |  }
            |}""".stripMargin
        )
        .as[Candidate]

      val urls = candidate.urlContextMetadata.get.urlMetadata
      urls.map(_.retrievedUrl) shouldBe Seq(
        "https://example.com",
        "https://nowhere.invalid/page",
        "https://example.org"
      )
      urls.map(_.succeeded) shouldBe Seq(true, false, false)
      Json.toJson(candidate).as[Candidate] shouldBe candidate
    }
  }
}
