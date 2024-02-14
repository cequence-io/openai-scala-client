package io.cequence.openaiscala.domain

sealed abstract class AssistantTool private (val `type`: AssistantTool.Type)

object AssistantTool {

  case object CodeInterpreterTool extends AssistantTool(Type.code_interpreter)

  case object RetrievalTool extends AssistantTool(Type.retrieval)

  final case class FunctionTool(function: Function) extends AssistantTool(Type.function)

  // TODO: check whether this approach is ok, elsewhere sealed traits are flat
  sealed trait Type extends EnumValue

  object Type extends EnumValue {
    case object code_interpreter extends Type

    case object retrieval extends Type

    case object function extends Type

    // TODO: check whether this approach is ok, elsewhere values are provided to JsonFormat directly
    def values: Seq[Type] = Seq(code_interpreter, retrieval, function)
  }

  final case class Function(
    // FIXME: description should be optional in request and mandatory in response
    description: String,
    name: String,
    parameters: Option[String]
  )

}
