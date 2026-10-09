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
