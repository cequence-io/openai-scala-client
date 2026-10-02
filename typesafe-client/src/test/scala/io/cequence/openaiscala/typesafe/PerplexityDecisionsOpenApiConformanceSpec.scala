package io.cequence.openaiscala.typesafe

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.HandleTypeSafeErrorCodes.toException
import io.cequence.openaiscala.typesafe.service._
import io.cequence.openaiscala.typesafe.service.impl.EndPoint
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.io.Source

/**
 * Checks the Perplexity preset against Perplexity's published Decisions API document
 * (`https://docs.perplexity.ai/openapi-gateway-preview.json`, vendored as
 * `perplexity-decisions-openapi.json` - version 0.1.0, fetched 2026-10-02): the endpoint and
 * auth scheme the preset calls, the spec's request and response examples decode into the
 * shared System One domain, what the client writes stays within the strict request schema (an
 * unknown field is a 400), the limits agree, and the documented error bodies classify by their
 * status.
 */
class PerplexityDecisionsOpenApiConformanceSpec extends AnyWordSpec with Matchers {

  private val spec: JsObject = {
    val source = Source.fromResource("perplexity-decisions-openapi.json")
    try Json.parse(source.mkString).as[JsObject]
    finally source.close()
  }

  private val schemas = (spec \ "components" \ "schemas").as[JsObject]

  private def schema(name: String): JsObject = (schemas \ name).as[JsObject]

  private def properties(name: String): JsObject = (schema(name) \ "properties").as[JsObject]

  private def required(name: String): Set[String] =
    (schema(name) \ "required").asOpt[Set[String]].getOrElse(Set.empty)

  private val post = (spec \ "paths" \ "/decisions" \ "post").as[JsObject]

  "The vendored OpenAPI document" should {

    "be the version this client was written against" in {
      (spec \ "info" \ "version").as[String] shouldBe "0.1.0"
    }
  }

  "The endpoint" should {

    "be the preset's base URL plus the decisions path, with bearer auth" in {
      val server = ((spec \ "servers")(0) \ "url").as[String]
      s"$server/decisions" shouldBe
        DecisionProviderSettings.perplexity.baseUrl + DecisionProviderSettings.perplexity.decisionsPath
      DecisionProviderSettings.perplexity.decisionsPath shouldBe EndPoint.decisions.toString

      val scheme = (spec \ "components" \ "securitySchemes" \ "HTTPBearer").as[JsObject]
      (scheme \ "type").as[String] shouldBe "http"
      (scheme \ "scheme").as[String] shouldBe "bearer"
      (post \ "security").as[Seq[JsObject]].flatMap(_.keys) should contain("HTTPBearer")
    }
  }

  "The spec's examples" should {

    "decode as the request and the response" in {
      val request =
        (post \ "requestBody" \ "content" \ "application/json" \ "examples" \ "mixed-questions" \ "value")
          .as[SystemOneRequest]
      request.model shouldBe TypeSafeModelId.pplx_decider_v1_27b
      request.questions.keySet shouldBe Set("defect", "sentiment", "severity")
      request.questions("severity") shouldBe ScoreQuestion(
        "How severe is the reported problem?",
        "Cosmetic",
        "Inconvenient",
        "Product unusable"
      )

      val response =
        (post \ "responses" \ "200" \ "content" \ "application/json" \ "examples" \ "answers" \ "value")
          .as[SystemOneResponse]
      response.model shouldBe TypeSafeModelId.pplx_decider_v1_27b
      response.noul("defect").noul shouldBe 0.9424522889347015
      response.choice("sentiment").choice shouldBe "mixed"
      response.score("severity").mostLikelyLevel shouldBe 2
      response.usage shouldBe Usage(Some(367), Some(3))
    }
  }

  "What the client writes" should {

    val written = Json
      .toJson(
        SystemOneRequest(
          JsString("state"),
          TypeSafeModelId.pplx_decider_v1_27b,
          Map(
            "noul" -> NoulQuestion("q", "yes", "no"),
            "choice" -> ChoiceQuestion("q", "a" -> "A"),
            "score" -> ScoreQuestion("q", "low", "high")
          )
        )
      )
      .as[JsObject]

    "stay within the strict request schema - Perplexity refuses any other field" in {
      (schema("DecisionsRequest") \ "additionalProperties").as[Boolean] shouldBe false
      written.keys shouldBe properties("DecisionsRequest").keys
      written.keys should contain allElementsOf required("DecisionsRequest")
    }

    "write each question with its schema's properties only" in {
      Seq(
        "noul" -> "NoulQuestion",
        "choice" -> "ChoiceQuestion",
        "score" -> "ScoreQuestion"
      ).foreach { case (name, schemaName) =>
        val question = (written \ "questions" \ name).as[JsObject]
        withClue(s"$schemaName: ") {
          question.keys should contain allElementsOf required(schemaName)
          question.keys.diff(properties(schemaName).keys) shouldBe empty
        }
      }
    }
  }

  "The limits" should {

    "agree with the domain's" in {
      (properties("ScoreQuestion") \ "criteria" \ "maxItems").as[Int] shouldBe
        ScoreQuestion.MaxLevels
      (properties("ChoiceQuestion") \ "criteria" \ "maxProperties").as[Int] shouldBe
        ChoiceQuestion.MaxOptions
      (properties("DecisionsRequest") \ "questions" \ "maxProperties").as[Int] shouldBe 128
    }
  }

  "The documented error bodies" should {

    def example(response: String): String =
      (spec \ "components" \ "responses" \ response \ "content" \ "application/json" \ "example")
        .as[JsObject]
        .toString

    "classify as their status says" in {
      toException(400, example("BadRequest")) shouldBe a[TypeSafeScalaInvalidRequestException]
      toException(401, example("Unauthorized")) shouldBe a[TypeSafeScalaUnauthorizedException]
      toException(413, example("PayloadTooLarge")) shouldBe
        a[TypeSafeScalaInvalidRequestException]
      toException(429, example("TooManyRequests")) shouldBe a[TypeSafeScalaRateLimitException]

      toException(401, example("Unauthorized")).errorType shouldBe Some("invalid_api_key")
      toException(429, example("TooManyRequests")).errorType shouldBe Some("too_many_requests")
    }
  }
}
