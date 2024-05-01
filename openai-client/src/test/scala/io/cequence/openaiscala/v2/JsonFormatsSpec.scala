package io.cequence.openaiscala.v2

import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.v2.JsonFormats._
import io.cequence.openaiscala.v2.domain.AssistantToolResource.{
  CodeInterpreterResources,
  FileSearchResources,
  VectorStore
}
import io.cequence.openaiscala.v2.domain.{AssistantToolResource, FileId}
import io.cequence.openaiscala.v2.domain.response.ResponseFormat
import io.cequence.openaiscala.v2.domain.response.ResponseFormat.{
  JsonObjectResponse,
  StringResponse,
  TextResponse
}

object JsonFormatsSpec {
  sealed trait JsonPrintMode
  object JsonPrintMode {
    case object Compact extends JsonPrintMode
    case object Pretty extends JsonPrintMode
  }
}

class JsonFormatsSpec extends AnyWordSpecLike with Matchers {

  private val textResponseJson =
    """{
      |  "type" : "text"
      |}""".stripMargin

  private val jsonObjectResponseJson =
    """{
      |  "type" : "json_object"
      |}""".stripMargin

  private val codeInterpreterResourcesJson =
    """{
      |  "code_interpreter" : {
      |    "file_ids" : [ {
      |      "file_id" : "file-id-1"
      |    }, {
      |      "file_id" : "file-id-2"
      |    } ]
      |  }
      |}""".stripMargin

  private val fileSearchResourcesJson =
    """{
      |  "file_search" : {
      |    "vector_store_ids" : [ {
      |      "file_id" : "file-id-1"
      |    } ],
      |    "vector_stores" : [ {
      |      "file_ids" : [ {
      |        "file_id" : "file-id-1"
      |      } ],
      |      "metadata" : {
      |        "key" : "value"
      |      }
      |    } ]
      |  }
      |}""".stripMargin

  "JSON Formats" should {

    "serialize and deserialize a String response format" in {
      testCodec[ResponseFormat](StringResponse: ResponseFormat, """"auto"""")
    }

    "serialize and deserialize a Text response format" in {
      testCodec[ResponseFormat](TextResponse, textResponseJson, Pretty)
    }

    "serialize and deserialize a JSON object response format" in {
      testCodec[ResponseFormat](JsonObjectResponse, jsonObjectResponseJson, Pretty)
    }

    "serialize and deserialize code interpreter resources" in {
      testCodec[AssistantToolResource](
        CodeInterpreterResources(Seq(FileId("file-id-1"), FileId("file-id-2"))),
        codeInterpreterResourcesJson,
        Pretty
      )
    }

    "serialize and deserialize file search resources" in {
      testCodec[AssistantToolResource](
        FileSearchResources(
          Seq(FileId("file-id-1")),
          Seq(VectorStore(Seq(FileId("file-id-1")), Map("key" -> "value")))
        ),
        fileSearchResourcesJson,
        Pretty
      )
    }

  }

  private def testCodec[A](
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

    Json.parse(json).as[A] shouldBe value
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
