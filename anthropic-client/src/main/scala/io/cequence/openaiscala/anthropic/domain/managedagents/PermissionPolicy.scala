package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/**
 * Whether a managed-agent tool runs automatically or pauses for user confirmation.
 *
 * Serialized as an object with a `type` discriminator, e.g. `{"type": "always_allow"}`.
 */
sealed trait PermissionPolicy extends EnumValue

object PermissionPolicy {
  case object always_allow extends PermissionPolicy
  case object always_ask extends PermissionPolicy

  def values: Seq[PermissionPolicy] = Seq(always_allow, always_ask)
}
