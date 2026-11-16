package io.cequence.openaiscala.service

import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.wsclient.ClassLoaders

/**
 * Trait providing OpenAI configuration loading capabilities.
 *
 * If the application-wide configuration should be used can be expressed by the
 * OPENAI_SCALA_CLIENT_USE_APP_CONFIG_FILE environment variable. If not set, defaults to
 * "openai-scala-client.conf".
 *
 * The configuration is read through the thread's context class loader when that loader sees
 * this library, else through the library's own class loader (ws-client's
 * `ClassLoaders.contextOr`). A pool thread of an in-process sbt `run` carries a loader that
 * sees neither the library nor its config file, and a thread may have none at all.
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
    val classLoader = ClassLoaders.contextOr(classOf[HasOpenAIConfig])
    if (sys.env.get("OPENAI_SCALA_CLIENT_USE_APP_CONFIG_FILE").exists(_.toBoolean))
      ConfigFactory.load(classLoader)
    else
      ConfigFactory.load(classLoader, "openai-scala-client.conf")
  }
}
