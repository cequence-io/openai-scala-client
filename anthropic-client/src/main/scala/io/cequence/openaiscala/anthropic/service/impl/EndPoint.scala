package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.domain.NamedEnumValue

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object messages extends EndPoint
  case object skills extends EndPoint
  case object files extends EndPoint
  // managed agents
  case object agents extends EndPoint
  case object environments extends EndPoint
  case object sessions extends EndPoint
  case object deployments extends EndPoint
}

sealed abstract class Param(value: String = "") extends NamedEnumValue(value)

object Param {

  case object model extends Param
  case object messages extends Param
  case object system extends Param
  case object max_tokens extends Param
  case object metadata extends Param
  case object stop_sequences extends Param
  case object stream extends Param
  case object temperature extends Param
  case object top_p extends Param
  case object top_k extends Param
  case object thinking extends Param
  case object container extends Param
  case object tools extends Param
  case object tool_choice extends Param
  case object mcp_servers extends Param
  case object output_format extends Param
  case object output_config extends Param
  case object speed extends Param
  // bedrock
  case object anthropic_version extends Param
  case object anthropic_beta extends Param
  // skills
  case object page extends Param
  case object limit extends Param
  case object source extends Param
  case object display_title extends Param
  case object files extends Param("files[]")
  // files
  case object file extends Param
  // files pagination
  case object before_id extends Param
  case object after_id extends Param
  // managed agents
  case object name extends Param
  case object description extends Param
  case object version extends Param
  case object skills extends Param
  case object multiagent extends Param
  case object include_archived extends Param
  case object created_at_gte extends Param("created_at[gte]")
  case object created_at_lte extends Param("created_at[lte]")
  case object config extends Param
  case object scope extends Param
  // environment work (self-hosted)
  case object block_ms extends Param
  case object reclaim_older_than_ms extends Param
  case object desired_ttl_seconds extends Param
  case object expected_last_heartbeat extends Param
  // sessions
  case object agent extends Param
  case object environment_id extends Param
  case object title extends Param
  case object resources extends Param
  case object vault_ids extends Param
  case object events extends Param
  case object agent_id extends Param
  case object agent_version extends Param
  case object deployment_id extends Param
  case object memory_store_id extends Param
  case object order extends Param
  case object statuses extends Param
  // deployments
  case object initial_events extends Param
  case object schedule extends Param
  case object status extends Param
}
