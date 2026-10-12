package io.cequence.openaiscala.service

/**
 * Scala 2: the inferred schema of a type with a [[JsonSchemaOf]] - an instance of your own, or
 * the derived one, built by runtime reflection when first needed (a type it does not support
 * infers nothing); an abstract `T` without one infers nothing.
 */
trait InferredJsonSchemaDerivation extends InferredJsonSchemaFallback {

  implicit def inferred[T](
    implicit schemaOf: JsonSchemaOf[T]
  ): InferredJsonSchema[T] =
    InferredJsonSchema.fromSchemaOf(schemaOf)
}

trait InferredJsonSchemaFallback {

  implicit def absent[T]: InferredJsonSchema[T] = InferredJsonSchema.none[T]
}
