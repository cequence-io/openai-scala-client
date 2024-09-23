package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.service.ReflectionUtil._

import scala.reflect.runtime.universe._

// This is experimental and subject to change
trait JsonSchemaReflectionHelper {

  def jsonSchemaFor[T: TypeTag](
    dateAsNumber: Boolean = false,
    useRuntimeMirror: Boolean = false,
    explicitTypes: Map[String, JsonSchema] = Map()
  ): JsonSchema = {
    val mirror =
      if (useRuntimeMirror) runtimeMirror(getClass.getClassLoader) else typeTag[T].mirror
    asJsonSchema(typeOf[T], mirror, dateAsNumber, explicitTypes)
  }

  private def asJsonSchema(
    typ: Type,
    mirror: Mirror,
    dateAsNumber: Boolean,
    explicitTypes: Map[String, JsonSchema]
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
      case t if t subMatches typeOf[Enumeration#Value] =>
        // TODO
        // val enumValues = t.enumValues()
        JsonSchema.String()

      // java enum
      case t if t subMatches typeOf[Enum[_]] =>
        JsonSchema.String()

      // date
      case t if t matches (typeOf[java.util.Date], typeOf[org.joda.time.DateTime]) =>
        if (dateAsNumber) JsonSchema.Number() else JsonSchema.String()

      // array/seq
      case t if t subMatches (typeOf[Seq[_]], typeOf[Set[_]], typeOf[Array[_]]) =>
        val innerType = t.typeArgs.head
        val itemsSchema = asJsonSchema(innerType, mirror, dateAsNumber, explicitTypes)
        JsonSchema.Array(itemsSchema)

      case t if t.isCaseClass() =>
        caseClassAsJsonSchema(t, mirror, dateAsNumber, explicitTypes)

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
    dateAsNumber: Boolean,
    explicitTypes: Map[String, JsonSchema]
  ): JsonSchema = {
    val memberNamesAndTypes = typ.getCaseClassFields()

    val fieldSchemas = memberNamesAndTypes.toSeq.map {
      case (fieldName: String, memberType: Type) =>
        val implicitFieldSchema = asJsonSchema(memberType, mirror, dateAsNumber, explicitTypes)
        val explicitFieldSchema = explicitTypes.get(fieldName)
        (fieldName, explicitFieldSchema.getOrElse(implicitFieldSchema), memberType.isOption())
    }

    val required = fieldSchemas.collect { case (fieldName, _, false) => fieldName }
    val properties = fieldSchemas.map { case (fieldName, schema, _) => (fieldName, schema) }

    JsonSchema.Object(
      properties.toMap,
      required
    )
  }
}
