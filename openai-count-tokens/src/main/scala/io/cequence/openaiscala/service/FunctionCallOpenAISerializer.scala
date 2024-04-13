package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.FunctionSpec

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

// rewritten from https://github.com/hmarr/openai-chat-tokens
// TODO: consider using a json schema; also avoid using mutable data structures
object FunctionCallOpenAISerializer {
  def formatFunctionDefinitions(functions: Seq[FunctionSpec]): String = {
    val lines = ListBuffer("namespace functions {", "")
    for (f: FunctionSpec <- functions) {
      if (f.description.isDefined) {
        lines += s"// ${f.description.get}"
      }
      f.parameters.get("properties") match {
        case Some(p: Map[_, _]) if p.nonEmpty =>
          lines += s"type ${f.name} = (_: {"
          lines += formatObjectProperties(f.parameters, 0)
          lines += "}) => any;"
        case None =>
          lines += s"type ${f.name} = () => any;"
        case _ =>
          // Unsupported type for function - f.name
          lines += s"type ${f.name} = () => any;"
      }
      lines += ""
    }
    lines += "} // namespace functions"
    lines.mkString("\n")
  }

  private def formatObjectProperties(
    obj: Map[String, Any],
    indent: Int
  ): String = {
    val properties: Map[String, Any] = obj("properties").asInstanceOf[Map[String, Any]]
    val required: Seq[String] = obj.get("required") match {
      case Some(r) => r.asInstanceOf[Seq[String]]
      case None    => Seq.empty[String]
    }

    val lines = scala.collection.mutable.ArrayBuffer[String]()

    for ((name, param) <- properties) {
      val paramAsInstance = param.asInstanceOf[Map[String, Any]]
      paramAsInstance.get("description") match {
        case Some(v) if indent < 2 =>
          lines += s"// ${v}"
        case _ => ()
      }

      val paramType = formatType(paramAsInstance, indent)

      if (required.contains(name)) {
        lines += s"$name: $paramType,"
      } else {
        lines += s"$name?: $paramType,"
      }
    }

    lines.map(line => " " * indent + line).mkString("\n")
  }

  private def formatType(
    param: Map[String, Any],
    indent: Int
  ): String = {
    implicit val ctMSA: ClassTag[Map[String, Any]] = ClassTag(classOf[Map[String, Any]])
    implicit val ctSS: ClassTag[Seq[String]] = ClassTag(classOf[Seq[String]])

    param.get("type") match {
      case Some("string") =>
        param.get("enum") match {
          case Some(e)
              if ctSS.runtimeClass
                .isAssignableFrom(e.getClass) && e.asInstanceOf[Seq[String]].nonEmpty =>
            e.asInstanceOf[Seq[String]].map(v => "\"" + v + "\"").mkString(" | ")
          case _ => "string"
        }
      case Some("number") | Some("integer") =>
        param.get("enum") match {
          case Some(e)
              if ctSS.runtimeClass
                .isAssignableFrom(e.getClass) && e.asInstanceOf[Seq[String]].nonEmpty =>
            e.asInstanceOf[Seq[String]].mkString(" | ")
          case _ => "number"
        }
      case Some("boolean") => "boolean"
      case Some("null")    => "null"
      case Some("object") =>
        "{" + "\n" + formatObjectProperties(param, indent + 2) + "\n" + "}"
      case Some("array") =>
        param.get("items") match {
          case Some(i)
              if ctMSA.runtimeClass
                .isAssignableFrom(i.getClass) && i.asInstanceOf[Map[String, Any]].nonEmpty =>
            formatType(i.asInstanceOf[Map[String, Any]], indent) + "[]"
          case _ => "any[]"
        }
      case _ =>
        // Unsupported type for param - param.get("type")
        "any"
    }
  }
}
