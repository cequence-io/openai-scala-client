package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats.assistantsFunctionSpecFormat
import io.cequence.openaiscala.JsonUtil.{StringAnyMapFormat, enumFormat}
import io.cequence.openaiscala.domain.response.Run.ActionToContinueRun
import io.cequence.openaiscala.domain.response.RunStep._
import io.cequence.openaiscala.domain.response.ToolCall.{
  CodeInterpreterCall,
  FunctionCall,
  RetrievalCall
}
import io.cequence.openaiscala.domain.response.{Run, RunStep, ToolCall}
import io.cequence.openaiscala.domain.{
  CodeInterpreterSpec,
  FunctionSpec,
  RetrievalSpec,
  RunTool,
  ToolOutput
}
import play.api.libs.functional.syntax._
import play.api.libs.json._

object RunApiJsonFormats extends RunApiJsonFormats

trait RunApiJsonFormats {

  implicit lazy val runFormat: Format[Run] = Json.format[Run]

  implicit val actionToContinueRunFormat: Format[Run.ActionToContinueRun] = {
    val implicitWriter = Json.writes[Run.ActionToContinueRun]

    val writes = (action: ActionToContinueRun) => {
      implicitWriter.writes(action) + ("type" -> JsString("submit_tool_outputs"))
    }
    Format(Json.reads[Run.ActionToContinueRun], Writes(writes))
  }

  implicit lazy val submitToolOutputsFormat: Format[Run.SubmitToolOutputs] =
    Json.format[Run.SubmitToolOutputs]

  implicit val runToolFormat: Format[RunTool] = {
    val typeDiscriminatorKey = "type"

    Format[RunTool](
      (json: JsValue) => {
        (json \ typeDiscriminatorKey).validate[String].flatMap {
          case "code_interpreter" => JsSuccess(CodeInterpreterSpec)
          case "retrieval"        => JsSuccess(RetrievalSpec)
          case "function"         => json.validate[FunctionSpec](assistantsFunctionSpecFormat)
          case _                  => JsError("Unknown type")
        }
      },
      { (tool: RunTool) =>
        val commonJson = Json.obj {
          val discriminatorValue = tool match {
            case CodeInterpreterSpec   => "code_interpreter"
            case RetrievalSpec         => "retrieval"
            case FunctionSpec(_, _, _) => "function"
          }
          typeDiscriminatorKey -> discriminatorValue
        }
        tool match {
          case CodeInterpreterSpec => commonJson
          case RetrievalSpec       => commonJson
          case ft: FunctionSpec =>
            commonJson ++ Json.toJson(ft)(assistantsFunctionSpecFormat).as[JsObject]
        }
      }
    )
  }

  implicit lazy val toolCallFormat: Format[ToolCall] = Format(
    (JsPath \ "type").read[String].flatMap {
      case "code_interpreter" => Reads.pure(CodeInterpreterCall)
      case "retrieval"        => Reads.pure(RetrievalCall)
      case "function" =>
        ((JsPath \ "function" \ "name").read[String] and
          (JsPath \ "function" \ "arguments").read[String])(FunctionCall.apply _)
      case _ => Reads(_ => JsError("Unknown type"))
    },
    _ match {
      case CodeInterpreterCall => Json.obj("type" -> "code_interpreter")
      case RetrievalCall       => Json.obj("type" -> "retrieval")
      case FunctionCall(name, arguments) =>
        Json.obj(
          "type" -> "function",
          "function" -> Json.obj(
            "name" -> name,
            "arguments" -> arguments
          )
        )
    }
  )

  implicit lazy val runErrorFormat: Format[Run.Error] = Json.format[Run.Error]
  implicit lazy val runErrorCodeFormat: Format[Run.Error.Code] =
    enumFormat[Run.Error.Code](Run.Error.Code.values: _*)
  implicit lazy val runStatusFormat: Format[Run.Status] =
    enumFormat[Run.Status](Run.Status.values: _*)
  implicit lazy val runUsageFormat: Format[Run.Usage] = Json.format[Run.Usage]

  implicit lazy val runStepFormat: Format[RunStep] = Json.format[RunStep]
  implicit lazy val runStepStatusFormat: Format[RunStep.Status] =
    enumFormat[RunStep.Status](RunStep.Status.values: _*)
  implicit lazy val runStepTypeFormat: Format[RunStep.Type] =
    enumFormat[RunStep.Type](RunStep.Type.values: _*)
  implicit lazy val runStepErrorCodeFormat: Format[RunStep.Error.Code] =
    enumFormat[RunStep.Error.Code](RunStep.Error.Code.values: _*)
  implicit lazy val runStepErrorFormat: Format[RunStep.Error] = Json.format[RunStep.Error]

  implicit lazy val codeInterpreterToolCallDetailsFormat
    : Format[ToolCallDetails.CodeInterpreterToolCallDetails] =
    Json.format[ToolCallDetails.CodeInterpreterToolCallDetails]
  implicit lazy val retrievalToolCallFormat: OFormat[ToolCallDetails.RetrievalToolCall] = {
    implicit lazy val format = StringAnyMapFormat
    Json.format[ToolCallDetails.RetrievalToolCall]
  }
  implicit lazy val functionCallOutputFormat: Format[FunctionCallOutput] =
    Json.format[FunctionCallOutput]
  implicit lazy val functionToolCallFormat: OFormat[ToolCallDetails.FunctionToolCall] =
    Json.format[ToolCallDetails.FunctionToolCall]

  implicit val codeInterpreterCallOutputFormat: Format[CodeInterpreterCallOutput] =
    new Format[CodeInterpreterCallOutput] {
      implicit lazy val logOutputFormat: Format[CodeInterpreterCallOutput.LogOutput] =
        Json.format[CodeInterpreterCallOutput.LogOutput]

      def writes(output: CodeInterpreterCallOutput): JsValue = {
        output match {
          case l: CodeInterpreterCallOutput.LogOutput =>
            logOutputFormat.writes(l).as[JsObject] + ("type" -> JsString("logs"))
          case i: CodeInterpreterCallOutput.ImageOutput =>
            Json.obj(
              "image" -> Json.obj("file_id" -> i.fileId),
              "type" -> "image"
            )
        }
      }

      def reads(json: JsValue): JsResult[CodeInterpreterCallOutput] = {
        (json \ "type").as[String] match {
          case "logs" => logOutputFormat.reads(json)
          case "image" =>
            (json \ "image")
              .validate[JsObject]
              .flatMap(obj =>
                (obj \ "file_id")
                  .validate[String]
                  .map(fileId => CodeInterpreterCallOutput.ImageOutput(fileId))
              )
        }
      }
    }

  implicit val stepToolCallReads: Reads[StepToolCall] = new Reads[StepToolCall] {
    def reads(json: JsValue): JsResult[StepToolCall] = {
      for {
        id <- (json \ "id").validate[String]
        toolType <- (json \ "type").validate[String]
        details <- toolType match {
          case "code_interpreter" =>
            (json \ "code_interpreter").validate[JsValue].flatMap { interpreterJson =>
              for {
                input <- (interpreterJson \ "input").validate[String]
                outputs <- (interpreterJson \ "outputs")
                  .validate[Seq[CodeInterpreterCallOutput]](
                    Reads.seq(codeInterpreterCallOutputFormat)
                  )
              } yield ToolCallDetails.CodeInterpreterToolCallDetails(input, outputs)
            }
          case "retrieval" =>
            implicit val mapFormat = StringAnyMapFormat
            (json \ "retrieval")
              .validate[Map[String, Any]]
              .map(ToolCallDetails.RetrievalToolCall)
          case "function" =>
            (json \ "function")
              .validate[FunctionCallOutput](functionCallOutputFormat)
              .map(ToolCallDetails.FunctionToolCall)
        }
      } yield StepToolCall(id, details)
    }
  }

  implicit val stepToolCallWrites: Writes[StepToolCall] = (stepToolCall: StepToolCall) => {
    val base = Json.obj("id" -> stepToolCall.id)
    stepToolCall.details match {
      case d: ToolCallDetails.CodeInterpreterToolCallDetails =>
        base ++ Json.obj(
          "code_interpreter" -> Json.toJson(d)(codeInterpreterToolCallDetailsFormat),
          "type" -> "code_interpreter"
        )
      case d: ToolCallDetails.RetrievalToolCall =>
        base ++ Json.toJsObject(d)(retrievalToolCallFormat) ++ Json.obj("type" -> "retrieval")
      case d: ToolCallDetails.FunctionToolCall =>
        base ++ Json.toJsObject(d)(functionToolCallFormat) ++ Json.obj("type" -> "function")
    }
  }

  implicit lazy val toolCallsFormat: Format[StepDetails.ToolCalls] =
    new Format[StepDetails.ToolCalls] {
      override def writes(o: StepDetails.ToolCalls): JsValue =
        Json.toJson(o.tool_calls)(Writes.seq(stepToolCallWrites))

      override def reads(json: JsValue): JsResult[StepDetails.ToolCalls] =
        (JsPath \ "tool_calls")
          .read[Seq[StepToolCall]](Reads.seq(stepToolCallReads))
          .map(StepDetails.ToolCalls.apply)
          .reads(json)
    }

  implicit lazy val stepDetailsFormat: Format[StepDetails] = new Format[StepDetails] {
    def writes(stepDetails: StepDetails): JsValue = {
      stepDetails match {
        case m: StepDetails.MessageCreation =>
          Json.obj(
            "type" -> "message_creation",
            "message_creation" -> messageCreationFormat.writes(m)
          )
        case t: StepDetails.ToolCalls =>
          Json.obj(
            "type" -> "tool_calls",
            "tool_calls" -> toolCallsFormat.writes(t)
          )
      }
    }

    implicit lazy val messageCreationFormat: Format[StepDetails.MessageCreation] =
      Json.format[StepDetails.MessageCreation]

    def reads(json: JsValue): JsResult[StepDetails] = {
      (json \ "type").as[String] match {
        case "message_creation" =>
          (json \ "message_creation").validate[StepDetails.MessageCreation]
        case "tool_calls" =>
          (json \ "tool_calls").validate[Seq[StepToolCall]].map(StepDetails.ToolCalls)
      }
    }
  }

  implicit lazy val toolOutputFormat: Format[ToolOutput] = Json.format[ToolOutput]

}
