package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import io.cequence.openaiscala.domain.response.TopLogprobInfo
import io.cequence.openaiscala.domain.{
  AssistantTool,
  CodeInterpreterSpec,
  FunctionSpec,
  RetrievalSpec
}
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

  private val functionToolJson =
    """{
       |  "type" : "function",
       |  "function" : {
       |    "name" : "name",
       |    "description" : "description",
       |    "parameters" : { }
       |  }
       |}""".stripMargin

  private val topLogprobInfoJson =
    """{
      |  "token" : "<|end|>",
      |  "logprob" : -17.942543,
      |  "bytes" : null
      |}""".stripMargin

  "JSON Formats" should {

    "serialize and deserialize a code interpreter tool" in {
      testCodec[AssistantTool](CodeInterpreterSpec, """{"type":"code_interpreter"}""")
    }

    "serialize and deserialize a retrieval tool" in {
      testCodec[AssistantTool](RetrievalSpec, """{"type":"retrieval"}""")
    }

    "serialize and deserialize a function tool" in {
      testCodec[AssistantTool](
        FunctionSpec("name", Some("description"), Map.empty),
        functionToolJson,
        Pretty
      )
    }

    "deserialize a top log prob info with null bytes" in {
      testDeserialization[TopLogprobInfo](
        TopLogprobInfo("<|end|>", -17.942543, Nil),
        topLogprobInfoJson
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
