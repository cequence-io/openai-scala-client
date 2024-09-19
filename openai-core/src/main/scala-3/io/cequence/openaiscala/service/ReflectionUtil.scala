package io.cequence.openaiscala.service

import scala.quoted._

object ReflectionUtil {

  class InfixOp[T](using q: Quotes, val typ: Type[T]) {

    import q.reflect.* // Import the reflection API

    private val typeRepr: TypeRepr = TypeRepr.of[T]
    private val typeSymbol = typeRepr.typeSymbol

    private val optionInnerType: Option[TypeRepr] =
      if (typeRepr <:< TypeRepr.of[Option[_]])
        Some(typeRepr.typeArgs.head)
      else
        None

    def matches(types: Type[_]*): Boolean =
      types.exists { candidateType =>
        val candidateRepr = TypeRepr.of(using candidateType)
        typeRepr =:= candidateRepr || (optionInnerType.isDefined && optionInnerType.get =:= candidateRepr)
      }

    def subMatches(types: Type[_]*): Boolean =
      types.exists { candidateType =>
        val candidateRepr = TypeRepr.of(using candidateType)
        typeRepr <:< candidateRepr || (optionInnerType.isDefined && optionInnerType.get <:< candidateRepr)
      }

    def isOption(): Boolean =
      typeRepr <:< TypeRepr.of[Option[_]]

    def isCaseClass(): Boolean = {
      typeSymbol.isClassDef && typeSymbol.flags.is(Flags.Case)
    }

    def getCaseClassFields(): List[(String, Type[_])] = {
      import q.reflect.*

      // Ensure it's a case class
      if (isCaseClass()) {
        // Collect case accessor fields
        typeSymbol.caseFields.map { field =>
          val fieldName = field.name

          val fieldTypeRepr = field.tree match {
            case v: ValDef => v.tpt.tpe // Extract the type of the field
          }

          // Convert TypeRepr to Type[_]
          val fieldType = fieldTypeRepr.asType match {
            case '[t] => Type.of[t] // Convert TypeRepr to Type[_]
          }

          (fieldName, fieldType)
        }
      } else {
        List.empty // Not a case class, return empty list
      }
    }
  }

  def shortName(symbol: Symbol): String = {
    val paramFullName = symbol.name
    paramFullName.substring(paramFullName.lastIndexOf('.') + 1, paramFullName.length)
  }
}