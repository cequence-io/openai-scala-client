package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{JsonSchema, JsonSchemaDescription, JsonSchemaRange}
import io.cequence.openaiscala.service.JsonSchemaShape._

import scala.reflect.runtime.universe._
import scala.util.Try

/**
 * Derives a [[JsonSchema]] from a case class - for structured output (`JsonSchemaDef(name,
 * strict, jsonSchemaFor[T]())`) or tool parameters. The same call works on Scala 2 (runtime
 * reflection, here) and Scala 3 (a macro) and derives the same schema:
 *
 *   - `Int` / `Long` / `Short` / `Byte` / `BigInt` (and the boxed / `java.math` kinds) ->
 *     integer; `Double` / `Float` / `BigDecimal` -> number; `Boolean`; `String` / `Char` /
 *     `UUID` / `URI` / `URL` / `Locale` / `Currency` / `File` / `Path` / the `java.time`
 *     values, `Duration`s and `Period`s (Scala's `Duration` too) -> string; `java.util.Date`
 * -> string, or a number with `dateAsNumber`
 *   - `Option[T]` / `java.util.Optional[T]` -> `T`, the field not required (OpenAI's strict
 *     mode requires every field, so use `strict = false` with optional fields)
 *   - `Seq` / `List` / `Set` / `Vector` / `Array` / any Scala `Iterable` or Java `Iterable`
 *     (`java.util.List`, `Set`, ...) -> array
 *   - a value class (`extends AnyVal`) -> its underlying type, as `Json.valueFormat` writes it
 *   - `Map[String, V]` / `java.util.Map` -> an open object (`additionalProperties: true`; the
 *     value type is not expressed, and OpenAI's strict mode closes every object - a map needs
 *     `strict = false`)
 *   - a case class -> object, its fields in declaration order (type parameters resolved)
 *   - a string enum: an `Enumeration` (declaration order), a Java enum (declaration order), a
 *     Scala 3 `enum` of singleton cases (declaration order), a sealed trait or class whose
 *     subclasses are all case objects (sorted) - the values are their `toString`s, so a case
 *     object overriding it (e.g. a `NamedEnumValue`) contributes its own name
 *   - [[io.cequence.openaiscala.domain.JsonSchemaDescription]] on a class or field ->
 *     `description`; [[io.cequence.openaiscala.domain.JsonSchemaRange]] on a numeric field ->
 *     `minimum` / `maximum`
 *
 * An `Either`, a sealed hierarchy with case classes (no `anyOf` here), a tuple or a recursive
 * case class is refused - on Scala 2 when called (an [[OpenAIScalaClientException]]), on Scala
 * 3 at compile time. Pass arguments by name: `useRuntimeMirror` exists on Scala 2 only.
 */
trait JsonSchemaReflectionHelper {

  /**
   * @param dateAsNumber
   *   a `java.util.Date` as a number (epoch millis - Play JSON's default format) rather than a
   *   string
   * @param useRuntimeMirror
   *   reflect with this class's class loader rather than the type tag's mirror (Scala 2 only)
   * @param explicitTypes
   *   schemas by field name, used instead of the derived ones (at any depth)
   */
  def jsonSchemaFor[T: TypeTag](
    dateAsNumber: Boolean = false,
    useRuntimeMirror: Boolean = false,
    explicitTypes: Map[String, JsonSchema] = Map()
  ): JsonSchema = {
    val mirror =
      if (useRuntimeMirror) runtimeMirror(getClass.getClassLoader) else typeTag[T].mirror

    JsonSchemaShape.toJsonSchema(
      JsonSchemaReflection.shapeOf(typeOf[T], mirror),
      dateAsNumber,
      explicitTypes
    )
  }
}

object JsonSchemaReflectionHelper extends JsonSchemaReflectionHelper

private object JsonSchemaReflection {

  private val integerTypes = Seq(
    typeOf[Int],
    typeOf[Long],
    typeOf[Short],
    typeOf[Byte],
    typeOf[BigInt],
    typeOf[java.lang.Integer],
    typeOf[java.lang.Long],
    typeOf[java.lang.Short],
    typeOf[java.lang.Byte],
    typeOf[java.math.BigInteger]
  )

  private val numberTypes = Seq(
    typeOf[Double],
    typeOf[Float],
    typeOf[BigDecimal],
    typeOf[java.lang.Double],
    typeOf[java.lang.Float],
    typeOf[java.math.BigDecimal]
  )

  private val booleanTypes = Seq(typeOf[Boolean], typeOf[java.lang.Boolean])

  private val stringTypes = Seq(
    typeOf[String],
    typeOf[Char],
    typeOf[java.lang.Character],
    typeOf[java.util.UUID],
    typeOf[java.net.URI],
    typeOf[java.net.URL],
    typeOf[java.util.Locale],
    typeOf[java.util.Currency],
    typeOf[java.io.File],
    typeOf[java.nio.file.Path],
    typeOf[java.time.ZoneId]
  )

  def shapeOf(
    tpe: Type,
    mirror: Mirror
  ): JsonSchemaShape = shapeOf(tpe, mirror, Nil)

  // `path` - the case classes being expanded, to refuse a recursive one
  private def shapeOf(
    tpe0: Type,
    mirror: Mirror,
    path: List[Symbol]
  ): JsonSchemaShape = {
    val tpe = tpe0.dealias
    val symbol = tpe.typeSymbol

    def isOneOf(types: Seq[Type]) = types.exists(tpe =:= _)
    def typeArgOf(base: Type) = tpe.baseType(base.typeSymbol).typeArgs.head

    if (isOneOf(integerTypes)) IntegerShape
    else if (isOneOf(numberTypes)) NumberShape
    else if (isOneOf(booleanTypes)) BooleanShape
    else if (isOneOf(stringTypes)) StringShape
    else if (tpe =:= typeOf[java.util.Date]) DateShape
    else if (tpe <:< typeOf[Option[_]])
      OptionShape(shapeOf(typeArgOf(typeOf[Option[_]]), mirror, path))
    else if (tpe <:< typeOf[java.util.Optional[_]])
      OptionShape(shapeOf(typeArgOf(typeOf[java.util.Optional[_]]), mirror, path))
    // an enum whose values reflection cannot reach (an Enumeration declared in a class, a
    // Java enum the mirror's class loader cannot see) stays a plain string, as 1.4.0 derived it
    else if (tpe <:< typeOf[Enumeration#Value])
      enumerationValues(tpe, mirror).fold[JsonSchemaShape](StringShape)(EnumShape(_))
    else if (tpe <:< typeOf[java.lang.Enum[_]])
      javaEnumValues(tpe, mirror).fold[JsonSchemaShape](StringShape)(EnumShape(_))
    else if (tpe <:< typeOf[java.time.temporal.TemporalAccessor]) StringShape
    // java.time.Duration / Period, and Scala's Duration
    else if (tpe <:< typeOf[java.time.temporal.TemporalAmount]) StringShape
    else if (tpe <:< typeOf[scala.concurrent.duration.Duration]) StringShape
    else if (tpe <:< typeOf[scala.collection.Map[_, _]] || tpe <:< typeOf[java.util.Map[_, _]])
      MapShape
    else if (tpe <:< typeOf[Iterable[_]])
      ArrayShape(shapeOf(typeArgOf(typeOf[Iterable[_]]), mirror, path))
    else if (tpe <:< typeOf[java.lang.Iterable[_]])
      ArrayShape(shapeOf(typeArgOf(typeOf[java.lang.Iterable[_]]), mirror, path))
    else if (symbol == definitions.ArrayClass)
      ArrayShape(shapeOf(tpe.typeArgs.head, mirror, path))
    else if (tpe <:< typeOf[Either[_, _]])
      unsupported(tpe, "an Either - no anyOf here; use a case class with two optional fields")
    else if (symbol.isClass && symbol.asClass.isDerivedValueClass)
      valueClassShape(tpe, mirror, path)
    else if (symbol.isClass && symbol.asClass.isCaseClass && !symbol.isModuleClass) {
      if (symbol.fullName.startsWith("scala.Tuple"))
        unsupported(tpe, "a tuple - use a case class")
      else if (path.contains(symbol))
        unsupported(tpe, "a recursive type")
      else
        objectShape(tpe, mirror, symbol :: path)
    } else if (symbol.isClass && symbol.asClass.isSealed)
      JsonSchemaShape.sortedEnumShape(singletonValues(tpe, symbol.asClass, mirror))
    else
      unsupported(tpe, "")
  }

  private def objectShape(
    tpe: Type,
    mirror: Mirror,
    path: List[Symbol]
  ): JsonSchemaShape = {
    val symbol = tpe.typeSymbol.asClass
    // annotations are only read once a symbol's type is complete
    symbol.typeSignature

    val constructor = tpe
      .decl(termNames.CONSTRUCTOR)
      .alternatives
      .collectFirst { case method: MethodSymbol if method.isPrimaryConstructor => method }
      .getOrElse(unsupported(tpe, "a case class without a primary constructor"))

    val fields = constructor.paramLists.headOption.getOrElse(Nil).map { param =>
      val paramType = param.typeSignature.substituteTypes(symbol.typeParams, tpe.typeArgs)
      val (minimum, maximum) = range(param.annotations)

      FieldShape(
        name = param.name.decodedName.toString,
        shape = shapeOf(paramType, mirror, path),
        description = description(param.annotations),
        minimum = minimum,
        maximum = maximum
      )
    }

    ObjectShape(fields, description(symbol.annotations))
  }

  // a value class as its one parameter's type - the way Json.valueFormat writes it
  private def valueClassShape(
    tpe: Type,
    mirror: Mirror,
    path: List[Symbol]
  ): JsonSchemaShape = {
    val symbol = tpe.typeSymbol.asClass
    val param = tpe
      .decl(termNames.CONSTRUCTOR)
      .alternatives
      .collectFirst { case method: MethodSymbol if method.isPrimaryConstructor => method }
      .flatMap(_.paramLists.flatten.headOption)
      .getOrElse(unsupported(tpe, "a value class without a parameter"))

    shapeOf(param.typeSignature.substituteTypes(symbol.typeParams, tpe.typeArgs), mirror, path)
  }

  // the values of an Enumeration that is a top-level or nested object
  private def enumerationValues(
    tpe: Type,
    mirror: Mirror
  ): Option[Seq[String]] =
    tpe match {
      case TypeRef(prefix, _, _) =>
        Try(
          JsonSchemaShape.enumerationValues(
            mirror.reflectModule(prefix.termSymbol.asModule).instance.asInstanceOf[Enumeration]
          )
        ).toOption
      case _ => None
    }

  private def javaEnumValues(
    tpe: Type,
    mirror: Mirror
  ): Option[Seq[String]] =
    Try(JsonSchemaShape.javaEnumValues(mirror.runtimeClass(tpe))).toOption

  // the case objects of a sealed hierarchy (nested sealed traits flattened), by `toString`, each
  // with its description
  private def singletonValues(
    tpe: Type,
    symbol: ClassSymbol,
    mirror: Mirror
  ): Seq[(String, Option[String])] = {
    symbol.typeSignature
    symbol.knownDirectSubclasses.toSeq.flatMap { child =>
      val childClass = child.asClass
      if (childClass.isModuleClass) {
        val module = childClass.module.asModule
        // annotations are only read once a symbol's type is complete
        childClass.typeSignature
        module.typeSignature
        // a case object reflection cannot reach (declared in a class) by its name - what its
        // toString gives unless overridden
        Seq(
          Try(mirror.reflectModule(module).instance.toString)
            .getOrElse(module.name.decodedName.toString) ->
            description(childClass.annotations ++ module.annotations)
        )
      } else if (childClass.isSealed)
        singletonValues(tpe, childClass, mirror)
      else
        unsupported(
          tpe,
          s"a sealed hierarchy with the class ${child.name} - only case objects make a string enum"
        )
    }
  }

  private def annotationArgs(
    annotations: List[Annotation],
    annotationType: Type
  ): Option[List[Any]] =
    annotations.find(_.tree.tpe =:= annotationType).map {
      _.tree.children.tail.collect { case Literal(Constant(value)) => value }
    }

  private def description(annotations: List[Annotation]): Option[String] =
    annotationArgs(annotations, typeOf[JsonSchemaDescription]).flatMap {
      _.collectFirst { case text: String => text }
    }

  private def range(annotations: List[Annotation]): (Option[Double], Option[Double]) =
    annotationArgs(annotations, typeOf[JsonSchemaRange]) match {
      case Some(List(min: java.lang.Number, max: java.lang.Number)) =>
        (Some(min.doubleValue), Some(max.doubleValue))
      case _ => (None, None)
    }

  private def unsupported(
    tpe: Type,
    reason: String
  ): Nothing =
    throw new OpenAIScalaClientException(
      s"JSON schema derivation doesn't support the type '$tpe'" +
        (if (reason.nonEmpty) s" - $reason." else ".")
    )
}
