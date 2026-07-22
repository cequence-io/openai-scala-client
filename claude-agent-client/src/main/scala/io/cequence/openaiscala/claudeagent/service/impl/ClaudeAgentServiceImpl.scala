package io.cequence.openaiscala.claudeagent.service.impl

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import io.cequence.openaiscala.claudeagent.JsonFormats
import io.cequence.openaiscala.claudeagent.domain.{
  ClaudeAgentEvent,
  ClaudeAgentProcessExit,
  InterruptResult,
  PermissionDecision
}
import io.cequence.openaiscala.claudeagent.service.ClaudeAgentService
import play.api.libs.json.{JsObject, JsValue, Json}

import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Default [[ClaudeAgentService]] implementation, wrapping a [[ClaudeAgentProcessTransport]].
 *
 * Subscribes to `transport.rawEvents` exactly once (in the constructor) and re-broadcasts the
 * parsed [[ClaudeAgentEvent]]s via its own `BroadcastHub` - this way side effects of
 * processing a raw line (completing a pending control-request promise, updating `sessionId`)
 * happen exactly once no matter how many times callers materialize `events`.
 */
private[claudeagent] class ClaudeAgentServiceImpl(
  transport: ClaudeAgentProcessTransport
)(
  implicit ec: ExecutionContext,
  materializer: Materializer
) extends ClaudeAgentService {

  private val controlResponseTimeout = 1.minutes
  private val readyTimeout = 30.seconds

  private val pendingControlRequests = TrieMap.empty[String, Promise[JsValue]]
  private val currentSessionId = new AtomicReference[Option[String]](None)
  private val closed = new AtomicBoolean(false)
  private val pendingLock = new Object
  private val readyPromise = Promise[ClaudeAgentEvent.SystemInit]()

  private val readyFuture: Future[ClaudeAgentEvent.SystemInit] =
    Future.firstCompletedOf(
      Seq(
        readyPromise.future,
        akka.pattern.after(readyTimeout, materializer.system.scheduler) {
          Future.failed(
            new ClaudeAgentProcessException(
              s"Claude process did not emit system/init within $readyTimeout"
            )
          )
        }
      )
    )

  private val eventsSource: Source[ClaudeAgentEvent, NotUsed] =
    transport.rawEvents.collect { case obj: JsObject => obj }
      .mapConcat(handleRawEvent)
      .toMat(BroadcastHub.sink[ClaudeAgentEvent](bufferSize = 256))(Keep.right)
      .run()

  // Keep the process stream draining and lifecycle side effects active even before a caller
  // attaches its own event subscriber. Public subscribers receive `ready` as a replayed init.
  private val eventDrain = eventsSource.runWith(Sink.ignore)

  eventDrain.failed.foreach(readyPromise.tryFailure)
  transport.completion.foreach { exit =>
    readyPromise.tryFailure(
      new ClaudeAgentProcessException(
        s"Claude process exited before system/init (code ${exit.exitCode.getOrElse("unknown")})"
      )
    )
  }

  override def completion: Future[ClaudeAgentProcessExit] = transport.completion

  override def ready: Future[ClaudeAgentEvent.SystemInit] = readyFuture

  override def events: Source[ClaudeAgentEvent, NotUsed] =
    // `eventsSource` is a hot BroadcastHub. Merge it with the replayed init rather than
    // concatenating after `ready`: concat would not subscribe to the hub until init completed
    // and could lose events emitted in that interval. The stateful stage preserves the public
    // ordering contract by buffering any live event that arrives before the replayed init.
    Source
      .future(ready)
      .map[Either[ClaudeAgentEvent.SystemInit, ClaudeAgentEvent]](Left(_))
      .merge(
        eventsSource.collect {
          case event if !event.isInstanceOf[ClaudeAgentEvent.SystemInit] => Right(event)
        }
      )
      .statefulMapConcat { () =>
        var initEmitted = false
        var bufferedEvents = Vector.empty[ClaudeAgentEvent]

        {
          case Left(init) =>
            initEmitted = true
            val events = init +: bufferedEvents
            bufferedEvents = Vector.empty
            events.toList

          case Right(event) if initEmitted =>
            event :: Nil

          case Right(event) =>
            bufferedEvents :+= event
            Nil
        }
      }

  override def sessionId: Option[String] = currentSessionId.get()

  override def send(text: String): Future[Unit] =
    writeWhenReady(JsonFormats.userMessageJson(text, sessionId.getOrElse("")))

  override def sendToolResult(
    toolUseId: String,
    content: String,
    isError: Boolean
  ): Future[Unit] =
    writeWhenReady(
      JsonFormats.toolResultMessageJson(toolUseId, content, isError, sessionId.getOrElse(""))
    )

  override def interrupt(): Future[InterruptResult] = {
    val requestId = UUID.randomUUID().toString

    sendControlRequestAndAwait(
      requestId,
      JsonFormats.controlRequestJson(requestId, "interrupt")
    ).map(json => InterruptResult((json \ "still_queued").asOpt[Seq[String]].getOrElse(Nil)))
  }

  override def respondToolPermission(
    requestId: String,
    decision: PermissionDecision
  ): Future[Unit] =
    // Answers an already-open request initiated BY the CLI - no correlation/timeout needed on
    // our side, we're not waiting on anything.
    transport
      .writeLine(
        JsonFormats.controlResponseJson(
          requestId,
          response = Some(JsonFormats.permissionResultJson(decision))
        )
      )
      .map(_ => ())

  override def sendControlRequest(
    subtype: String,
    payload: JsObject
  ): Future[JsValue] = {
    val requestId = UUID.randomUUID().toString

    sendControlRequestAndAwait(
      requestId,
      JsonFormats.controlRequestJson(requestId, subtype, payload)
    )
  }

  override def close(): Unit = {
    val pendingToFail = pendingLock.synchronized {
      if (closed.compareAndSet(false, true)) {
        val registered = pendingControlRequests.values.toSeq
        pendingControlRequests.clear()
        registered
      } else
        Nil
    }

    val closedException = new ClaudeAgentProcessException("Claude agent session is closed")
    readyPromise.tryFailure(closedException)
    pendingToFail.foreach(_.tryFailure(closedException))
    transport.shutdown()
    ()
  }

  // -- event pipeline --------------------------------------------------------------------

  private def handleRawEvent(json: JsObject): List[ClaudeAgentEvent] =
    (json \ "type").asOpt[String] match {
      case Some("control_response") =>
        // Answers to a control_request WE sent - complete the matching pending promise (if
        // any); never surfaced as a public event.
        completePendingControlResponse(json)
        Nil

      case _ =>
        val event = JsonFormats.parseEvent(json)
        trackSessionId(event)
        List(event)
    }

  private def completePendingControlResponse(json: JsObject): Unit = {
    val requestIdOpt = (json \ "response" \ "request_id").asOpt[String]
    val subtypeOpt = (json \ "response" \ "subtype").asOpt[String]

    requestIdOpt.foreach { requestId =>
      pendingControlRequests.remove(requestId).foreach { promise =>
        if (subtypeOpt.contains("error"))
          promise.tryFailure(
            new ClaudeAgentControlRequestException(
              (json \ "response" \ "error").asOpt[String].getOrElse("unknown error")
            )
          )
        else
          promise.trySuccess(
            (json \ "response" \ "response").asOpt[JsValue].getOrElse(Json.obj())
          )
      }
    }
  }

  private def trackSessionId(event: ClaudeAgentEvent): Unit = {
    val sessionIdOpt = event match {
      case e: ClaudeAgentEvent.SystemInit    => Some(e.sessionId)
      case e: ClaudeAgentEvent.ResultSuccess => Some(e.sessionId)
      case e: ClaudeAgentEvent.ResultError   => Some(e.sessionId)
      case _                                 => None
    }

    sessionIdOpt.filter(_.nonEmpty).foreach(id => currentSessionId.set(Some(id)))
    event match {
      case init: ClaudeAgentEvent.SystemInit => readyPromise.trySuccess(init)
      case _                                 => ()
    }
  }

  // -- control-request/response correlation -----------------------------------------------

  /**
   * Registers a pending promise for `requestId` BEFORE writing (so a response that arrives
   * fast can never race ahead of the registration), sends `wireJson`, then races the matching
   * `control_response` against the configured timeout. The pending-map entry is removed in
   * every case (success, error response, write failure, or timeout) so it never leaks.
   */
  private def sendControlRequestAndAwait(
    requestId: String,
    wireJson: JsValue
  ): Future[JsValue] = ready.flatMap { _ =>
    val promise = Promise[JsValue]()
    val registered = pendingLock.synchronized {
      if (closed.get())
        false
      else {
        pendingControlRequests.put(requestId, promise)
        true
      }
    }

    if (!registered)
      Future.failed(new ClaudeAgentProcessException("Claude agent session is closed"))
    else {
      val timeoutFuture = akka.pattern.after(
        controlResponseTimeout,
        materializer.system.scheduler
      ) {
        Future.failed[JsValue](
          new TimeoutException(
            s"No control_response for request $requestId within $controlResponseTimeout"
          )
        )
      }

      transport.writeLine(wireJson).failed.foreach(promise.tryFailure)

      Future.firstCompletedOf(List(promise.future, timeoutFuture)).andThen { case _ =>
        pendingControlRequests.remove(requestId)
      }
    }
  }

  private def writeWhenReady(json: => JsValue): Future[Unit] =
    if (closed.get())
      Future.failed(new ClaudeAgentProcessException("Claude agent session is closed"))
    else
      ready.flatMap { _ =>
        if (closed.get())
          Future.failed(new ClaudeAgentProcessException("Claude agent session is closed"))
        else
          transport.writeLine(json)
      }
}

/**
 * Thrown into a failed `Future` when a `control_response` comes back with `subtype:"error"`.
 */
class ClaudeAgentControlRequestException(message: String) extends RuntimeException(message)
