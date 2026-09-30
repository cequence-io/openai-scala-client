package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.{
  AgentMessage,
  CreateModelResponseSettings,
  Inputs,
  MultiAgentCall,
  MultiAgentCallOutput,
  MultiAgentConfig,
  OutputMessageContent
}
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

/**
 * Server-hosted multi-agent execution (beta, GPT-6.1 Sol): the root agent spawns subagents,
 * waits for them and answers. The client sends the required `OpenAI-Beta:
 * responses_multi_agent=v1` header for you; `outputText` is the root agent's answer, the
 * delegation is in the `multi_agent_call` / `multi_agent_call_output` / `agent_message` items
 * and the subagents' own messages in `subagentMessages`. Note: `reasoning.summary` cannot be
 * combined with it.
 */
object CreateModelResponseMultiAgent extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text(
          "Use two subagents in parallel: one lists three fruits, the other three vegetables " +
            "(one short line each). Then reply with a single combined line."
        ),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_6_1_sol,
          multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2))),
          store = Some(false)
        )
      )
      .map { response =>
        response.output.foreach {
          case call: MultiAgentCall =>
            println(
              s"${call.agent.map(_.agentName).getOrElse("")} ${call.action} ${call.arguments.take(60)}"
            )
          case result: MultiAgentCallOutput =>
            println(s"  -> ${result.action}: ${result.outputText}")
          case message: AgentMessage =>
            println(s"  (agent message by ${message.agent.map(_.agentName).getOrElse("?")})")
          case _ =>
        }
        response.subagentMessages.foreach { message =>
          val agent = message.agent.map(_.agentName).getOrElse("?")
          val text = message.content.collect { case t: OutputMessageContent.OutputText =>
            t.text
          }
          println(s"[$agent] ${text.mkString}")
        }
        println(s"Answer: ${response.outputText.getOrElse("N/A")}")
      }
}
