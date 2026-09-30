package io.cequence.openaiscala.domain.agents

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.agents.AgentSessionEvent._
import io.cequence.openaiscala.domain.agents.JsonFormats._
import io.cequence.openaiscala.domain.responsesapi.MultiAgentConfig
import io.cequence.openaiscala.domain.settings.{ReasoningEffort, ServiceTier}
import io.cequence.openaiscala.service.AgentSessionEvents
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext

/**
 * The Agents API (beta) JSON against payloads recorded live on 2026-09-30 (`gpt-6-luna` /
 * `gpt-6.1-sol` sessions: a plain turn, a client function call before and after its result, a
 * follow-up over the event subscription, an OpenAI-hosted command, a multi-agent run) and the
 * request bodies the client writes.
 */
class AgentsApiJsonSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("agents-api-json")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def resource(name: String): String = {
    val source = scala.io.Source.fromResource(s"agents/$name")
    try source.mkString
    finally source.close()
  }

  private def events(name: String): Seq[AgentSessionEvent] =
    resource(name)
      .split("\n")
      .collect { case line if line.startsWith("data:") => Json.parse(line.drop(5)) }
      .map(_.as[AgentSessionEvent])
      .toSeq

  private def deltas(events: Seq[AgentSessionEvent]): String =
    events.collect { case d: OutputTextDelta => d.delta }.mkString

  "session events" should {

    "parse a plain turn - created ... deltas ... completed, idle" in {
      val turn = events("basic.sse")

      turn.head shouldBe a[SessionUpdated]
      turn.head.eventType shouldBe SessionCreated
      turn.collect { case t: TurnUpdated => t.eventType } shouldBe
        Seq(TurnCreated, TurnInProgress, TurnCompleted)
      turn.last.asInstanceOf[SessionUpdated].isIdle shouldBe true

      deltas(turn) shouldBe "Hi there!"
      AgentSessionEvents.finalAnswer(turn) shouldBe "Hi there!"
      turn.collect { case ItemAdded(_, _, _, _, m: AgentSessionItem.Message) =>
        m.role
      } shouldBe
        Seq("user", "assistant")
      // the content parts are kept raw
      turn.collect { case o: Other => o.eventType }.distinct shouldBe
        Seq("agent.session.turn.content_part.added", "agent.session.turn.content_part.done")
    }

    "parse a client function call and the required action it pauses on" in {
      val paused = events("tool-1.sse")
      val calls = paused.collect {
        case ItemAdded(_, _, _, _, call: AgentSessionItem.FunctionCall) => call
      }
      calls.map(c => (c.name, c.arguments)) shouldBe Seq(
        ("get_weather", Json.obj("city" -> "Paris"))
      )

      val requiresAction = paused.last.asInstanceOf[SessionUpdated]
      requiresAction.requiresAction shouldBe true
      val pending = requiresAction.session.pendingFunctionCalls
      pending.map(p => (p.name, p.callId, p.arguments)) shouldBe
        Seq(("get_weather", calls.head.callId, Json.obj("city" -> "Paris")))
      pending.head.turnId should startWith("turn_")
    }

    "parse a turn replayed by a subscription - done events, no deltas" in {
      val resumed = events("tool-2.sse")
      deltas(resumed) shouldBe ""
      resumed.collect { case d: OutputTextDone => d.text }.last shouldBe
        "It’s sunny in Paris, 22°C."
      resumed.collect { case ItemAdded(_, _, _, _, out: AgentSessionItem.FunctionCallOutput) =>
        out.callId
      } should have size 1
      AgentSessionEvents.finalAnswer(resumed) shouldBe "It’s sunny in Paris, 22°C."
    }

    "parse a command run in an OpenAI-hosted environment" in {
      val hosted = events("hosted.sse")
      val commands = hosted.collect {
        case ItemDone(_, _, _, _, cmd: AgentSessionItem.CommandExecution) => cmd
      }
      commands.map(c => (c.command, c.exitCode)) shouldBe
        Seq(("/bin/bash -lc 'echo hello-agents && uname -s'", Some(0)))
      hosted.collect { case o: Other => o.eventType }.distinct should contain allOf (
        "agent.session.environment.ready",
        "agent.session.environment.connected"
      )
      AgentSessionEvents.finalAnswer(hosted) should include("hello-agents")
    }

    "keep the multi-agent items and events raw" in {
      val multi = events("multi.sse")
      multi.collect { case ItemDone(_, _, _, _, o: AgentSessionItem.Other) =>
        o.itemType
      }.distinct shouldBe
        Seq("create_subagent_call", "wait_for_subagents_call", "close_subagent_call")
      multi.collect { case o: Other => o.eventType }.distinct should contain allOf (
        "agent.session.subagent.created",
        "agent.session.subagent.closed"
      )
      multi.collect { case ItemAdded(_, _, _, _, o: AgentSessionItem.Other) =>
        o.itemType
      } should contain(
        "agent_message"
      )
      AgentSessionEvents.finalAnswer(multi) shouldBe
        "Fruits: apple, banana, orange; vegetables: carrot, broccoli, spinach."
    }
  }

  "AgentSessionEvents.untilSettled" should {

    "end a subscription at the idle that follows a turn - not at one before it" in {
      val followUp = events("sub-1.sse")
      // a subscription could deliver the idle of the previous turn first
      val previousIdle = events("basic.sse").last
      val out = Source((previousIdle +: (followUp :+ events("basic.sse").head)).toList)
        .via(AgentSessionEvents.untilSettled())
        .runWith(Sink.seq)
        .futureValue

      out.head shouldBe previousIdle
      out.last.eventType shouldBe SessionIdle
      out.size shouldBe followUp.size + 1
      AgentSessionEvents.finalAnswer(out) shouldBe "7"
    }

    "optionally end at requires_action" in {
      val paused = events("tool-1.sse")
      Source((paused :+ paused.head).toList)
        .via(AgentSessionEvents.untilSettled(stopOnRequiresAction = true))
        .runWith(Sink.seq)
        .futureValue shouldBe paused
    }
  }

  "the resources" should {

    "parse a session with its inline agent" in {
      val session = Json.parse(resource("basic-session.json")).as[AgentSession]
      session.isIdle shouldBe true
      session.agent.map(a => (a.model, a.instructions, a.serviceTier)) shouldBe
        Some(("gpt-6-luna", Some("Be brief."), Some("auto")))
      session.agent.flatMap(_.reasoning).flatMap(_.effort) shouldBe Some(
        ReasoningEffort.medium
      )
      session.agent.flatMap(_.multiAgent) shouldBe Some(MultiAgentConfig(enabled = false))
      session.environment.map(e => (e \ "type").as[String]) shouldBe Some("none")
      session.usage shouldBe None
    }

    "parse a reusable agent, items, turns and subagents" in {
      val agent = Json.parse(resource("agent.json")).as[Agent]
      (agent.name, agent.metadata, agent.createdAt.isDefined) shouldBe
        (Some("probe-agent"), Map("created_by" -> "probe"), true)

      val items = Json.parse(resource("basic-items.json")).as[AgentsPage[AgentSessionItem]]
      items.data.collect { case m: AgentSessionItem.Message =>
        (m.role, m.text)
      } should contain(
        ("assistant", "Hi there!")
      )

      val turns = Json.parse(resource("basic-turns.json")).as[AgentsPage[AgentTurn]]
      turns.data.map(t => (t.status, t.completedAt.isDefined)) shouldBe Seq(
        ("completed", true)
      )
      turns.hasMore shouldBe false

      val subagents =
        Json.parse(resource("multi-subagents.json")).as[AgentsPage[AgentSubagent]]
      subagents.data.map(_.status).distinct shouldBe Seq("closed")
      subagents.data.flatMap(_.name) should have size 2
    }

    "read an unknown tool, item, event or required action raw, never failing" in {
      Json.obj("type" -> "shiny_tool", "x" -> 1).as[AgentTool] shouldBe
        AgentTool.Raw(Json.obj("type" -> "shiny_tool", "x" -> 1))
      Json.obj("type" -> "hologram", "id" -> "h1").as[AgentSessionItem] shouldBe
        AgentSessionItem.Other("hologram", Json.obj("type" -> "hologram", "id" -> "h1"))
      Json.obj("type" -> "agent.session.teleported").as[AgentSessionEvent] shouldBe
        Other("agent.session.teleported", Json.obj("type" -> "agent.session.teleported"))
      Json.obj("type" -> "sign_contract", "id" -> 1).as[AgentRequiredAction] shouldBe
        AgentRequiredAction.Other(
          "sign_contract",
          Json.obj("type" -> "sign_contract", "id" -> 1)
        )
    }
  }

  "the request bodies" should {

    "write an inline agent, its tools and the environment" in {
      val settings = CreateAgentSessionSettings(
        agent = Some(
          AgentConfig(
            model = Some("gpt-6.1-sol"),
            instructions = Some("Be brief."),
            reasoning = Some(AgentReasoning(effort = Some(ReasoningEffort.high))),
            serviceTier = Some(ServiceTier.fast),
            tools = Some(
              Seq(
                AgentTool.Function("get_weather", "Weather", Json.obj("type" -> "object")),
                AgentTool.Mcp(
                  "deepwiki",
                  McpTransport.Http("https://mcp.deepwiki.com/mcp"),
                  allowedTools = Some(Seq("ask_wiki_question"))
                ),
                AgentTool.WebSearch(contextSize = Some("low")),
                AgentTool.Raw(Json.obj("type" -> "future_tool"))
              )
            ),
            multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
          )
        ),
        environment = AgentEnvironment.OpenAIHosted(
          containerSize = Some("small"),
          setupCommands = Seq("pip install x"),
          extra = Json.obj("network" -> Json.obj("mode" -> "restricted"))
        ),
        metadata = Map("k" -> "v")
      )

      createAgentSessionBody(settings, Some(AgentInput.Text("hi")), stream = true) shouldBe
        Json.obj(
          "agent" -> Json.obj(
            "model" -> "gpt-6.1-sol",
            "instructions" -> "Be brief.",
            "reasoning" -> Json.obj("effort" -> "high"),
            "service_tier" -> "fast",
            "tools" -> Json.arr(
              Json.obj(
                "type" -> "function",
                "name" -> "get_weather",
                "description" -> "Weather",
                "parameters" -> Json.obj("type" -> "object")
              ),
              Json.obj(
                "type" -> "mcp",
                "server_label" -> "deepwiki",
                "transport" -> Json.obj(
                  "type" -> "http",
                  "server_url" -> "https://mcp.deepwiki.com/mcp"
                ),
                "allowed_tools" -> Json.arr("ask_wiki_question")
              ),
              Json.obj("type" -> "web_search", "context_size" -> "low"),
              Json.obj("type" -> "future_tool")
            ),
            "multi_agent" -> Json.obj("enabled" -> true, "max_concurrent_subagents" -> 2)
          ),
          "environment" -> Json.obj(
            "type" -> "openai_hosted",
            "container_size" -> "small",
            "setup_commands" -> Json.arr("pip install x"),
            "network" -> Json.obj("mode" -> "restricted")
          ),
          "metadata" -> Json.obj("k" -> "v"),
          "input" -> "hi",
          "stream" -> true
        )

      createAgentSessionBody(
        CreateAgentSessionSettings(agentId = Some("agent_1")),
        Some(AgentInput.Messages(Seq(AgentUserMessage.text("hi")))),
        stream = false
      ) shouldBe Json.obj(
        "agent_id" -> "agent_1",
        "environment" -> Json.obj("type" -> "none"),
        "input" -> Json.arr(
          Json.obj(
            "type" -> "message",
            "role" -> "user",
            "content" -> Json.arr(Json.obj("type" -> "input_text", "text" -> "hi"))
          )
        )
      )
    }

    "write the session input events" in {
      val call =
        AgentRequiredAction.FunctionCall("turn_1", "call_1", "get_weather", Json.obj())
      Json.toJson(
        Seq[AgentSessionInput](
          AgentSessionInput.text("next"),
          AgentSessionInput.toolResult(call, "Sunny"),
          AgentSessionInput
            .ToolResult("turn_1", "call_2", success = false, error = Some("boom")),
          AgentSessionInput.Cancel
        )
      ) shouldBe Json.arr(
        Json.obj(
          "type" -> "agent.session.input.message",
          "input" -> Json.arr(
            Json.obj(
              "type" -> "message",
              "role" -> "user",
              "content" -> Json.arr(Json.obj("type" -> "input_text", "text" -> "next"))
            )
          )
        ),
        Json.obj(
          "type" -> "agent.session.input.tool_result",
          "turn_id" -> "turn_1",
          "call_id" -> "call_1",
          "success" -> true,
          "output" -> "Sunny"
        ),
        Json.obj(
          "type" -> "agent.session.input.tool_result",
          "turn_id" -> "turn_1",
          "call_id" -> "call_2",
          "success" -> false,
          "error" -> "boom"
        ),
        Json.obj("type" -> "agent.session.input.cancel")
      )
    }

    "write a reusable agent and its update" in {
      Json.toJson(
        CreateAgentSettings(
          model = "gpt-6-luna",
          name = Some("a"),
          metadata = Map("k" -> "v")
        )
      ) shouldBe Json.obj(
        "model" -> "gpt-6-luna",
        "name" -> "a",
        "metadata" -> Json.obj("k" -> "v")
      )

      Json.toJson(UpdateAgentSettings(instructions = Some("x"), tools = Some(Nil))) shouldBe
        Json.obj("instructions" -> "x", "tools" -> Json.arr())
      Json.toJson(UpdateAgentSettings()).as[JsObject] shouldBe Json.obj()
    }
  }
}
