package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.JsonSchema

import scala.collection.mutable.ListBuffer

// rewritten from https://github.com/hmarr/openai-chat-tokens
object FunctionCallOpenAISerializer {
  def formatFunctionDefinitions(functions: Seq[FunctionTool]): String = {
    val lines = ListBuffer("namespace functions {", "")
    for (f: FunctionTool <- functions) {
      if (f.description.isDefined) {
        lines += s"// ${f.description.get}"
      }
      f.parameters match {
        case JsonSchema.Object(properties, _, _, _) if properties.nonEmpty =>
          lines += s"type ${f.name} = (_: {"
          lines += formatObjectProperties(f.parameters.asInstanceOf[JsonSchema.Object], 0)
          lines += "}) => any;"
        case _ =>
          lines += s"type ${f.name} = () => any;"
      }
      lines += ""
    }
    lines += "} // namespace functions"
    lines.mkString("\n")
  }

  private def formatObjectProperties(
    obj: JsonSchema.Object,
    indent: Int
  ): String = {
    val lines = scala.collection.mutable.ArrayBuffer[String]()

    for ((name, schema) <- obj.properties) {
      extractDescription(schema) match {
        case Some(v) if indent < 2 =>
          lines += s"// ${v}"
        case _ => ()
      }

      val paramType = formatType(schema, indent)

      if (obj.required.contains(name)) {
        lines += s"$name: $paramType,"
      } else {
        lines += s"$name?: $paramType,"
      }
    }

    lines.map(line => " " * indent + line).mkString("\n")
  }

  private def extractDescription(schema: JsonSchema): Option[String] = schema match {
    case JsonSchema.String(description, _) => description
    case JsonSchema.Number(description)    => description
    case JsonSchema.Integer(description)   => description
    case JsonSchema.Boolean(description)   => description
    case JsonSchema.Object(_, _, _, desc)  => desc
    case JsonSchema.Array(_, desc)         => desc
    case _                                 => None
  }

  private def formatType(
    schema: JsonSchema,
    indent: Int
  ): String = schema match {
    case JsonSchema.String(_, enumVals) if enumVals.nonEmpty =>
      enumVals.map(v => "\"" + v + "\"").mkString(" | ")
    case JsonSchema.String(_, _) => "string"
    case JsonSchema.Number(_)    => "number"
    case JsonSchema.Integer(_)   => "number"
    case JsonSchema.Boolean(_)   => "boolean"
    case JsonSchema.Null()       => "null"
    case obj: JsonSchema.Object =>
      "{" + "\n" + formatObjectProperties(obj, indent + 2) + "\n" + "}"
    case JsonSchema.Array(items, _) =>
      formatType(items, indent) + "[]"
    case _ => "any"
  }
}
