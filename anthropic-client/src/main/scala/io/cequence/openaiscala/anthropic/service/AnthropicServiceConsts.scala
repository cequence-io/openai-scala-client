package io.cequence.openaiscala.anthropic.service

/**
 * Constants of [[AnthropicService]], mostly defaults
 */
trait AnthropicServiceConsts {

  protected val defaultCoreUrl = "https://api.anthropic.com/v1/"

  protected val defaultRequestTimeout = 120 * 1000 // two minutes

  protected val defaultReadoutTimeout = 120 * 1000 // two minutes

  protected val configPrefix = "anthropic-scala-client"

  protected val configFileName = "anthropic-scala-client.conf"

  // TODO: move to consts? determine the default value
  val defaultMaxTokens = 2048


}
