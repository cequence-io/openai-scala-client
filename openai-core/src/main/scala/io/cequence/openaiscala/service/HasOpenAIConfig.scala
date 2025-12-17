package io.cequence.openaiscala.service

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Trait providing OpenAI configuration loading capabilities.
 *
 * If the application-wide configuration should be used can be expressed by the
 * OPENAI_SCALA_CLIENT_USE_APP_CONFIG_FILE environment variable. If not set, defaults to
 * "openai-scala-client.conf".
 */
trait HasOpenAIConfig {

  /**
   * The configuration prefix used for all OpenAI client settings.
   */
  protected val configPrefix = "openai-scala-client"

  /**
   * The loaded OpenAI client configuration from the specified config file.
   */
  protected lazy val clientConfig: Config = {
    if (sys.env.get("OPENAI_SCALA_CLIENT_USE_APP_CONFIG_FILE").exists(_.toBoolean))
      ConfigFactory.load()
    else
      ConfigFactory.load("openai-scala-client.conf")
  }
}
