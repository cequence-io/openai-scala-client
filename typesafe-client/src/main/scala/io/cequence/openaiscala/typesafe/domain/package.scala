package io.cequence.openaiscala.typesafe

package object domain {

  /** The name of [[DecisionModelId]] until 1.5.0 - the same constants, the same object. */
  @deprecated("Renamed to DecisionModelId, which covers every decision host", "1.5.0")
  val TypeSafeModelId: DecisionModelId.type = DecisionModelId
}
