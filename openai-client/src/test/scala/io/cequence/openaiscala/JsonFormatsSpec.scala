package io.cequence.openaiscala

import io.cequence.openaiscala.domain.{
  AssistantTool,
  CodeInterpreterSpec,
  FunctionSpec,
  RetrievalSpec,
  RunTool
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonFormatsSpec.{Compact, JsonPrintMode, Pretty}
import io.cequence.openaiscala.domain.response.{Run, ToolCall}
import io.cequence.openaiscala.domain.response.Run.ActionToContinueRun

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
       |    "name" : "name",
       |    "description" : "description",
       |    "parameters" : { }
       |  }
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

    "serialize and deserialize a code interpreter as a run tool" in {
      testCodec[RunTool](CodeInterpreterSpec, """{"type":"code_interpreter"}""")
    }

    "serialize and edeserialize a retrieval as a run tool" in {
      testCodec[RunTool](RetrievalSpec, """{"type":"retrieval"}""")
    }

    "serialize and deserialize a function as a run tool" in {
      testCodec[RunTool](
        FunctionSpec("name", Some("description"), Map.empty),
        functionToolJson,
        Pretty
      )
    }

    "serialize and deserialize a required action to continue run" in {
      val requiredActionToContinueRun =
        ActionToContinueRun(
          Run.SubmitToolOutputs(
            Seq(
              ToolCall.CodeInterpreterCall,
              ToolCall.RetrievalCall,
              ToolCall.FunctionCall("name", "arguments")
            )
          )
        )
      val expectedJson =
        """{
            |  "submit_tool_outputs" : {
            |    "tool_calls" : [ {
            |      "type" : "code_interpreter"
            |    }, {
            |      "type" : "retrieval"
            |    }, {
            |      "type" : "function",
            |      "function" : {
            |        "name" : "name",
            |        "arguments" : "arguments"
            |      }
            |    } ]
            |  },
            |  "type" : "submit_tool_outputs"
            |}""".stripMargin
      testCodec[ActionToContinueRun](requiredActionToContinueRun, expectedJson, Pretty)
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
