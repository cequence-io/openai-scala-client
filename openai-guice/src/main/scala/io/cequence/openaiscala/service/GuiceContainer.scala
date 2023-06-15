package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import com.google.inject.{Guice, Injector, Module}
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

trait GuiceContainer {

  protected def modules: Seq[Module]

  protected lazy val injector: Injector = Guice.createInjector(modules :_*)

  protected lazy val config = instance[Config]

  protected def instance[T: Manifest] = injector.instance[T]

  protected def result[T](future: Future[T]) =
    Await.result(future, 100.minutes)

  protected def terminate: Unit = {
    val system = instance[ActorSystem]
    system.terminate
    Await.result(system.whenTerminated, 1.day)
  }
}
