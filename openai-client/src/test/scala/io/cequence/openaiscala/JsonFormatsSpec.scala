package io.cequence.openaiscala

import io.cequence.openaiscala.domain.AssistantTool
import io.cequence.openaiscala.domain.AssistantTool.CodeInterpreterTool
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonFormatsSpec.{Compact, JsonPrintMode, Pretty}

object JsonFormatsSpec {
  sealed trait JsonPrintMode
  case object Compact extends JsonPrintMode
  case object Pretty extends JsonPrintMode
}

class JsonFormatsSpec extends AnyWordSpecLike with Matchers {

  private val functionToolJson =
    """{
       |  "type" : "function",
       |  "function" : {
       |    "description" : "description",
       |    "name" : "name",
       |    "parameters" : "parameters"
       |  }
       |}""".stripMargin

  "JSON Formats" should {

    "serialize and deserialize a code interpreter tool" in {
      testCodec[AssistantTool](CodeInterpreterTool, """{"type":"code_interpreter"}""")
    }

    "serialize and deserialize a retrieval tool" in {
      testCodec[AssistantTool](AssistantTool.RetrievalTool, """{"type":"retrieval"}""")
    }

    "serialize and deserialize a function tool" in {
      val function = AssistantTool.Function("description", "name", Some("parameters"))
      testCodec[AssistantTool](AssistantTool.FunctionTool(function), functionToolJson, Pretty)
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

}
