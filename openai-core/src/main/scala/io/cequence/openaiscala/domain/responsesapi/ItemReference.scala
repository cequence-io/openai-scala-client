package io.cequence.openaiscala.domain.responsesapi

/**
 * An internal identifier for an item to reference.
 *
 * @param id
 */
final case class ItemReference(
  id: String
) extends Input {
  val `type`: String = "item_reference"
}
