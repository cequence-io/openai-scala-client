package io.cequence.openaiscala.service

import net.codingwell.scalaguice.ScalaModule

class ServiceModule extends ScalaModule {

  override def configure = {
    bind[OpenAIService].toProvider(classOf[OpenAIServiceProvider]).asEagerSingleton
  }
}