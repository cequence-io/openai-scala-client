package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{JsonSchema, JsonSchemaDescription, JsonSchemaRange}
import io.cequence.openaiscala.service.JsonSchemaShape._

import scala.quoted.*

/**
 * Derives a [[JsonSchema]] from a case class - for structured output
 * (`JsonSchemaDef(name, strict, jsonSchemaFor[T]())`) or tool parameters. The same call
 * works on Scala 2 (runtime reflection) and Scala 3 (a macro, here) and derives the same
 * schema:
 *
 *   - `Int` / `Long` / `Short` / `Byte` / `BigInt` (and the boxed / `java.math` kinds) ->
 *     integer; `Double` / `Float` / `BigDecimal` -> number; `Boolean`; `String` / `Char` /
 *     `UUID` / `URI` / `URL` / `Locale` / `Currency` / `File` / `Path` / the `java.time` values,
 *     `Duration`s and `Period`s (Scala's `Duration` too) -> string; `java.util.Date` -> string,
 *     or a number with `dateAsNumber`
 *   - `Option[T]` / `java.util.Optional[T]` -> `T`, the field not required (OpenAI's strict
 *     mode requires every field, so use `strict = false` with optional fields)
 *   - `Seq` / `List` / `Set` / `Vector` / `Array` / any Scala `Iterable` or Java `Iterable`
 *     (`java.util.List`, `Set`, ...) -> array
 *   - a value class (`extends AnyVal`) -> its underlying type, as `Json.valueFormat` writes it
 *   - `Map[String, V]` / `java.util.Map` -> an open object (`additionalProperties: true`; the
 *     value type is not expressed, and OpenAI's strict mode closes every object - a map needs
 *     `strict = false`)
 *   - a case class -> object, its fields in declaration order (type parameters resolved)
 *   - a string enum: an `Enumeration` (declaration order), a Java enum (declaration order),
 *     a Scala 3 `enum` of singleton cases (declaration order), a sealed trait or class whose
 *     subclasses are all case objects (sorted) - the values are their `toString`s, so a case
 *     object overriding it (e.g. a `NamedEnumValue`) contributes its own name
 *   - [[io.cequence.openaiscala.domain.JsonSchemaDescription]] on a class or field ->
 *     `description`; [[io.cequence.openaiscala.domain.JsonSchemaRange]] on a numeric field ->
 *     `minimum` / `maximum`
 *
 * An `Either`, a sealed hierarchy with case classes (no `anyOf` here), a tuple or a recursive
 * case class is refused - on Scala 2 when called, on Scala 3 at compile time. Pass arguments
 * by name: Scala 2's `useRuntimeMirror` does not exist here.
 */
trait JsonSchemaReflectionHelper {

  /**
   * @param dateAsNumber
   *   a `java.util.Date` as a number (epoch millis - Play JSON's default format) rather than
   *   a string
   * @param explicitTypes
   *   schemas by field name, used instead of the derived ones (at any depth)
   */
  inline def jsonSchemaFor[T](
    dateAsNumber: Boolean = false,
    explicitTypes: Map[String, JsonSchema] = Map()
  ): JsonSchema =
    JsonSchemaShape.toJsonSchema(JsonSchemaMacros.shapeOf[T], dateAsNumber, explicitTypes)
}

object JsonSchemaReflectionHelper extends JsonSchemaReflectionHelper

/** Internal - the macro behind the Scala 3 `jsonSchemaFor`. */
object JsonSchemaMacros {

  inline def shapeOf[T]: JsonSchemaShape = ${ shapeOfImpl[T] }

  def shapeOfImpl[T: Type](using q: Quotes): Expr[JsonSchemaShape] = {
    import q.reflect.*

    val integerTypes = List(
      TypeRepr.of[Int],
      TypeRepr.of[Long],
      TypeRepr.of[Short],
      TypeRepr.of[Byte],
      TypeRepr.of[BigInt],
      TypeRepr.of[java.lang.Integer],
      TypeRepr.of[java.lang.Long],
      TypeRepr.of[java.lang.Short],
      TypeRepr.of[java.lang.Byte],
      TypeRepr.of[java.math.BigInteger]
    )

    val numberTypes = List(
      TypeRepr.of[Double],
      TypeRepr.of[Float],
      TypeRepr.of[BigDecimal],
      TypeRepr.of[java.lang.Double],
      TypeRepr.of[java.lang.Float],
      TypeRepr.of[java.math.BigDecimal]
    )

    val booleanTypes = List(TypeRepr.of[Boolean], TypeRepr.of[java.lang.Boolean])

    val stringTypes = List(
      TypeRepr.of[String],
      TypeRepr.of[Char],
      TypeRepr.of[java.lang.Character],
      TypeRepr.of[java.util.UUID],
      TypeRepr.of[java.net.URI],
      TypeRepr.of[java.net.URL],
      TypeRepr.of[java.util.Locale],
      TypeRepr.of[java.util.Currency],
      TypeRepr.of[java.io.File],
      TypeRepr.of[java.nio.file.Path],
      TypeRepr.of[java.time.ZoneId]
    )

    val optionClass = Symbol.requiredClass("scala.Option")
    val optionalClass = Symbol.requiredClass("java.util.Optional")
    val iterableClass = Symbol.requiredClass("scala.collection.Iterable")
    val javaIterableClass = Symbol.requiredClass("java.lang.Iterable")
    val mapClass = Symbol.requiredClass("scala.collection.Map")
    val javaMapClass = Symbol.requiredClass("java.util.Map")
    val eitherClass = Symbol.requiredClass("scala.util.Either")
    val temporalAmountClass = Symbol.requiredClass("java.time.temporal.TemporalAmount")
    val scalaDurationClass = Symbol.requiredClass("scala.concurrent.duration.Duration")
    val javaEnumClass = Symbol.requiredClass("java.lang.Enum")
    val temporalClass = Symbol.requiredClass("java.time.temporal.TemporalAccessor")
    val enumerationValueClass = TypeRepr.of[Enumeration#Value].typeSymbol
    val descriptionClass = TypeRepr.of[JsonSchemaDescription].typeSymbol
    val rangeClass = TypeRepr.of[JsonSchemaRange].typeSymbol

    def unsupported(
      tpe: TypeRepr,
      reason: String
    ): Nothing =
      report.errorAndAbort(
        s"jsonSchemaFor doesn't support the type '${tpe.show}'" +
          (if (reason.nonEmpty) s" - $reason." else ".")
      )

    def strip(tpe: TypeRepr): TypeRepr =
      tpe.dealias match {
        case AnnotatedType(underlying, _) => strip(underlying)
        case other                        => other
      }

    def annotation(
      annotations: List[Term],
      annotationClass: Symbol
    ): Option[Term] =
      annotations.find(_.tpe.typeSymbol == annotationClass)

    def descriptionOf(annotations: List[Term]): Expr[Option[String]] =
      annotation(annotations, descriptionClass) match {
        case Some(annot) => '{ Some(${ annot.asExprOf[JsonSchemaDescription] }.value) }
        case None        => '{ Option.empty[String] }
      }

    def rangeOf(annotations: List[Term]): (Expr[Option[Double]], Expr[Option[Double]]) =
      annotation(annotations, rangeClass) match {
        case Some(annot) =>
          val range = annot.asExprOf[JsonSchemaRange]
          ('{ Some($range.min) }, '{ Some($range.max) })
        case None =>
          ('{ Option.empty[Double] }, '{ Option.empty[Double] })
      }

    // `path` - the case classes being expanded, to refuse a recursive one
    def shapeOf(
      tpe0: TypeRepr,
      path: List[Symbol]
    ): Expr[JsonSchemaShape] = {
      val tpe = strip(tpe0.widen)
      val symbol = tpe.typeSymbol

      def isOneOf(types: List[TypeRepr]) = types.exists(tpe =:= _)
      def typeArgOf(baseClass: Symbol) = tpe.baseType(baseClass).typeArgs.head

      if (isOneOf(integerTypes)) '{ IntegerShape }
      else if (isOneOf(numberTypes)) '{ NumberShape }
      else if (isOneOf(booleanTypes)) '{ BooleanShape }
      else if (isOneOf(stringTypes)) '{ StringShape }
      else if (tpe =:= TypeRepr.of[java.util.Date]) '{ DateShape }
      else if (tpe.derivesFrom(optionClass))
        '{ OptionShape(${ shapeOf(typeArgOf(optionClass), path) }) }
      else if (tpe.derivesFrom(optionalClass))
        '{ OptionShape(${ shapeOf(typeArgOf(optionalClass), path) }) }
      else if (tpe.derivesFrom(enumerationValueClass)) enumerationShape(tpe)
      else if (tpe.derivesFrom(javaEnumClass)) {
        // the values are read by plain helpers: a member selected on a spliced tree is typed
        // again against its concrete type, which fails for some (e.g. `Class[T]#T`)
        val enumClass = Literal(ClassOfConstant(tpe)).asExprOf[Class[?]]
        '{ EnumShape(JsonSchemaShape.javaEnumValues($enumClass)) }
      } else if (tpe.derivesFrom(temporalClass)) '{ StringShape }
      // java.time.Duration / Period, and Scala's Duration
      else if (tpe.derivesFrom(temporalAmountClass) || tpe.derivesFrom(scalaDurationClass))
        '{ StringShape }
      else if (tpe.derivesFrom(mapClass) || tpe.derivesFrom(javaMapClass)) '{ MapShape }
      else if (tpe.derivesFrom(iterableClass))
        '{ ArrayShape(${ shapeOf(typeArgOf(iterableClass), path) }) }
      else if (tpe.derivesFrom(javaIterableClass))
        '{ ArrayShape(${ shapeOf(typeArgOf(javaIterableClass), path) }) }
      else if (symbol == defn.ArrayClass)
        '{ ArrayShape(${ shapeOf(tpe.typeArgs.head, path) }) }
      else if (tpe.derivesFrom(eitherClass))
        unsupported(tpe, "an Either - no anyOf here; use a case class with two optional fields")
      else if (
        symbol.isClassDef && tpe.derivesFrom(defn.AnyValClass) && !symbol.flags.is(Flags.Module)
      )
        valueClassShape(tpe, symbol, path)
      else if (
        symbol.isClassDef && symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Module)
      ) {
        if (symbol.fullName.startsWith("scala.Tuple"))
          unsupported(tpe, "a tuple - use a case class")
        else if (path.contains(symbol))
          unsupported(tpe, "a recursive type")
        else
          objectShape(tpe, symbol, symbol :: path)
      } else if (symbol.flags.is(Flags.Enum) && symbol.isClassDef)
        '{ JsonSchemaShape.enumShape(${ Expr.ofSeq(singletonValues(tpe, symbol)) }) }
      else if (symbol.flags.is(Flags.Sealed))
        '{ JsonSchemaShape.sortedEnumShape(${ Expr.ofSeq(singletonValues(tpe, symbol)) }) }
      else
        unsupported(tpe, "")
    }

    // a value class as its one parameter's type - the way Json.valueFormat writes it
    def valueClassShape(
      tpe: TypeRepr,
      symbol: Symbol,
      path: List[Symbol]
    ): Expr[JsonSchemaShape] = {
      val field = symbol.primaryConstructor.paramSymss.flatten
        .filter(_.isTerm)
        .headOption
        .map(param => symbol.fieldMember(param.name))
        .filter(_.exists)
        .getOrElse(unsupported(tpe, "a value class without a parameter"))
      shapeOf(tpe.memberType(field), path)
    }

    def objectShape(
      tpe: TypeRepr,
      symbol: Symbol,
      path: List[Symbol]
    ): Expr[JsonSchemaShape] = {
      val constructorParams = symbol.primaryConstructor.paramSymss.flatten.filter(_.isTerm)

      val fields = symbol.caseFields.map { field =>
        val annotations =
          constructorParams.find(_.name == field.name).toList.flatMap(_.annotations) ++
            field.annotations
        val (minimum, maximum) = rangeOf(annotations)

        '{
          FieldShape(
            ${ Expr(field.name) },
            ${ shapeOf(tpe.memberType(field), path) },
            ${ descriptionOf(annotations) },
            $minimum,
            $maximum
          )
        }
      }

      '{ ObjectShape(${ Expr.ofSeq(fields) }, ${ descriptionOf(symbol.annotations) }) }
    }

    // `Value` of an object extending Enumeration - its values read when the schema is built;
    // one that is not a top-level or nested object (declared in a class, a generic `E#Value`)
    // stays a plain string, as the Scala 2 derivation does
    def enumerationShape(tpe: TypeRepr): Expr[JsonSchemaShape] =
      tpe match {
        case TypeRef(prefix: TermRef, _) if isStaticObject(prefix.termSymbol) =>
          val enumeration = Ref(prefix.termSymbol).asExprOf[Enumeration]
          '{ EnumShape(JsonSchemaShape.enumerationValues($enumeration)) }
        case _ =>
          '{ StringShape }
      }

    def isStaticObject(symbol: Symbol): Boolean =
      symbol.isPackageDef ||
        (symbol.flags.is(Flags.Module) && isStaticObject(symbol.owner))

    // the singleton cases of an enum / the case objects of a sealed hierarchy (nested sealed
    // types flattened), by `toString` when the schema is built, each with its description
    def singletonValues(
      tpe: TypeRepr,
      symbol: Symbol
    ): List[Expr[(String, Option[String])]] =
      symbol.children.flatMap { child =>
        // a case object's annotation sits on its class or its value, an enum case's on the case
        def described(value: Expr[String]) = {
          val annotations =
            child.annotations ++
              (if (child.isTerm && child.flags.is(Flags.Module)) child.moduleClass.annotations
               else if (!child.isTerm) child.companionModule.annotations
               else Nil)
          List('{ ($value, ${ descriptionOf(annotations) }) })
        }

        // String.valueOf, not `.toString` - see the Java enum above (a toString declared
        // without parentheses fails as `toString()`)
        if (child.isTerm)
          described('{ String.valueOf(${ Ref(child).asExprOf[Any] }) })
        else if (child.flags.is(Flags.Module))
          described('{ String.valueOf(${ Ref(child.companionModule).asExprOf[Any] }) })
        else if (child.flags.is(Flags.Sealed))
          singletonValues(tpe, child)
        else
          unsupported(
            tpe,
            s"a sealed hierarchy or enum with the class ${child.name} - only case objects and singleton enum cases make a string enum"
          )
      }

    shapeOf(TypeRepr.of[T], Nil)
  }
}
