package io.cequence.openaiscala.service

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Trait providing OpenAI configuration loading capabilities.
 *
 * Configuration can be customized via the OPENAI_SCALA_CLIENT_CONFIG_FILE environment
 * variable. If not set, defaults to "openai-scala-client.conf".
 */
trait HasOpenAIConfig {

  /**
   * The configuration prefix used for all OpenAI client settings.
   */
  protected val configPrefix = "openai-scala-client"

  /**
   * The configuration file name. Can be overridden via the OPENAI_SCALA_CLIENT_CONFIG_FILE
   * environment variable.
   */
  protected val configFileName: String =
    sys.env.getOrElse("OPENAI_SCALA_CLIENT_CONFIG_FILE", "openai-scala-client.conf")

  /**
   * The loaded OpenAI client configuration from the specified config file.
   */
  protected lazy val clientConfig: Config =
    ConfigFactory.load(configFileName)
}
