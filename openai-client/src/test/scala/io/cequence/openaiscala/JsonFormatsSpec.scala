package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonPrintMode.Pretty
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.Run.ActionToContinueRun
import io.cequence.openaiscala.domain.response.{Run, ToolCall}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class JsonFormatsSpec extends AnyWordSpecLike with Matchers with RunApiJsonFormats {

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

    "serialize and deserialize a retrieval as a run tool" in {
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
            List(
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

}
