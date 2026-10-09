package io.cequence.openaiscala.service

/**
 * Scala 3: a [[JsonSchemaOf]] for any type the macro can derive a schema for - an unsupported
 * type is a compile error where the instance is needed.
 */
trait JsonSchemaOfDerivation {

  inline given derived[T]: JsonSchemaOf[T] =
    JsonSchemaOf.instance(JsonSchemaReflectionHelper.jsonSchemaFor[T]())
}
