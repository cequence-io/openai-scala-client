package io.cequence.openaiscala.typesafe

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.impl.EndPoint
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.io.Source

/**
 * Checks this client against TypeSafe's published OpenAPI document
 * (`https://api.typesafe.ai/openapi.json`, vendored as `typesafe-openapi.json` - version
 * 0.2.0, fetched 2026-09-16): the endpoints and auth scheme it calls exist as it calls them,
 * every example the spec gives decodes into the domain, what the client writes carries every
 * required property and nothing the spec does not know, and the discriminators agree.
 */
class OpenApiConformanceSpec extends AnyWordSpec with Matchers {

  private val spec: JsObject = {
    val source = Source.fromResource("typesafe-openapi.json")
    try Json.parse(source.mkString).as[JsObject]
    finally source.close()
  }

  private val schemas = (spec \ "components" \ "schemas").as[JsObject]

  private def schema(name: String): JsObject = (schemas \ name).as[JsObject]

  private def properties(name: String): JsObject = (schema(name) \ "properties").as[JsObject]

  private def required(name: String): Set[String] =
    (schema(name) \ "required").asOpt[Set[String]].getOrElse(Set.empty)

  /**
   * An instance of a schema built from the first example (or the `const`) of every property.
   */
  private def exampleOf(name: String): JsObject =
    JsObject(properties(name).fields.flatMap { case (property, definition) =>
      (definition \ "const").toOption
        .orElse((definition \ "examples").asOpt[Seq[JsValue]].flatMap(_.headOption))
        .map(property -> _)
    })

  "The vendored OpenAPI document" should {

    "be the version this client was written against" in {
      (spec \ "info" \ "version").as[String] shouldBe "0.2.0"
    }
  }

  "The endpoints" should {

    "exist in the spec with the methods the client uses" in {
      val paths = (spec \ "paths").as[JsObject]

      (paths \ ("/" + EndPoint.systemOne.toString) \ "post").toOption shouldBe defined
      (paths \ ("/" + EndPoint.models.toString) \ "get").toOption shouldBe defined
    }

    "use HTTP bearer auth, which the client sends as `Authorization: Bearer <key>`" in {
      val scheme = (spec \ "components" \ "securitySchemes" \ "HTTPBearer").as[JsObject]

      (scheme \ "type").as[String] shouldBe "http"
      (scheme \ "scheme").as[String] shouldBe "bearer"

      val paths = (spec \ "paths").as[JsObject]
      Seq("/v1/systemone" -> "post", "/v1/models" -> "get").foreach { case (path, method) =>
        (paths \ path \ method \ "security").as[Seq[JsObject]].flatMap(_.keys) should contain(
          "HTTPBearer"
        )
      }
    }

    "take and return the request / response schemas the client encodes" in {
      val post = (spec \ "paths" \ "/v1/systemone" \ "post").as[JsObject]

      (post \ "requestBody" \ "content" \ "application/json" \ "schema" \ "$ref")
        .as[String] shouldBe "#/components/schemas/SystemOneRequest"
      (post \ "responses" \ "200" \ "content" \ "application/json" \ "schema" \ "$ref")
        .as[String] shouldBe "#/components/schemas/SystemOneResponse"

      val get = (spec \ "paths" \ "/v1/models" \ "get").as[JsObject]
      (get \ "responses" \ "200" \ "content" \ "application/json" \ "schema" \ "$ref")
        .as[String] shouldBe "#/components/schemas/ModelMetadataList"
    }
  }

  "The spec's own examples" should {

    "decode as answers" in {
      exampleOf("NoulAnswer").as[Answer] shouldBe NoulAnswer(0.98)

      exampleOf("ChoiceAnswer").as[Answer] shouldBe
        ChoiceAnswer("angry", 0.9, Map("angry" -> 0.8, "calm" -> 0.1, "excited" -> 0.1))

      exampleOf("ScoreAnswer").as[Answer] shouldBe ScoreAnswer(
        1.7,
        0.9,
        Map(
          0 -> JsString("Can wait"),
          1 -> JsString("Needs attention this week"),
          2 -> JsString("Needs attention today")
        ),
        Map(0 -> 0.1, 1 -> 0.1, 2 -> 0.8)
      )
    }

    "decode as questions" in {
      exampleOf("NoulQuestion").as[Question] shouldBe NoulQuestion(
        Some(JsString("Is this message spam?")),
        Some(NoulCriteria("Unsolicited advertising", "A legitimate conversation"))
      )

      exampleOf("ChoiceQuestion").as[Question] shouldBe ChoiceQuestion(
        "What is the tone of this message?",
        "angry" -> "An upset or hostile message",
        "calm" -> "A neutral or polite message",
        "excited" -> "An enthusiastic or eager message"
      )

      exampleOf("ScoreQuestion").as[Question] shouldBe ScoreQuestion(
        "How urgent is this message?",
        "Can wait",
        "Needs attention this week",
        "Needs attention today"
      )
    }

    "decode as the request, the response, the usage and the model listing" in {
      val request = exampleOf("SystemOneRequest").as[SystemOneRequest]
      request.model shouldBe "jev-latest"
      request.state shouldBe JsString("I was charged twice. Please help.")
      request.questions shouldBe Map(
        "billing" -> NoulQuestion(Some(JsString("Is this message about billing?")), None)
      )

      val response = exampleOf("SystemOneResponse").as[SystemOneResponse]
      response.model shouldBe "jev-latest"
      response.answers shouldBe Map("billing" -> NoulAnswer(0.98))
      response.usage shouldBe Usage(Some(120), Some(12))

      exampleOf("Usage").as[Usage] shouldBe Usage(Some(120), Some(12))

      exampleOf("ModelMetadata").as[ModelMetadata] shouldBe
        ModelMetadata("jev-latest", "General-purpose system one model.", "2026-09-15")

      (exampleOf("ModelMetadataList") \ "models").as[Seq[ModelMetadata]].map(_.name) shouldBe
        Seq("jev-latest")
    }

    "include a structured (object) instruction, which the domain carries as JSON" in {
      val instructionExamples =
        (properties("NoulQuestion") \ "instructions" \ "examples").as[Seq[JsValue]]

      instructionExamples.collect { case o: JsObject => o } should not be empty

      instructionExamples.foreach { instructions =>
        val question = NoulQuestion(instructions = Some(instructions))
        (Json.toJson[Question](question) \ "instructions").get shouldBe instructions
      }
    }
  }

  "What the client writes" should {

    val fullQuestions: Map[String, (Question, String)] = Map(
      "noul" -> (NoulQuestion("q", "yes", "no"), "NoulQuestion"),
      "choice" -> (ChoiceQuestion("q", "a" -> "A"), "ChoiceQuestion"),
      "score" -> (ScoreQuestion("q", "low", "high"), "ScoreQuestion")
    )

    "carry every required property of each question schema, and no unknown one" in {
      fullQuestions.values.foreach { case (question, schemaName) =>
        val written = Json.toJson(question).as[JsObject]

        withClue(s"$schemaName: ") {
          written.keys should contain allElementsOf required(schemaName)
          written.keys should contain allElementsOf properties(schemaName).keys
          written.keys.diff(properties(schemaName).keys) shouldBe empty
        }
      }
    }

    "carry every required property of the request schema, and no unknown one" in {
      val written = Json
        .toJson(
          SystemOneRequest(
            JsString("state"),
            "jev-latest",
            fullQuestions.map { case (k, v) =>
              k -> v._1
            }
          )
        )
        .as[JsObject]

      written.keys should contain allElementsOf required("SystemOneRequest")
      written.keys.diff(properties("SystemOneRequest").keys) shouldBe empty
    }

    "match the `type` discriminator mapping of the Question union" in {
      val mapping =
        (schema("Question") \ "discriminator" \ "mapping").as[Map[String, String]]

      mapping.keySet shouldBe Set("noul", "choice", "score")

      fullQuestions.foreach { case (tag, (question, schemaName)) =>
        (Json.toJson(question) \ "type").as[String] shouldBe tag
        mapping(tag) shouldBe s"#/components/schemas/$schemaName"
      }

      (schema("Question") \ "discriminator" \ "propertyName").as[String] shouldBe "type"
    }

    "respect the request's minimum of one question" in {
      (properties("SystemOneRequest") \ "questions" \ "minProperties").as[Int] shouldBe 1

      an[IllegalArgumentException] should be thrownBy
        SystemOneRequest(JsString("x"), "jev-latest", Map.empty)
    }

    "respect the score rubric's minimum of one level" in {
      (properties("ScoreQuestion") \ "criteria" \ "minItems").as[Int] shouldBe 1

      an[IllegalArgumentException] should be thrownBy ScoreQuestion(Nil)
    }

    "write an undescribed choice option as null, which the schema allows" in {
      val allowed =
        (properties("ChoiceQuestion") \ "criteria" \ "additionalProperties" \ "anyOf")
          .as[Seq[JsObject]]
          .flatMap(o => (o \ "type").asOpt[String])

      allowed should contain("null")

      (Json.toJson[Question](
        ChoiceQuestion(ListMap("a" -> None))
      ) \ "criteria" \ "a").get shouldBe JsNull
    }
  }

  "What the client reads" should {

    "cover every answer kind in the Answer union's discriminator mapping" in {
      val mapping = (schema("Answer") \ "discriminator" \ "mapping").as[Map[String, String]]

      mapping shouldBe Map(
        "noul" -> "#/components/schemas/NoulAnswer",
        "choice" -> "#/components/schemas/ChoiceAnswer",
        "score" -> "#/components/schemas/ScoreAnswer"
      )

      // every mapped schema's example decodes into a modelled (non-Unknown) answer
      mapping.values.map(_.stripPrefix("#/components/schemas/")).foreach { schemaName =>
        exampleOf(schemaName).as[Answer] should not be an[UnknownAnswer]
      }
    }

    "read every property each answer schema declares" in {
      val modelled = Map(
        "NoulAnswer" -> Set("type", "noul"),
        "ChoiceAnswer" -> Set("type", "choice", "confidence", "probabilities"),
        "ScoreAnswer" -> Set("type", "score", "confidence", "legend", "probabilities")
      )

      modelled.foreach { case (schemaName, fields) =>
        withClue(s"$schemaName: ") {
          properties(schemaName).keys shouldBe fields
          required(schemaName) shouldBe fields
        }
      }
    }

    "treat the required usage counts leniently, as the official SDKs do" in {
      required("Usage") shouldBe Set("input_tokens", "output_tokens")

      Json.obj().as[Usage] shouldBe Usage(None, None)
    }
  }
}
