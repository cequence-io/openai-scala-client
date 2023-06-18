package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Guice, Injector}
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

trait GuiceContainer {

  protected lazy val injector: Injector = Guice.createInjector(modules: _*)
  protected lazy val config: Config = instance[Config]

  protected def modules: Seq[AbstractModule]

  protected def result[T](future: Future[T]): T =
    Await.result(future, 100.minutes)

  protected def terminate: Unit = {
    val system = instance[ActorSystem]
    system.terminate
    Await.result(system.whenTerminated, 1.day)
  }

  protected def instance[T: ClassTag]: T = injector.getInstance(
    implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
  )
}
