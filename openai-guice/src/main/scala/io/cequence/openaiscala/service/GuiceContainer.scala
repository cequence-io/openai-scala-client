package io.cequence.openaiscala.service

import com.google.inject.{Guice, Injector, Module}
import com.typesafe.config.Config
import scala.concurrent.duration._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

trait GuiceContainer {
  protected def modules: Seq[Module]

  protected lazy val injector: Injector = Guice.createInjector(modules: _*)

  protected lazy val config: Config = instance[Config]

  protected def instance[T: ClassTag]: T = injector.getInstance(
    implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
  )

  protected def result[T](future: Future[T]): T =
    Await.result(future, 100.minutes)

  /**
   * Shuts the container's thread pool down (the one bound by `ExecutionContextModule`; an
   * execution context of another kind is left alone). Close the services first.
   */
  protected def terminate(): Unit =
    ExecutionContextModule.shutdown(instance[ExecutionContext])
}
