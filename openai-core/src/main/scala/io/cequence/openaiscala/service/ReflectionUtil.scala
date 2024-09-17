package io.cequence.openaiscala.service

import scala.reflect.runtime.universe._

object ReflectionUtil {

  implicit class InfixOp(val typ: Type) {

    private val optionInnerType =
      if (typ <:< typeOf[Option[_]])
        Some(typ.typeArgs.head)
      else
        None

    def matches(types: Type*): Boolean =
      types.exists(typ =:= _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get =:= _))

    def subMatches(types: Type*): Boolean =
      types.exists(typ <:< _) ||
        (optionInnerType.isDefined && types.exists(optionInnerType.get <:< _))

    def isOption(): Boolean =
      typ <:< typeOf[Option[_]]
  }

  def isCaseClass(runType: Type): Boolean =
    runType.members.exists(m => m.isMethod && m.asMethod.isCaseAccessor)

  def shortName(symbol: Symbol): String = {
    val paramFullName = symbol.fullName
    paramFullName.substring(paramFullName.lastIndexOf('.') + 1, paramFullName.length)
  }

  def getCaseClassMemberNamesAndTypes(
    runType: Type
  ): Traversable[(String, Type)] =
    runType.decls.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => (shortName(m), m.returnType)
    }
}
