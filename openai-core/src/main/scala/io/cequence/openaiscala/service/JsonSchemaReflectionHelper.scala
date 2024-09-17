package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.JsonSchema

import scala.reflect.runtime.universe._
import io.cequence.openaiscala.service.ReflectionUtil._

// This is experimental and subject to change
trait JsonSchemaReflectionHelper {

  def jsonSchemaFor[T: TypeTag](
    dateAsNumber: Boolean = false,
    useRuntimeMirror: Boolean = false
  ): JsonSchema = {
    val mirror = if (useRuntimeMirror) runtimeMirror(getClass.getClassLoader) else typeTag[T].mirror
    asJsonSchema(typeOf[T], mirror, dateAsNumber)
  }

  private def asJsonSchema(
    typ: Type,
    mirror: Mirror,
    dateAsNumber: Boolean = false
  ): JsonSchema =
    typ match {
      // number
      case t
          if t matches (typeOf[Int], typeOf[Long], typeOf[Byte], typeOf[Double], typeOf[
            Float
          ], typeOf[BigDecimal], typeOf[BigInt]) =>
        JsonSchema.Number()

      // boolean
      case t if t matches typeOf[Boolean] =>
        JsonSchema.Boolean()

      // string
      case t if t matches (typeOf[String], typeOf[java.util.UUID]) =>
        JsonSchema.String()

      // enum
      case t if t subMatches (typeOf[Enumeration#Value], typeOf[Enum[_]]) =>
        JsonSchema.String()

      // date
      case t if t matches (typeOf[java.util.Date], typeOf[org.joda.time.DateTime]) =>
        if (dateAsNumber) JsonSchema.Number() else JsonSchema.String()

      // array/seq
      case t if t subMatches (typeOf[Seq[_]], typeOf[Set[_]], typeOf[Array[_]]) =>
        val innerType = t.typeArgs.head
        val itemsSchema = asJsonSchema(innerType, mirror, dateAsNumber)
        JsonSchema.Array(itemsSchema)

      case t if isCaseClass(t) =>
        caseClassAsJsonSchema(t, mirror, dateAsNumber)

      // map - TODO
      case t if t subMatches (typeOf[Map[String, _]]) =>
        throw new OpenAIScalaClientException(
          "JSON schema reflection doesn't support 'Map' type."
        )

      // either value - TODO
      case t if t matches typeOf[Either[_, _]] =>
        throw new OpenAIScalaClientException(
          "JSON schema reflection doesn't support 'Either' type."
        )

      // otherwise
      case _ =>
        val typeName =
          if (typ <:< typeOf[Option[_]])
            s"Option[${typ.typeArgs.head.typeSymbol.fullName}]"
          else
            typ.typeSymbol.fullName
        throw new OpenAIScalaClientException(s"Type ${typeName} unknown.")
    }

  private def caseClassAsJsonSchema(
    typ: Type,
    mirror: Mirror,
    dateAsNumber: Boolean
  ): JsonSchema = {
    val memberNamesAndTypes = getCaseClassMemberNamesAndTypes(typ)

    val fieldSchemas = memberNamesAndTypes.toSeq.map {
      case (fieldName: String, memberType: Type) =>
        val fieldSchema = asJsonSchema(memberType, mirror, dateAsNumber)
        (fieldName, fieldSchema, memberType.isOption())
    }

    val required = fieldSchemas.collect { case (fieldName, _, false) => fieldName }
    val properties = fieldSchemas.map { case (fieldName, schema, _) => (fieldName, schema) }

    JsonSchema.Object(
      properties.toMap,
      required
    )
  }
}
