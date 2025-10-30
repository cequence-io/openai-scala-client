package io.cequence.openaiscala.domain.graders

import io.cequence.wsclient.domain.EnumValue

/**
 * String check operation type.
 */
sealed trait StringCheckOperation extends EnumValue

object StringCheckOperation {
  case object eq extends StringCheckOperation
  case object ne extends StringCheckOperation
  case object like extends StringCheckOperation
  case object ilike extends StringCheckOperation

  def values = Seq(eq, ne, like, ilike)
}

/**
 * A grader that checks if the input text matches the reference text.
 *
 * @param input
 *   The input text. This may include template strings.
 * @param name
 *   The name of the grader.
 * @param operation
 *   The string check operation to perform. One of eq, ne, like, or ilike.
 * @param reference
 *   The reference text. This may include template strings.
 */
case class StringGrader(
  input: String,
  name: String,
  operation: StringCheckOperation,
  reference: String
) extends Grader {
  override val `type`: String = "string_check"
}
