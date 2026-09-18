package io.cequence.openaiscala.claudeagent

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.claudeagent.domain.{
  ClaudeAgentEvent,
  ClaudeAgentSettings,
  InterruptResult
}
import io.cequence.openaiscala.claudeagent.service.ClaudeAgentServiceFactory
import io.cequence.openaiscala.claudeagent.service.impl.ClaudeAgentProcessException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Promise}

class ClaudeAgentServiceLifecycleSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("claude-agent-lifecycle-spec")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val ec: ExecutionContext = system.dispatcher

  // generous: CI runs these under coverage instrumentation on a cold JVM
  private val timeout: FiniteDuration = 15.seconds

  private val fakeCli: Path = Files.createTempFile("fake-claude-cli", ".sh")

  Files.write(fakeCli, fakeCliScript.getBytes(StandardCharsets.UTF_8))
  fakeCli.toFile.setExecutable(true)

  override def afterAll(): Unit = {
    Files.deleteIfExists(fakeCli)
    Await.result(system.terminate(), 5.seconds)
    ()
  }

  "ClaudeAgentService" should {

    "replay system init through ready and complete a successful turn" in {
      val service = newService("success")

      try {
        val init = Await.result(service.ready, timeout)
        init.model shouldBe "fake-model"

        // `events` is a hot BroadcastHub: a subscriber only sees what is published after it has
        // registered with the hub. Registration is asynchronous, so wait for the replayed init
        // (the first element this subscription emits, which cannot arrive before its stages have
        // started) before sending - otherwise the result can be published before the
        // registration lands and this subscriber never sees the end of the turn (CI flake).
        val subscribed = Promise[Unit]()
        val events = service.events
          .wireTap(_ => subscribed.trySuccess(()))
          .takeWhile(event => !isTurnEnd(event), inclusive = true)
          .runWith(Sink.seq)

        Await.result(subscribed.future, timeout)
        Await.result(service.send("hello"), timeout)
        Await
          .result(events, timeout)
          .collect { case event: ClaudeAgentEvent.ResultSuccess => event }
          .head
          .result shouldBe "ok"
      } finally service.close()
    }

    "surface an unexpected nonzero process exit through events and completion" in {
      val service = newService("crash")

      try {
        val streamFailure = intercept[ClaudeAgentProcessException] {
          Await.result(service.events.runWith(Sink.seq), timeout)
        }

        streamFailure.getMessage should include("code 23")
        Await.result(service.completion, timeout).exitCode shouldBe Some(23)
      } finally service.close()
    }

    "correlate an interrupt control response" in {
      val service = newService("success")

      try {
        Await.result(service.ready, timeout)
        Await.result(service.interrupt(), timeout) shouldBe InterruptResult(Seq("queued-1"))
      } finally service.close()
    }

    "fail an outstanding control request when closed" in {
      val service = newService("no_response")

      try {
        Await.result(service.ready, timeout)
        val pending = service.interrupt()

        Thread.sleep(100)
        service.close()

        intercept[ClaudeAgentProcessException] {
          Await.result(pending, timeout)
        }.getMessage should include("closed")
      } finally service.close()
    }
  }

  "ClaudeAgentSettings" should {

    "allow a system prompt to be extended" in {
      noException shouldBe thrownBy {
        ClaudeAgentSettings(systemPrompt = Some("a"), appendSystemPrompt = Some("b"))
      }
    }

    "reject incompatible flag combinations and invalid values" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        ClaudeAgentSettings(resume = Some("session"), continueSession = true)
      }
      an[IllegalArgumentException] shouldBe thrownBy {
        ClaudeAgentSettings(maxTurns = Some(0))
      }
    }
  }

  private def newService(mode: String) =
    ClaudeAgentServiceFactory.startSession(
      ClaudeAgentSettings(
        executablePath = Some(fakeCli.toString),
        env = Map("FAKE_CLAUDE_MODE" -> mode)
      )
    )

  private def isTurnEnd(event: ClaudeAgentEvent): Boolean = event match {
    case _: ClaudeAgentEvent.ResultSuccess => true
    case _: ClaudeAgentEvent.ResultError   => true
    case _                                 => false
  }

  private lazy val fakeCliScript =
    """#!/bin/sh
      |printf '%s\n' '{"type":"system","subtype":"init","apiKeySource":"none","model":"fake-model","tools":[],"permissionMode":"default","session_id":"session-1","cwd":"/tmp","claude_code_version":"test"}'
      |
      |if [ "$FAKE_CLAUDE_MODE" = "crash" ]; then
      |  printf '%s\n' 'fake CLI crashed' >&2
      |  exit 23
      |fi
      |
      |while IFS= read -r line; do
      |  case "$line" in
      |    *'"subtype":"interrupt"'*)
      |      if [ "$FAKE_CLAUDE_MODE" != "no_response" ]; then
      |        request_id=$(printf '%s' "$line" | sed -n 's/.*"request_id":"\([^"]*\)".*/\1/p')
      |        printf '%s\n' "{\"type\":\"control_response\",\"response\":{\"subtype\":\"success\",\"request_id\":\"$request_id\",\"response\":{\"still_queued\":[\"queued-1\"]}}}"
      |      fi
      |      ;;
      |    *'"type":"user"'*)
      |      printf '%s\n' '{"type":"result","subtype":"success","result":"ok","total_cost_usd":0.0,"num_turns":1,"duration_ms":1,"is_error":false,"session_id":"session-1"}'
      |      ;;
      |  esac
      |done
      |""".stripMargin
}
