package io.cequence.openaiscala.service

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.service.ConfigModule.ConfigProvider
import io.cequence.wsclient.ClassLoaders
import net.codingwell.scalaguice.ScalaModule

object ConfigModule {
  // the context class loader when it sees this library, else the library's own (see HasOpenAIConfig)
  class ConfigProvider extends Provider[Config] {
    override def get: Config =
      ConfigFactory.load(ClassLoaders.contextOr(classOf[ConfigModule]))
  }
}

class ConfigModule extends AbstractModule with ScalaModule {

  override def configure: Unit = {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
  }
}
