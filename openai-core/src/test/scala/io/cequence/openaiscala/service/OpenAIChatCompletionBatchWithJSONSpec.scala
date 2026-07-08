package io.cequence.openaiscala.service

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  JsonSchema,
  UserMessage
}
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object OpenAIChatCompletionBatchWithJSONSpec {
  final case class Answer(city: String)
  implicit val answerFormat: Format[Answer] = Json.format[Answer]
}

/**
 * Coverage for `OpenAIChatCompletionExtra.createChatCompletionBatchWithJSON` - the typed
 * (JSON-parsing) sibling of `createChatCompletionBatchAndWaitForResults`.
 */
class OpenAIChatCompletionBatchWithJSONSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  import OpenAIChatCompletionBatchWithJSONSpec._

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("batch-with-json-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
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
    userText: String = "hi"
  ) = ChatCompletionBatchRequest(customId, Seq(UserMessage(userText)))

  private val citySchemaDef = JsonSchemaDef(
    name = "answer_response",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq("city" -> JsonSchema.String()),
        required = Seq("city")
      )
    )
  )

  /**
   * A batch-capable fake that answers every request with `contentFor(customId, messages)`,
   * optionally shuffling/dropping/failing items, and recording what was submitted.
   */
  private class FakeBatchService(
    contentFor: (String, Seq[BaseMessage]) => String,
    transformResults: Seq[ChatCompletionBatchResultItem] => Seq[
      ChatCompletionBatchResultItem
    ] = identity,
    failSubmit: Boolean = false
  ) extends OpenAIChatCompletionService
      with OpenAIChatCompletionBatchService {

    val submitCount = new AtomicInteger(0)
    val submittedRequests = new ListBuffer[ChatCompletionBatchRequest]
    @volatile var submittedSettings: Option[CreateChatCompletionSettings] = None

    private val batchResults =
      new java.util.concurrent.ConcurrentHashMap[String, Seq[ChatCompletionBatchResultItem]]()

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      Future.successful(response(settings.model, contentFor("sync", messages)))

    override def createChatCompletionBatch(
      requests: Seq[ChatCompletionBatchRequest],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionBatchInfo] =
      if (failSubmit)
        Future.failed(new IllegalStateException("submit refused"))
      else {
        submitCount.incrementAndGet()
        submittedRequests ++= requests
        submittedSettings = Some(settings)

        val items = requests.map { request =>
          ChatCompletionBatchResultItem(
            request.customId,
            Right(response(settings.model, contentFor(request.customId, request.messages)))
          )
        }
        val batchId = s"fake-batch-${submitCount.get}"
        batchResults.put(batchId, transformResults(items))

        Future.successful(
          ChatCompletionBatchInfo(batchId, ChatCompletionBatchStatus.Completed, "completed")
        )
      }

    override def getChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      Future.successful(
        ChatCompletionBatchInfo(batchId, ChatCompletionBatchStatus.Completed, "completed")
      )

    override def retrieveChatCompletionBatchResults(
      batchId: String,
      model: String
    ): Future[Seq[ChatCompletionBatchResultItem]] =
      Future.successful(Option(batchResults.get(batchId)).getOrElse(Nil))

    override def cancelChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      Future.successful(
        ChatCompletionBatchInfo(batchId, ChatCompletionBatchStatus.Cancelled, "cancelled")
      )

    override def deleteChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[Unit] = Future.successful(())

    override def close(): Unit = ()
  }

  private def cityJson(customId: String) = s"""{"city": "city-of-$customId"}"""

  "createChatCompletionBatchWithJSON" should {

    "return typed results in request order even when the batch returns them shuffled" in {
      val service = new FakeBatchService(
        contentFor = (
          customId,
          _
        ) => cityJson(customId),
        transformResults = _.reverse
      )

      val results = service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("b"), req("c")),
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      results.map(_.customId) shouldBe Seq("a", "b", "c")
      results.flatMap(_.valueOption) shouldBe Seq(
        Answer("city-of-a"),
        Answer("city-of-b"),
        Answer("city-of-c")
      )
    }

    "pass a provider-side item error through and keep the other items typed" in {
      val service = new FakeBatchService(
        contentFor = (
          customId,
          _
        ) => cityJson(customId),
        transformResults = _.map { item =>
          if (item.customId == "b")
            item
              .copy(result = Left(ChatCompletionBatchError("provider exploded", Some("boom"))))
          else item
        }
      )

      val results = service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("b")),
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      results.head.valueOption shouldBe Some(Answer("city-of-a"))
      results(1).errorOption.flatMap(_.code) shouldBe Some("boom")
    }

    "map an unparseable response to a response_parse_error item without failing the batch" in {
      val service = new FakeBatchService(
        contentFor = (
          customId,
          _
        ) => if (customId == "b") """{"unexpected": 42}""" else cityJson(customId)
      )

      val results = service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("b"), req("c")),
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      results.map(_.customId) shouldBe Seq("a", "b", "c")
      results.head.valueOption shouldBe Some(Answer("city-of-a"))
      results(1).errorOption.flatMap(_.code) shouldBe Some("response_parse_error")
      results(2).valueOption shouldBe Some(Answer("city-of-c"))
    }

    "map a request with no returned result to a missing_result item" in {
      val service = new FakeBatchService(
        contentFor = (
          customId,
          _
        ) => cityJson(customId),
        transformResults = _.filterNot(_.customId == "b")
      )

      val results = service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("b")),
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      results.head.valueOption shouldBe Some(Answer("city-of-a"))
      results(1).errorOption.flatMap(_.code) shouldBe Some("missing_result")
    }

    "use the native json_schema mode for models listed in jsonSchemaModels" in {
      val service = new FakeBatchService(contentFor =
        (
          customId,
          _
        ) => cityJson(customId)
      )

      service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("b")),
          CreateChatCompletionSettings("m1", jsonSchema = Some(citySchemaDef)),
          jsonSchemaModels = Seq("m1")
        )
        .futureValue

      val settings = service.submittedSettings.get
      settings.response_format_type shouldBe Some(ChatCompletionResponseFormatType.json_schema)
      settings.jsonSchema shouldBe Some(citySchemaDef)

      // messages untouched in native mode
      service.submittedRequests.foreach { request =>
        request.messages.collect { case UserMessage(content, _) => content }.head shouldBe "hi"
      }
    }

    "fall back to json_object mode with a per-request schema appendix for unlisted models" in {
      val service = new FakeBatchService(contentFor =
        (
          customId,
          _
        ) => cityJson(customId)
      )

      service
        .createChatCompletionBatchWithJSON[Answer](
          Seq(req("a", "question a"), req("b", "question b")),
          CreateChatCompletionSettings("m1", jsonSchema = Some(citySchemaDef)),
          jsonSchemaModels = Seq("some-other-model")
        )
        .futureValue

      val settings = service.submittedSettings.get
      settings.response_format_type shouldBe Some(ChatCompletionResponseFormatType.json_object)
      settings.jsonSchema shouldBe None

      // every request's user message got the schema appendix
      val userContents = service.submittedRequests.map(
        _.messages.collect { case UserMessage(content, _) => content }.head
      )
      userContents.size shouldBe 2
      userContents.zip(Seq("question a", "question b")).foreach { case (content, original) =>
        content should startWith(original)
        content should include("<output_json_schema>")
        content should include("\"city\"")
      }
    }

    "short-circuit empty requests without submitting a batch" in {
      val service = new FakeBatchService(contentFor =
        (
          customId,
          _
        ) => cityJson(customId)
      )

      val results = service
        .createChatCompletionBatchWithJSON[Answer](
          Nil,
          CreateChatCompletionSettings("m1")
        )
        .futureValue

      results shouldBe Nil
      service.submitCount.get shouldBe 0
    }

    "reject duplicate custom ids" in {
      val service = new FakeBatchService(contentFor =
        (
          customId,
          _
        ) => cityJson(customId)
      )

      an[IllegalArgumentException] should be thrownBy
        service.createChatCompletionBatchWithJSON[Answer](
          Seq(req("a"), req("a")),
          CreateChatCompletionSettings("m1")
        )
    }
  }
}
