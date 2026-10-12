package io.cequence.openaiscala.service

import scala.reflect.runtime.universe.{typeOf, TypeTag}

/**
 * Scala 2: a [[JsonSchemaOf]] for any type, derived by runtime reflection when first used and
 * named after the type.
 */
trait JsonSchemaOfDerivation {

  implicit def derived[T: TypeTag]: JsonSchemaOf[T] =
    JsonSchemaOf.instance(
      typeOf[T].dealias.typeSymbol.name.decodedName.toString,
      JsonSchemaReflectionHelper.jsonSchemaFor[T]()
    )
}
