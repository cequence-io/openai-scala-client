package io.cequence.openaiscala.examples.agents

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.agents.AgentSessionEvent._
import io.cequence.openaiscala.domain.agents._
import io.cequence.openaiscala.domain.responsesapi.MultiAgentConfig
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.AgentSessionEvents
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import play.api.libs.json.Json

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for the OpenAI Agents API (beta): a streamed session turn, a client function
 * call answered mid-stream, a follow-up turn over the event subscription (subscribe first,
 * then send), the session's items / turns, a reusable agent, a multi-agent session and (with
 * `hosted` as an argument) an OpenAI-hosted sandbox running a command. Every session is
 * deleted afterwards. Requires `OPENAI_SCALA_CLIENT_API_KEY`; the exit code is 1 if any
 * section failed or the run did not complete.
 */
object OpenAIAgentsApiSmokeTest {

  private val model = ModelId.gpt_6_luna

  private val weatherTool = AgentTool.Function(
    name = "get_weather",
    description = "Get the current weather in a city",
    parameters = Json.obj(
      "type" -> "object",
      "properties" -> Json.obj("city" -> Json.obj("type" -> "string")),
      "required" -> Json.arr("city")
    )
  )

  private def describe(e: Throwable): String =
    s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("(no message)").take(400)}"

  private def textOf(events: Seq[AgentSessionEvent]): String =
    events.collect { case OutputTextDelta(_, _, _, _, _, _, delta) => delta }.mkString

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val service = OpenAIServiceFactory.withStreaming()
    val failures = new AtomicInteger(0)

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${describe(e)}")
          Success(())
      }
    }

    // deletes a session even when the turn is still paused (cancel first - a paused session
    // cannot be deleted)
    def cleanup(sessionId: String): Future[Unit] =
      service
        .deleteAgentSession(sessionId)
        .recoverWith { case _ =>
          service
            .sendAgentSessionEvents(sessionId, Seq(AgentSessionInput.Cancel))
            .flatMap(_ => service.deleteAgentSession(sessionId))
        }
        .map(_ => ())
        .recover { case e => println(s"  (cleanup of $sessionId failed: ${describe(e)})") }

    def sessionIdOf(events: Seq[AgentSessionEvent]): String =
      events.collectFirst { case s: SessionUpdated => s.session.id }
        .getOrElse(throw new IllegalStateException("no session event"))

    val inline = CreateAgentSessionSettings(
      agent = Some(AgentConfig(model = Some(model), instructions = Some("Be brief.")))
    )

    val all = for {
      _ <- section("createAgentSessionStreamed - one turn, the stream ends at idle") {
        service
          .createAgentSessionStreamed(inline, AgentInput.Text("Say hi in two words."))
          .runWith(Sink.seq)
          .flatMap { events =>
            val sessionId = sessionIdOf(events)
            val types = events.map(_.eventType).distinct
            val finalAnswer = AgentSessionEvents.finalAnswer(events)
            val last = events.last
            cleanup(sessionId).map { _ =>
              if (!last.isInstanceOf[SessionUpdated] || last.eventType != SessionIdle)
                throw new IllegalStateException(s"did not end at idle: ${last.eventType}")
              if (finalAnswer.isEmpty) throw new IllegalStateException(s"no answer: $types")
              s"${events.size} events, deltas='${textOf(events)}' final='$finalAnswer' others=${events.collect { case o: Other =>
                  o.eventType
                }.distinct}"
            }
          }
      }

      _ <- section("client function tool - answered mid-stream (requires_action)") {
        val settings = inline.copy(
          agent = inline.agent.map(_.copy(tools = Some(Seq(weatherTool))))
        )
        var sessionId = ""
        service
          .createAgentSessionStreamed(
            settings,
            AgentInput.Text("What's the weather in Paris right now?")
          )
          .mapAsync(1) {
            case event: SessionUpdated if event.requiresAction =>
              sessionId = event.session.id
              val results = event.session.pendingFunctionCalls.map(call =>
                AgentSessionInput.toolResult(call, "Sunny, 22 C")
              )
              service
                .sendAgentSessionEvents(event.session.id, results)
                .map(_ => event: AgentSessionEvent)
            case event =>
              Future.successful(event)
          }
          .runWith(Sink.seq)
          .flatMap { events =>
            val id = sessionIdOf(events)
            val calls = events.collect {
              case ItemDone(_, _, _, _, call: AgentSessionItem.FunctionCall) => call
            }
            val outputs = events.collect {
              case ItemAdded(_, _, _, _, out: AgentSessionItem.FunctionCallOutput) => out
            }
            val answer = AgentSessionEvents.finalAnswer(events)
            cleanup(id).map { _ =>
              if (sessionId.isEmpty)
                throw new IllegalStateException("never required an action")
              if (!answer.contains("22")) throw new IllegalStateException(s"answer: '$answer'")
              s"calls=${calls.map(c => s"${c.name}(${c.arguments})")} outputs=${outputs.size} final='$answer'"
            }
          }
      }

      _ <- section("follow-up turn - subscribe to the events, then send") {
        for {
          first <- service
            .createAgentSessionStreamed(
              inline,
              AgentInput.Text("Remember the number 7. Say ok.")
            )
            .runWith(Sink.seq)
          sessionId = sessionIdOf(first)
          subscription = service
            .streamAgentSessionEvents(sessionId)
            .via(AgentSessionEvents.untilSettled())
            .runWith(Sink.seq)
          _ = Thread.sleep(2000) // let the subscription connect
          _ <- service.sendAgentSessionEvents(
            sessionId,
            Seq(AgentSessionInput.text("Which number? Digits only."))
          )
          second <- subscription
          session <- service.getAgentSession(sessionId)
          items <- service.listAgentSessionItems(sessionId)
          turns <- service.listAgentSessionTurns(sessionId)
          turn <- service.getAgentSessionTurn(sessionId, turns.data.head.id)
          _ <- cleanup(sessionId)
        } yield {
          val answer = AgentSessionEvents.finalAnswer(second)
          if (!answer.contains("7")) throw new IllegalStateException(s"answer: '$answer'")
          s"final='$answer' deltas='${textOf(second)}' session=${session.status} items=${items.data
              .map(_.itemType)} turns=${turns.data.map(_.status)} turn=${turn.status}"
        }
      }

      _ <- section("reusable agent - create / get / update / list, a session by agent_id") {
        for {
          agent <- service.createAgent(
            CreateAgentSettings(
              model = model,
              name = Some("scala-client-smoke-test"),
              instructions = Some("Be brief."),
              metadata = Map("created_by" -> "openai-scala-client")
            )
          )
          fetched <- service.getAgent(agent.id)
          updated <- service.updateAgent(
            agent.id,
            UpdateAgentSettings(instructions = Some("Be very brief."))
          )
          listed <- service.listAgents(limit = Some(5))
          events <- service
            .createAgentSessionStreamed(
              CreateAgentSessionSettings(agentId = Some(agent.id)),
              AgentInput.Text("Say hi.")
            )
            .runWith(Sink.seq)
          _ <- cleanup(sessionIdOf(events))
          deleted <- service.deleteAgent(agent.id)
        } yield s"agent=${fetched.id} model=${fetched.model} instructions='${updated.instructions
            .getOrElse("")}' listed=${listed.data.size} answer='${AgentSessionEvents
            .finalAnswer(events)}' deleted=${deleted.deleted}"
      }

      _ <- section(s"multi-agent session (${ModelId.gpt_6_1_sol})") {
        service
          .createAgentSessionStreamed(
            CreateAgentSessionSettings(
              agent = Some(
                AgentConfig(
                  model = Some(ModelId.gpt_6_1_sol),
                  instructions = Some("Be brief."),
                  multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
                )
              )
            ),
            AgentInput.Text(
              "Use two subagents in parallel: one lists three fruits, the other three " +
                "vegetables (one short line each). Then reply with one combined line."
            )
          )
          .runWith(Sink.seq)
          .flatMap { events =>
            val sessionId = sessionIdOf(events)
            val delegation = events.collect {
              case ItemDone(_, _, _, _, other: AgentSessionItem.Other) => other.itemType
            }
            service.listAgentSessionSubagents(sessionId).flatMap { subagents =>
              cleanup(sessionId).map { _ =>
                if (subagents.data.isEmpty) throw new IllegalStateException("no subagents")
                s"delegation=${delegation.distinct} subagents=${subagents.data.map(s =>
                    s"${s.name.getOrElse("?")}:${s.status}"
                  )} final='${AgentSessionEvents.finalAnswer(events)}'"
              }
            }
          }
      }

      _ <- section("OpenAI-hosted environment - a command") {
        if (!args.contains("hosted"))
          Future.successful("skipped - pass `hosted` to run it (provisions a sandbox)")
        else
          service
            .createAgentSessionStreamed(
              inline.copy(environment = AgentEnvironment.OpenAIHosted()),
              AgentInput.Text(
                "Run the shell command `echo hello-agents && uname -s` and report its output."
              )
            )
            .runWith(Sink.seq)
            .flatMap { events =>
              val sessionId = sessionIdOf(events)
              val commands = events.collect {
                case ItemDone(_, _, _, _, cmd: AgentSessionItem.CommandExecution) => cmd
              }
              service.listAgentSessionArtifacts(sessionId).flatMap { artifacts =>
                cleanup(sessionId).map { _ =>
                  if (commands.isEmpty) throw new IllegalStateException("no command ran")
                  s"commands=${commands.map(c => s"${c.command} -> ${c.exitCode}")} artifacts=${artifacts.data.size} final='${AgentSessionEvents
                      .finalAnswer(events)}'"
                }
              }
            }
      }
    } yield ()

    Try(Await.result(all, 20.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: ${describe(e)}")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
