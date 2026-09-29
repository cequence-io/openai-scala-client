package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue

/**
 * Whether a managed-agent tool runs automatically or pauses for user confirmation:
 * `always_allow`, `always_ask`, or `auto` (the platform evaluates each call - it may allow,
 * deny, or pause it for confirmation like `always_ask`).
 *
 * Serialized as an object with a `type` discriminator, e.g. `{"type": "always_allow"}`.
 */
sealed trait PermissionPolicy extends EnumValue

object PermissionPolicy {
  case object always_allow extends PermissionPolicy
  case object always_ask extends PermissionPolicy
  case object auto extends PermissionPolicy

  def values: Seq[PermissionPolicy] = Seq(always_allow, always_ask, auto)
}
