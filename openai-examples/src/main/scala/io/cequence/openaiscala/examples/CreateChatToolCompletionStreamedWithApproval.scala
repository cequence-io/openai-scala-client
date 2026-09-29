package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentTool,
  AgentToolConfig,
  PermissionPolicy
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.response.AssembledChatCompletion
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.{ChatCompletionTool, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Human approval mid-stream on the typed stream: a run pauses until a tool call is approved
 * (`ChatChunk.ToolApprovalRequest` + `Finish(approval_required)`), and a second call carrying
 * the decisions (`setToolApprovalDecisions`) resumes it.
 *
 *   - OpenAI (Responses API): a provider-neutral `MCPServerTool(requireApproval = true)`
 *     (DeepWiki) - the run pauses before each call (twice here); the paused response is stored
 *     and each resume continues it by id (same messages and tools, plus the answers)
 *   - Anthropic Managed Agents: an agent whose built-in toolset asks before every call
 *     (`always_ask`); the resume continues the same session (messages are ignored)
 *
 * Run with `OPENAI_SCALA_CLIENT_API_KEY` and / or `ANTHROPIC_API_KEY` (with the
 * `managed-agents-2026-04-01` beta); a provider whose key is missing is skipped. Pass `deny`
 * to deny the calls instead of approving them. Note: the managed-agent adapter creates an
 * agent (`openai-scala-client-chat-adapter-<model>`) on first use - archive it when done.
 */
object CreateChatToolCompletionStreamedWithApproval {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val deny = args.contains("deny")
    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)

    def pauseAndResume(
      label: String,
      service: OpenAIChatCompletionStreamedService,
      messages: Seq[UserMessage],
      tools: Seq[ChatCompletionTool],
      settings: CreateChatCompletionSettings
    ): Future[Unit] = {
      def show(
        phase: String,
        assembled: AssembledChatCompletion
      ): Unit = {
        println(s"\n[$label] $phase: finish=${assembled.finishReason.getOrElse("-")}")
        assembled.toolApprovalRequests.foreach(r =>
          println(
            s"  approval? ${r.serverName.fold("")(_ + ".")}${r.toolName}(${r.arguments}) [${r.requestId}]"
          )
        )
        assembled.toolCalls.foreach(c => println(s"  tool call ${c.toolName}(${c.arguments})"))
        assembled.toolResults.foreach(r =>
          println(s"  tool result error=${r.isError} ${r.text.getOrElse("").take(160)}")
        )
        if (assembled.text.nonEmpty) println(s"  text: ${assembled.text.take(400)}")
      }

      // resume while the run keeps pausing (a run may ask again for its next call)
      def run(
        decisions: Seq[io.cequence.openaiscala.domain.response.ToolApprovalDecision],
        round: Int
      ): Future[Unit] =
        service
          .createChatToolCompletionStreamed(
            messages,
            tools,
            None,
            settings.setToolApprovalDecisions(decisions)
          )
          .assembled
          .flatMap { assembled =>
            show(if (round == 0) "first call" else s"resume #$round", assembled)
            if (assembled.awaitingApproval && round < 5)
              run(
                if (deny) assembled.denyAll(Some("Not allowed in this demo."))
                else assembled.approveAll,
                round + 1
              )
            else {
              if (assembled.awaitingApproval)
                println(
                  s"[$label] still paused after $round resumes - giving up (the run stays paused: " +
                    s"'${assembled.toolApprovalRequests.head.runId}')"
                )
              Future.successful(())
            }
          }

      run(Nil, 0).andThen { case _ => service.close() }
    }

    // one provider after the other (the calls are lazy), each service closed whatever happens
    def openAI =
      if (has("OPENAI_SCALA_CLIENT_API_KEY"))
        pauseAndResume(
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
        pauseAndResume(
          "managed-agent",
          AnthropicServiceFactory.managedAgentAsOpenAI(
            agentTools = Seq(
              AgentTool.Toolset(defaultConfig =
                Some(AgentToolConfig(permissionPolicy = Some(PermissionPolicy.always_ask)))
              )
            )
          ),
          Seq(UserMessage("Run `echo approval-demo-ok` with bash and tell me the output.")),
          Nil,
          CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_4_6)
        )
      else Future.successful(println("[managed-agent] skipped - no ANTHROPIC_API_KEY"))

    try Await.result(openAI.flatMap(_ => anthropic), 5.minutes)
    finally {
      Await.result(system.terminate(), 30.seconds)
      ()
    }
  }
}
