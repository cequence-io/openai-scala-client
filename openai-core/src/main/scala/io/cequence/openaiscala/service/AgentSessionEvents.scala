package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.cequence.openaiscala.domain.agents.{AgentSessionEvent, AgentSessionItem}
import io.cequence.openaiscala.domain.agents.AgentSessionEvent._

/**
 * Helpers for Agents API session event streams.
 */
object AgentSessionEvents {

  /**
   * Ends a session event stream when the session settles after a turn: `agent.session.idle` or
   * `agent.session.failed` (inclusive) - once a turn has finished (completed, failed or
   * cancelled), so the `idle` of a session created without input, before its first turn, does
   * not end it. With `stopOnRequiresAction` also at `agent.session.requires_action` (the
   * session waits for a client function result) - note that the items of the same model output
   * may follow that event (live 2026-09-30).
   *
   * Needed for `streamAgentSessionEvents`, a subscription the server holds open;
   * `createAgentSessionStreamed` ends by itself once the session idles.
   */
  def untilSettled(
    stopOnRequiresAction: Boolean = false
  ): Flow[AgentSessionEvent, AgentSessionEvent, NotUsed] =
    Flow[AgentSessionEvent].statefulMapConcat { () =>
      var turnFinished = false
      var done = false

      (event: AgentSessionEvent) =>
        if (done) Nil
        else {
          event match {
            case turn: TurnUpdated if turn.isFinished => turnFinished = true
            case session: SessionUpdated
                if (session.isIdle && turnFinished) || session.isFailed ||
                  (session.requiresAction && stopOnRequiresAction) =>
              done = true
            case _ =>
          }
          List((event, done))
        }
    }.takeWhile({ case (_, done) => !done }, inclusive = true).map(_._1)

  /** The text of the final-answer messages of a sequence of events (done items). */
  def finalAnswer(events: Seq[AgentSessionEvent]): String =
    events.collect {
      case ItemDone(_, _, _, _, message: AgentSessionItem.Message) if message.isFinalAnswer =>
        message.text
    }.mkString("\n")
}
