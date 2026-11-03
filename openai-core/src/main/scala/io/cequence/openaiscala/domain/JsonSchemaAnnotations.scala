package io.cequence.openaiscala.domain

import scala.annotation.StaticAnnotation

/**
 * The description of a case class, or of one of its fields, in the JSON schema
 * `JsonSchemaReflectionHelper.jsonSchemaFor` derives - the model reads it as the field's
 * instructions (a decision model through `TypeSafeServiceFactory.asOpenAI` as the question it
 * answers).
 *
 * {{{
 * @JsonSchemaDescription("A product review, triaged")
 * case class Triage(
 *   @JsonSchemaDescription("Does the review report a product defect?") defect: Boolean
 * )
 * }}}
 *
 * The text must be a literal or a constant (Scala 2 reads it by runtime reflection).
 */
final class JsonSchemaDescription(val value: String) extends StaticAnnotation

/**
 * The inclusive range of a numeric field - or of the items of a numeric collection - in the
 * JSON schema `JsonSchemaReflectionHelper.jsonSchemaFor` derives (`minimum` / `maximum`; an
 * integer field gets the whole numbers within). OpenAI honours it in strict mode only and
 * Anthropic's adapter drops it; a decision model turns a range of at most 10 whole numbers
 * into a score question.
 *
 * The bounds must be literals or constants (Scala 2 reads them by runtime reflection).
 */
final class JsonSchemaRange(
  val min: Double,
  val max: Double
) extends StaticAnnotation

/**
 * Leaves a case class field out of the JSON schema `JsonSchemaReflectionHelper.jsonSchemaFor`
 * derives - for a field the model is never asked for (set by your code afterwards). The field
 * must be an `Option` or have a default value, or the derivation refuses it (on Scala 2 when
 * called, on Scala 3 at compile time): the answer is read without it, so `Json.format` reads
 * an `Option` as `None`, and a default needs `Json.using[Json.WithDefaultValues].format`.
 *
 * {{{
 * case class Change(
 *   text: Option[String],
 *   @JsonSchemaIgnore style: Option[String] = None
 * )
 * }}}
 */
final class JsonSchemaIgnore extends StaticAnnotation
