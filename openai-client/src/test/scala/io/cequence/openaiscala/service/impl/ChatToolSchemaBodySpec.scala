package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.{ChatCompletionTool, JsonSchema}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsValue}

/**
 * Function tool parameters in the chat completions body: a strict tool closed, a non-strict
 * one as given - its nullable fields optional on the hosts that mishandle them.
 */
class ChatToolSchemaBodySpec extends AnyWordSpec with Matchers {

  private class Maker(optional: Boolean) extends ChatCompletionBodyMaker {
    override protected def optionalToolFields: Boolean = optional

    def parameters(tool: ChatCompletionTool): JsValue =
      createToolBodyParams(Seq(tool), None).collectFirst { case (_, Some(json)) =>
        (json \ 0 \ "function" \ "parameters").get
      }.get
  }

  private val maker = new Maker(optional = false)
  private val optionalMaker = new Maker(optional = true)

  private def str = JsonSchema.String()

  private val detail = JsonSchema.Object(
    properties = Seq("code" -> str, "note" -> JsonSchema.Nullable(str)),
    required = Seq("code", "note")
  )

  private val params = JsonSchema.Object(
    properties = Seq(
      "city" -> str,
      "detail" -> JsonSchema.Nullable(detail),
      "items" -> JsonSchema.Array(detail),
      "unit" -> JsonSchema.Nullable(str)
    ),
    required = Seq("city", "detail", "items", "unit")
  )

  private def objects(json: JsValue): Seq[JsObject] =
    json match {
      case o: JsObject =>
        val isObject = (o \ "type").asOpt[String].contains("object")
        val here = Seq(o).filter(_ => isObject)
        here ++ o.values.toSeq.flatMap(objects)

      case a: JsArray =>
        a.value.toSeq.flatMap(objects)

      case _ =>
        Nil
    }

  "createToolBodyParams" should {

    "close every object of a strict tool and keep all properties required" in {
      val json = maker.parameters(FunctionTool("f", None, params, strict = Some(true)))
      val all = objects(json)

      all.size shouldBe 3
      all.foreach { o =>
        (o \ "additionalProperties").asOpt[Boolean] shouldBe Some(false)
        (o \ "required").as[Seq[String]].toSet shouldBe (o \ "properties").as[JsObject].keys
      }
      (json \ "properties" \ "detail" \ "anyOf").asOpt[JsArray] should not be empty
    }

    "send a non-strict tool's parameters as given - nullable fields as anyOf" in {
      val json = maker.parameters(FunctionTool("f", None, params))

      (json \ "required").as[Seq[String]] shouldBe Seq("city", "detail", "items", "unit")
      (json \ "properties" \ "unit" \ "anyOf").asOpt[JsArray] should not be empty
    }

    "know the hosts that get the optional form" in {
      ChatCompletionBodyMaker.optionalToolFieldHost(
        "https://api.cerebras.ai/v1/"
      ) shouldBe true
      ChatCompletionBodyMaker.optionalToolFieldHost(
        "https://api.together.xyz/v1/"
      ) shouldBe true
      ChatCompletionBodyMaker.optionalToolFieldHost("https://api.meta.ai/v1/") shouldBe true
      ChatCompletionBodyMaker.optionalToolFieldHost("https://api.groq.com/openai/v1/") shouldBe
        false
      ChatCompletionBodyMaker.optionalToolFieldHost(
        "https://api.openai.com/v1/"
      ) shouldBe false
    }

    "send a non-strict tool's nullable fields as optional ones on those hosts" in {
      val json = optionalMaker.parameters(FunctionTool("f", None, params))

      (json \ "required").as[Seq[String]] shouldBe Seq("city", "items")
      (json \ "properties" \ "unit").get shouldBe (
        play.api.libs.json.Json.obj("type" -> "string")
      )
      (json \ "properties" \ "detail" \ "type").as[String] shouldBe "object"
      (json \ "properties" \ "detail" \ "required").as[Seq[String]] shouldBe Seq("code")
      (json \ "properties" \ "items" \ "items" \ "required").as[Seq[String]] shouldBe
        Seq("code")
      json.toString should not include "anyOf"
    }
  }
}
