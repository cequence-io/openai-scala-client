package io.cequence.openaiscala.service

import com.google.inject.Provider
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.service.ConfigModule.ConfigProvider
import net.codingwell.scalaguice.ScalaModule

object ConfigModule {
  class ConfigProvider extends Provider[Config] {
    override def get = ConfigFactory.load()
  }
}

class ConfigModule extends ScalaModule {

  override def configure() {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
  }
}