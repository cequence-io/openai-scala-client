package io.cequence.openaiscala.service

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.service.ConfigModule.ConfigProvider
import net.codingwell.scalaguice.ScalaModule

object ConfigModule {
  class ConfigProvider extends Provider[Config] {
    override def get: Config = ConfigFactory.load()
  }
}

class ConfigModule extends AbstractModule with ScalaModule {

  override def configure: Unit = {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
  }
}
