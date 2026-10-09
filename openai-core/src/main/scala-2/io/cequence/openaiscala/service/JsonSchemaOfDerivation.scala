package io.cequence.openaiscala.service

import scala.reflect.runtime.universe.TypeTag

/** Scala 2: a [[JsonSchemaOf]] for any type, derived by runtime reflection when first used. */
trait JsonSchemaOfDerivation {

  implicit def derived[T: TypeTag]: JsonSchemaOf[T] =
    JsonSchemaOf.instance(JsonSchemaReflectionHelper.jsonSchemaFor[T]())
}
