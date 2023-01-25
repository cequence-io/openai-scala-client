package io.cequence.openaiscala

import com.typesafe.config.Config

object ConfigImplicits {
  implicit class ConfigExt(config: Config) {
    def optionalString(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getString(configPath)) else None

    def optionalInt(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getInt(configPath)) else None

    def optionalBoolean(configPath: String) =
      if (config.hasPath(configPath)) Some(config.getBoolean(configPath)) else None
  }
}
