package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.gemini.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.gemini.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import io.cequence.openaiscala.gemini.domain.{ChatRole, Content}
import io.cequence.openaiscala.gemini.domain.response.{Candidate, CitationMetadata, FinishReason, GroundingAttribution, GroundingMetadata, LogprobsResult, SafetyRating, TopCandidates}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

object JsonFormatsSpec {
  sealed trait JsonPrintMode
  object JsonPrintMode {
    case object Compact extends JsonPrintMode
    case object Pretty extends JsonPrintMode
  }
}

class JsonFormatsSpec extends AnyWordSpecLike with Matchers {

  "JSON Formats" should {

    "serialize and deserialize candidate with log probs" in {
      prettyTestCodec[Candidate](
        Candidate(
          content = Content.textPart("Hello, world!", ChatRole.User),
          logprobsResult = Some(LogprobsResult(
            topCandidates = Nil,
            chosenCandidates = Seq(
              Candidate(
                content = Content.textPart("Hello, back!", ChatRole.Model)
              )
            )
          )),
        ),
        """{
          |  "content" : {
          |    "parts" : [ {
          |      "text" : "Hello, world!"
          |    } ],
          |    "role" : "user"
          |  },
          |  "safetyRatings" : [ ],
          |  "groundingAttributions" : [ ],
          |  "logprobsResult" : {
          |    "topCandidates" : [ ],
          |    "chosenCandidates" : [ {
          |      "content" : {
          |        "parts" : [ {
          |          "text" : "Hello, back!"
          |        } ],
          |        "role" : "model"
          |      },
          |      "safetyRatings" : [ ],
          |      "groundingAttributions" : [ ]
          |    } ]
          |  }
          |}""".stripMargin
      )
    }

    "serialize and deserialize top candidate" in {
      prettyTestCodec[TopCandidates](
        TopCandidates(
          Seq(
            Candidate(
              content = Content.textPart("Hello, there!", ChatRole.User)
            ),
            Candidate(
              content = Content.textPart("Hello, back!", ChatRole.Model)
            )
          )
        ),
        """{
          |  "candidates" : [ {
          |    "content" : {
          |      "parts" : [ {
          |        "text" : "Hello, there!"
          |      } ],
          |      "role" : "user"
          |    },
          |    "safetyRatings" : [ ],
          |    "groundingAttributions" : [ ]
          |  }, {
          |    "content" : {
          |      "parts" : [ {
          |        "text" : "Hello, back!"
          |      } ],
          |      "role" : "model"
          |    },
          |    "safetyRatings" : [ ],
          |    "groundingAttributions" : [ ]
          |  } ]
          |}""".stripMargin
      )
    }
  }

  private def testCodec[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact,
    justSemantics: Boolean = false
  )(
    implicit format: Format[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }

    println(serialized)

    if (!justSemantics) serialized shouldBe json

    val json2 = Json.parse(json).as[A]
    json2 shouldBe value
  }

  private def prettyTestCodec[A](
    value: A,
    json: String,
    justSemantics: Boolean = false
  )(
    implicit format: Format[A]
  ): Unit =
    testCodec(value, json, Pretty, justSemantics)

  private def testSerialization[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit format: Format[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }
    serialized shouldBe json
  }

  private def testDeserialization[A](
    value: A,
    json: String
  )(
    implicit format: Format[A]
  ): Unit = {
    Json.parse(json).as[A] shouldBe value
  }
}
