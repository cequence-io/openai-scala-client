package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import io.cequence.openaiscala.domain.response.TopLogprobInfo
import io.cequence.openaiscala.domain.{
  AssistantTool,
  CodeInterpreterSpec,
  FineTune,
  FunctionSpec,
  RetrievalSpec
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.Ignore
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.AssistantToolResource.{
  CodeInterpreterResources,
  FileSearchResources,
  VectorStore
}
import io.cequence.openaiscala.domain.response.AssistantToolResourceResponse.{
  CodeInterpreterResourcesResponse,
  FileSearchResourcesResponse
}
import io.cequence.openaiscala.domain.{
  AssistantToolResource,
  Attachment,
  CodeInterpreterSpec,
  FileId,
  FileSearchSpec
}
import io.cequence.openaiscala.domain.response.{AssistantToolResourceResponse, ResponseFormat}
import io.cequence.openaiscala.domain.response.ResponseFormat.{
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

@Ignore
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

  private val codeInterpreterResourcesResponseJson =
    """{
      |  "code_interpreter" : {
      |    "file_ids" : [ {
      |      "file_id" : "file-id-1"
      |    } ]
      |  }
      |}""".stripMargin

  private val fileSearchResourcesResponseJson =
    """{
      |  "file_search" : {
      |    "vector_store_ids" : [ {
      |      "file_id" : "file-id-1"
      |    } ]
      |  }
      |}""".stripMargin

  private val weightsAndBiasesIntegrationJson =
    """{
      |  "type" : "wandb",
      |  "wandb" : {
      |    "project" : "project the run belong to",
      |    "name" : "a run display name",
      |    "entity" : "integrations team",
      |    "tags" : [ "openai/finetune", "openai/chatgpt-4" ]
      |  }
      |}""".stripMargin

  private val attachmentJson =
    """{
      |  "file_id" : {
      |    "file_id" : "file-id-1"
      |  },
      |  "tools" : [ {
      |    "type" : "code_interpreter"
      |  }, {
      |    "type" : "file_search"
      |  } ]
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

    "serialize and deserialize code interpreter's resources" in {
      testCodec[AssistantToolResource](
        CodeInterpreterResources(Seq(FileId("file-id-1"), FileId("file-id-2"))),
        codeInterpreterResourcesJson,
        Pretty
      )
    }

    "serialize and deserialize file search's resources" in {
      testCodec[AssistantToolResource](
        FileSearchResources(
          Seq(FileId("file-id-1")),
          Seq(VectorStore(Seq(FileId("file-id-1")), Map("key" -> "value")))
        ),
        fileSearchResourcesJson,
        Pretty
      )
    }

    "serialize and deserialize code interpreter's resources response" in {
      testCodec[AssistantToolResourceResponse](
        CodeInterpreterResourcesResponse(Seq(FileId("file-id-1"))),
        codeInterpreterResourcesResponseJson,
        Pretty
      )
    }

    "serialize and deserialize file search's resources response" in {
      testCodec[AssistantToolResourceResponse](
        FileSearchResourcesResponse(Seq(FileId("file-id-1"))),
        fileSearchResourcesResponseJson,
        Pretty
      )
    }

    "serialize and deserialize a fine-tuning Weights and Biases integration" in {
      val integration = FineTune.WeightsAndBiases(
        "project the run belong to",
        Some("a run display name"),
        Some("integrations team"),
        Seq("openai/finetune", "openai/chatgpt-4")
      )
      testCodec[FineTune.Integration](
        integration,
        weightsAndBiasesIntegrationJson,
        Pretty
      )
    }

    "serialize and deserialize attachment" in {
      testCodec[Attachment](
        Attachment(
          fileId = Some(FileId("file-id-1")),
          tools = Seq(CodeInterpreterSpec, FileSearchSpec)
        ),
        attachmentJson,
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
