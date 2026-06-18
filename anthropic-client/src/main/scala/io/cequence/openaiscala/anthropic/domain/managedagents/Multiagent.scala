package io.cequence.openaiscala.anthropic.domain.managedagents

/**
 * A member of a coordinator's roster: another agent (optionally version-pinned) or a recursive
 * self-reference.
 */
sealed trait MultiagentMember

object MultiagentMember {

  /** Reference to another agent. On the wire: `{type:"agent", id, version?}`. */
  final case class AgentRef(
    id: String,
    version: Option[Int] = None
  ) extends MultiagentMember

  /** Recursive self-reference. On the wire: `{type:"self"}`. */
  case object SelfRef extends MultiagentMember
}

/**
 * Multi-agent coordinator configuration: the roster of agents this agent may delegate to.
 *
 * @param agents
 *   Roster members (1-20).
 */
final case class Multiagent(
  agents: Seq[MultiagentMember]
) {
  val `type`: String = "coordinator"
}
