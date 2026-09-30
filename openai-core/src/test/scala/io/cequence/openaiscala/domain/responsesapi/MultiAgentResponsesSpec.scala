package io.cequence.openaiscala.domain.responsesapi

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import io.cequence.openaiscala.service.ChatChunks
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext

/**
 * Server-hosted multi-agent execution on the Responses API (beta) against a response and a
 * stream recorded live on 2026-09-30 (`gpt-6.1-sol`, two subagents spawned and waited for):
 * the settings, the new item types and their agent tags, the root agent's answer, and the
 * typed stream.
 */
class MultiAgentResponsesSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("multi-agent-responses")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def resource(name: String): String = {
    val source = scala.io.Source.fromResource(s"multi-agent/$name")
    try source.mkString
    finally source.close()
  }

  private lazy val response = Json.parse(resource("responses-multi-agent.json")).as[Response]

  private lazy val events: Seq[ResponseStreamEvent] =
    resource("responses-multi-agent.sse")
      .split("\n")
      .collect { case line if line.startsWith("data: ") => Json.parse(line.drop(6)) }
      .map(_.as[ResponseStreamEvent])
      .toSeq

  "CreateModelResponseSettings.multiAgent" should {

    "be written as multi_agent and ask for the beta header" in {
      val settings = CreateModelResponseSettings(
        model = "gpt-6.1-sol",
        multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
      )

      (Json.toJson(settings) \ "multi_agent").as[JsObject] shouldBe
        Json.obj("enabled" -> true, "max_concurrent_subagents" -> 2)
      Json.toJson(settings).as[CreateModelResponseSettings] shouldBe settings

      CreateModelResponseSettings.betaHeaders(settings) shouldBe
        Seq("OpenAI-Beta" -> "responses_multi_agent=v1")
      CreateModelResponseSettings.betaHeaders(settings.copy(multiAgent = None)) shouldBe Nil
      CreateModelResponseSettings.betaHeaders(
        settings.copy(multiAgent = Some(MultiAgentConfig(enabled = false)))
      ) shouldBe Nil
    }
  }

  "a multi-agent response" should {

    "parse the delegation items with their agent tags" in {
      val calls = response.output.collect { case c: MultiAgentCall => c }
      calls.map(c => (c.action, c.agent.map(_.agentName))) shouldBe Seq(
        ("spawn_agent", Some("/root")),
        ("spawn_agent", Some("/root")),
        ("wait_agent", Some("/root"))
      )
      (Json.parse(calls.head.arguments) \ "task_name").as[String] shouldBe "fruits"

      val results = response.output.collect { case r: MultiAgentCallOutput => r }
      results.map(_.callId) shouldBe calls.map(_.callId)
      (Json.parse(results.head.outputText) \ "task_name").as[String] shouldBe "/root/fruits"

      val agentMessages = response.output.collect { case m: AgentMessage => m }
      agentMessages should not be empty
      (agentMessages.head.content.head \ "type").as[String] shouldBe "encrypted_content"
    }

    "answer with the root agent's message only" in {
      response.outputText shouldBe Some(
        "Fruits: apple, banana, orange; vegetables: carrots, broccoli, spinach."
      )
      response.subagentMessages.map(_.agent.map(_.agentName)) shouldBe Seq(
        Some("/root/fruits"),
        Some("/root/vegetables")
      )
    }

    "write its items back as input for a stateless follow-up" in {
      val items = response.output.collect { case input: Input => input }
      items.size shouldBe response.output.size

      val json = Json.toJson(Inputs.Items(items: _*): Inputs)
      val reread = json.as[Inputs].asInstanceOf[Inputs.Items].items
      reread shouldBe items
      (json \ 0 \ "type").as[String] shouldBe "multi_agent_call"
      (json \ 0 \ "agent" \ "agent_name").as[String] shouldBe "/root"
    }
  }

  "ChatChunks.fromResponseEvents on a multi-agent stream" should {

    "stream the root agent's answer and the delegation as server-side tool calls" in {
      val chunks: Seq[ChatChunk] =
        Source(events.toList).via(ChatChunks.fromResponseEvents).runWith(Sink.seq).futureValue

      // only the root agent's text, never the subagents' (their deltas interleave with it)
      chunks.collect { case Text(text) => text }.mkString shouldBe
        "Fruits: apple, banana, orange; vegetables: carrot, broccoli, spinach."

      val calls = chunks.collect { case c: ToolCall => c }
      calls.map(c => (c.toolName, c.serverSide)) shouldBe Seq(
        ("multi_agent.spawn_agent", true),
        ("multi_agent.spawn_agent", true),
        ("multi_agent.wait_agent", true)
      )
      (Json.parse(calls.head.arguments) \ "task_name").as[String] shouldBe "fruits"

      val results = chunks.collect { case r: ToolResult => r }
      results.map(_.callId) shouldBe calls.map(_.callId)
      results.map(_.toolName) shouldBe calls.map(_.toolName)
      results.last.text.map(t => (Json.parse(t) \ "timed_out").as[Boolean]) shouldBe Some(
        false
      )

      // the subagents' messages are passed through whole, not dropped
      val subagentMessages = chunks.collect { case Other("subagent.message", raw) => raw }
      subagentMessages.map(raw => (raw \ "item" \ "agent" \ "agent_name").as[String]) shouldBe
        Seq("/root/fruits", "/root/vegetables")
      (subagentMessages.head \ "item" \ "content" \ 0 \ "text").as[String] shouldBe
        "Apple, banana, orange."

      chunks.collect { case f: Finish => f.reason } shouldBe Seq(FinishReason.stop)
      chunks.collect { case u: Usage => u.usage.total_tokens } shouldBe Seq(3790)
    }
  }
}
