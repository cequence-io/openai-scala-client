package io.cequence.openaiscala.service

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule

class ServiceModule extends AbstractModule with ScalaModule {

  override def configure: Unit = {
    bind[OpenAIService].toProvider(classOf[OpenAIServiceProvider]).asEagerSingleton
  }
}
