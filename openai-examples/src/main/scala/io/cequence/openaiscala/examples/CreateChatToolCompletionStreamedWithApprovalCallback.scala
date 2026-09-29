package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentTool,
  AgentToolConfig,
  PermissionPolicy
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.response.{ChatChunk, ToolApprovalDecision}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{ChatCompletionTool, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.duration._
import scala.concurrent.{blocking, Await, ExecutionContext, Future}
import scala.io.StdIn

/**
 * Human approval by callback: `createChatToolCompletionStreamedWithApprovals` asks the
 * callback about every call the run pauses on and resumes it with the answers - the caller
 * gets ONE stream from the question to the final answer (compare
 * [[CreateChatToolCompletionStreamedWithApproval]], which resumes by hand).
 *
 * The callback approves `read_*` tools on its own and asks about the rest: on the console with
 * `ask`, otherwise it approves them (`deny` denies them).
 *
 *   - OpenAI (Responses API): DeepWiki as an `MCPServerTool(requireApproval = true)` - the run
 *     pauses before each call (twice)
 *   - Anthropic Managed Agents: the built-in toolset with `always_ask`
 *
 * Run with `OPENAI_SCALA_CLIENT_API_KEY` and / or `ANTHROPIC_API_KEY` (with the
 * `managed-agents-2026-04-01` beta); a provider whose key is missing is skipped.
 */
object CreateChatToolCompletionStreamedWithApprovalCallback {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val ask = args.contains("ask")
    val deny = args.contains("deny")
    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)

    def decide(label: String)(request: ChatChunk.ToolApprovalRequest)
      : Future[ToolApprovalDecision] = {
      val call =
        s"${request.serverName.fold("")(_ + ".")}${request.toolName}(${request.arguments})"

      if (request.toolName.startsWith("read_")) {
        println(s"\n[$label] approval: $call -> approved (read-only)")
        Future.successful(request.approve)
      } else if (ask)
        Future {
          val answer = blocking(StdIn.readLine(s"\n[$label] approve $call? [y/N] "))
          if (Option(answer).exists(_.trim.toLowerCase.startsWith("y"))) request.approve
          else request.deny("The user said no.")
        }
      else {
        println(s"\n[$label] approval: $call -> ${if (deny) "denied" else "approved"}")
        Future.successful(
          if (deny) request.deny("Not allowed in this demo.") else request.approve
        )
      }
    }

    def run(
      label: String,
      service: OpenAIChatCompletionStreamedService,
      messages: Seq[UserMessage],
      tools: Seq[ChatCompletionTool],
      settings: CreateChatCompletionSettings
    ): Future[Unit] =
      service
        .createChatToolCompletionStreamedWithApprovals(messages, tools, settings = settings)(
          decide(label)
        )
        .alsoTo(Sink.foreach[ChatChunk] {
          case ChatChunk.Text(text)                               => print(text)
          case _: ChatChunk.Thinking | _: ChatChunk.ToolCallDelta => ()
          case chunk => println(s"[$label] ${ChatChunkPrinter.describe(chunk)}")
        })
        .assembled
        .map { assembled =>
          println(
            s"\n[$label] done: finish=${assembled.finishReason.getOrElse("-")}, " +
              s"tool calls=${assembled.toolCalls.map(c => s"#${c.index} ${c.toolName}").mkString(", ")}, " +
              s"awaiting approval=${assembled.awaitingApproval}, usage=${assembled.usage.getOrElse("-")}"
          )
        }
        .andThen { case _ => service.close() }

    // one provider after the other (the calls are lazy), each service closed whatever happens
    def openAI =
      if (has("OPENAI_SCALA_CLIENT_API_KEY"))
        run(
          "openai",
          OpenAIServiceFactory.withStreaming(),
          Seq(
            UserMessage(
              "Using the deepwiki tools one call at a time on repo cequence-io/openai-scala-client: " +
                "first read its wiki structure, then ask which ws-client version it depends on. " +
                "Answer in two sentences."
            )
          ),
          Seq(
            MCPServerTool(
              "deepwiki",
              "https://mcp.deepwiki.com/mcp",
              allowedTools = Seq("read_wiki_structure", "ask_wiki_question"),
              requireApproval = true
            )
          ),
          CreateChatCompletionSettings(
            "gpt-5.4-mini",
            reasoning_effort = Some(ReasoningEffort.medium)
          )
        )
      else Future.successful(println("[openai] skipped - no OPENAI_SCALA_CLIENT_API_KEY"))

    def anthropic =
      if (has("ANTHROPIC_API_KEY"))
        run(
          "managed-agent",
          AnthropicServiceFactory.managedAgentAsOpenAI(
            agentTools = Seq(
              AgentTool.Toolset(defaultConfig =
                Some(AgentToolConfig(permissionPolicy = Some(PermissionPolicy.always_ask)))
              )
            )
          ),
          Seq(
            UserMessage(
              "Run `echo approval-callback-ok` with bash, then run `date -u +%Y` with bash, and " +
                "tell me both outputs."
            )
          ),
          Nil,
          CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_4_6)
        )
      else Future.successful(println("[managed-agent] skipped - no ANTHROPIC_API_KEY"))

    try Await.result(openAI.flatMap(_ => anthropic), 10.minutes)
    finally {
      Await.result(system.terminate(), 30.seconds)
      ()
    }
  }
}
