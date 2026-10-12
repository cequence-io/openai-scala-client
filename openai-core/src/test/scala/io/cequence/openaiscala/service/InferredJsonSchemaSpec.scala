package io.cequence.openaiscala.service

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  JsonSchema,
  JsonSchemaDescription,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

object InferredJsonSchemaSpec {
  final case class Capital(
    @JsonSchemaDescription("The capital city") city: String,
    populationMillions: Int
  )
  final case class WithOption(
    city: String,
    nickname: Option[String]
  )
  final case class WithMap(
    city: String,
    extras: Map[String, String]
  )
  final case class WithEither(value: Either[Int, String])

  sealed trait Shape
  final case class Circle(radius: Double) extends Shape

  sealed trait Color
  case object Red extends Color
  case object Blue extends Color

  implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  def inferred[T](
    implicit inference: InferredJsonSchema[T]
  ): Option[JsonSchemaDef] = inference.jsonSchemaDef

  // generic code: an abstract T infers nothing unless the caller passes something on
  def unbounded[T]: Option[JsonSchemaDef] = InferredJsonSchema[T].jsonSchemaDef
  def withSchemaOf[T: JsonSchemaOf]: Option[JsonSchemaDef] =
    InferredJsonSchema[T].jsonSchemaDef
  def passedOn[T: InferredJsonSchema]: Option[JsonSchemaDef] =
    InferredJsonSchema[T].jsonSchemaDef

  val capitalSchema: JsonSchema = JsonSchemaReflectionHelper.jsonSchemaFor[Capital]()

  val ownSchema: JsonSchema =
    JsonSchema.Object(properties = Seq("city" -> JsonSchema.String()), required = Seq("city"))
}

/**
 * The schema a JSON helper infers from `T` when the settings carry none
 * ([[InferredJsonSchema]])
 *   - the same on Scala 2 (runtime reflection) and Scala 3 (a macro that, unlike
 *     `JsonSchemaOf.derived`, infers nothing for a type it cannot derive instead of failing to
 *     compile).
 */
class InferredJsonSchemaSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  import InferredJsonSchemaSpec._

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("inferred-json-schema-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  "InferredJsonSchema" should {

    "infer a case class's schema, named after the type and strict" in {
      inferred[Capital] shouldBe Some(
        JsonSchemaDef("Capital", strict = true, Left(capitalSchema))
      )
      // the field's annotation is in it
      capitalSchema match {
        case obj: JsonSchema.Object =>
          obj.properties.toMap.get("city") shouldBe Some(
            JsonSchema.String(description = Some("The capital city"))
          )
        case other => fail(s"not an object: $other")
      }
    }

    "infer a non-strict schema for an optional field or a map" in {
      inferred[WithOption].map(_.strict) shouldBe Some(false)
      inferred[WithMap].map(_.strict) shouldBe Some(false)
    }

    "infer nothing for a JSON value" in {
      inferred[JsObject] shouldBe None
      inferred[JsValue] shouldBe None
    }

    "infer nothing for a type whose schema is not an object with fields" in {
      inferred[Seq[Capital]] shouldBe None
      inferred[String] shouldBe None
      inferred[Map[String, String]] shouldBe None
      inferred[Color] shouldBe None
    }

    "infer nothing for a type the derivation does not support - it still compiles" in {
      inferred[WithEither] shouldBe None
      inferred[Shape] shouldBe None
    }

    "infer nothing for an abstract type, unless the caller passes a schema on" in {
      unbounded[Capital] shouldBe None
      withSchemaOf[Capital].map(_.name) shouldBe Some("Capital")
      passedOn[Capital].map(_.name) shouldBe Some("Capital")
    }

    "use an own JsonSchemaOf in scope, named after it (else 'response')" in {
      {
        implicit val own: JsonSchemaOf[Capital] =
          JsonSchemaOf.instance("capital_v2", ownSchema)
        inferred[Capital] shouldBe Some(
          JsonSchemaDef("capital_v2", strict = true, Left(ownSchema))
        )
      }
      {
        implicit val own: JsonSchemaOf[Capital] = JsonSchemaOf.instance(ownSchema)
        inferred[Capital].map(_.name) shouldBe Some("response")
      }
    }

    "let a local instance keep a derivable type schema-less" in {
      implicit val noSchema: InferredJsonSchema[Capital] = InferredJsonSchema.none
      inferred[Capital] shouldBe None
    }

    "make a name OpenAI takes" in {
      InferredJsonSchema.schemaName("My Type!") shouldBe "My_Type_"
      InferredJsonSchema.schemaName("x" * 70) shouldBe "x" * 64
      InferredJsonSchema.schemaName("") shouldBe "response"
    }

    "tell a strict-compatible schema" in {
      InferredJsonSchema.strictCompatible(capitalSchema) shouldBe true
      InferredJsonSchema.strictCompatible(
        JsonSchema.Object(properties = Seq("a" -> JsonSchema.String()), required = Nil)
      ) shouldBe false
      InferredJsonSchema.strictCompatible(
        JsonSchema.Array(
          JsonSchema.Object(properties = Nil, additionalProperties = Some(true))
        )
      ) shouldBe false
    }
  }

  private def response(content: String) = ChatCompletionResponse(
    id = "id",
    created = new java.util.Date(0L),
    model = "test-model",
    system_fingerprint = None,
    choices = Seq(ChatCompletionChoiceInfo(AssistantMessage(content), 0, Some("stop"), None)),
    usage = None,
    originalResponse = None
  )

  // answers every call with `content`, recording what it was asked
  private class RecordingService(content: String) extends OpenAIChatCompletionService {
    @volatile var askedMessages: Seq[BaseMessage] = Nil
    @volatile var askedSettings: Option[CreateChatCompletionSettings] = None

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      askedMessages = messages
      askedSettings = Some(settings)
      Future.successful(response(content))
    }

    override def close(): Unit = ()
  }

  private val question = Seq(UserMessage("The capital of Norway?"))
  private val oslo = """{"city": "Oslo", "populationMillions": 1}"""

  private def userText(messages: Seq[BaseMessage]) =
    messages.collect { case UserMessage(content, _) => content }.mkString

  "createChatCompletionWithJSON" should {

    "ask with the schema inferred from T when the settings carry none" in {
      val service = new RecordingService(oslo)

      service
        .createChatCompletionWithJSON[Capital](
          question,
          CreateChatCompletionSettings("test-model"),
          jsonSchemaModels = Seq("test-model")
        )
        .futureValue shouldBe Capital("Oslo", 1)

      val settings = service.askedSettings.get
      settings.response_format_type shouldBe Some(ChatCompletionResponseFormatType.json_schema)
      settings.jsonSchema shouldBe Some(
        JsonSchemaDef("Capital", strict = true, Left(capitalSchema))
      )
    }

    "put the inferred schema in the prompt for a model without json_schema" in {
      val service = new RecordingService(oslo)

      service
        .createChatCompletionWithJSON[Capital](
          question,
          CreateChatCompletionSettings("test-model"),
          jsonSchemaModels = Seq("other-model")
        )
        .futureValue shouldBe Capital("Oslo", 1)

      service.askedSettings.get.response_format_type shouldBe Some(
        ChatCompletionResponseFormatType.json_object
      )
      userText(service.askedMessages) should include("<output_json_schema>")
      userText(service.askedMessages) should include("populationMillions")
    }

    "keep the settings' own schema" in {
      val service = new RecordingService(oslo)
      val own = JsonSchemaDef("own", strict = false, Left(ownSchema))

      service
        .createChatCompletionWithJSON[Capital](
          question,
          CreateChatCompletionSettings("test-model", jsonSchema = Some(own)),
          jsonSchemaModels = Seq("test-model")
        )
        .futureValue

      service.askedSettings.get.jsonSchema shouldBe Some(own)
    }

    "ask for a JsObject without a schema, as before" in {
      val service = new RecordingService(oslo)
      val settings = CreateChatCompletionSettings("test-model")

      service
        .createChatCompletionWithJSON[JsObject](question, settings)
        .futureValue shouldBe Json.obj("city" -> "Oslo", "populationMillions" -> 1)

      service.askedSettings shouldBe Some(settings)
      service.askedMessages shouldBe question
    }
  }
}
