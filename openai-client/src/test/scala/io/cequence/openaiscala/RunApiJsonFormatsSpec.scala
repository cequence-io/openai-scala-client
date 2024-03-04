package io.cequence.openaiscala

import io.cequence.openaiscala.JsonPrintMode.Pretty
import io.cequence.openaiscala.RunApiJsonFormatsSpec._
import io.cequence.openaiscala.domain.response.RunStep
import io.cequence.openaiscala.domain.response.RunStep.CodeInterpreterCallOutput.{
  ImageOutput,
  LogOutput
}
import io.cequence.openaiscala.domain.response.RunStep.StepDetails.ToolCalls
import io.cequence.openaiscala.domain.response.RunStep.ToolCallDetails
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object RunApiJsonFormatsSpec {

  val messageCreationExpectedJson =
    """{
      |  "type" : "message_creation",
      |  "message_creation" : {
      |    "message_id" : "msg_abc123"
      |  }
      |}""".stripMargin

  val retrievalToolCallExpectedJson =
    """{
      |  "type" : "tool_calls",
      |  "tool_calls" : [ {
      |    "id" : "call_abc12345",
      |    "retrieval" : { },
      |    "type" : "retrieval"
      |  } ]
      |}""".stripMargin

  val codeInterpreterToolCallExpectedJson =
    """{
      |  "type" : "tool_calls",
      |  "tool_calls" : [ {
      |    "id" : "call_abc12345",
      |    "code_interpreter" : {
      |      "input" : "input to the Code Interpreter tool call",
      |      "outputs" : [ {
      |        "logs" : "log output",
      |        "type" : "logs"
      |      }, {
      |        "image" : {
      |          "file_id" : "image_file_id"
      |        },
      |        "type" : "image"
      |      } ]
      |    },
      |    "type" : "code_interpreter"
      |  } ]
      |}""".stripMargin

  val functionToolCallExpectedJson =
    """{
      |  "type" : "tool_calls",
      |  "tool_calls" : [ {
      |    "id" : "call_abc12345",
      |    "function" : {
      |      "name" : "name",
      |      "arguments" : "arguments",
      |      "outputs" : "outputs from the function"
      |    },
      |    "type" : "function"
      |  } ]
      |}""".stripMargin

}

class RunApiJsonFormatsSpec extends AnyWordSpecLike with Matchers with RunApiJsonFormats {

  "Run API JSON Formats" should {
    "serialize and deserialize a run step details of a message creation" in {
      val messageCreation = RunStep.StepDetails.MessageCreation("msg_abc123")
      testCodec[RunStep.StepDetails](messageCreation, messageCreationExpectedJson, Pretty)
    }

    "serialize and deserialize run step details of a code interpreter call with log and image outputs" in {
      val codeInterpreterToolCall = ToolCalls(
        List(
          RunStep.StepToolCall(
            "call_abc12345",
            ToolCallDetails.CodeInterpreterToolCallDetails(
              "input to the Code Interpreter tool call",
              List(LogOutput("log output"), ImageOutput("image_file_id"))
            )
          )
        )
      )
      testCodec[RunStep.StepDetails](
        codeInterpreterToolCall,
        codeInterpreterToolCallExpectedJson,
        Pretty
      )
    }

    "serialize and deserialize run step details of a retrieval tool call" in {
      val retrievalToolCall = ToolCalls(
        List(
          RunStep.StepToolCall("call_abc12345", ToolCallDetails.RetrievalToolCall(Map.empty))
        )
      )
      testCodec[RunStep.StepDetails](retrievalToolCall, retrievalToolCallExpectedJson, Pretty)
    }

    "serialize and deserialize run step details of a function tool call" in {
      val functionToolCall = ToolCalls(
        List(
          RunStep.StepToolCall(
            "call_abc12345",
            ToolCallDetails.FunctionToolCall(
              RunStep
                .FunctionCallOutput("name", "arguments", Some("outputs from the function"))
            )
          )
        )
      )

      testCodec[RunStep.StepDetails](functionToolCall, functionToolCallExpectedJson, Pretty)
    }

  }

}
