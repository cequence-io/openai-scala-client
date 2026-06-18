package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.{AgentModelConfig, AgentTool}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicUpdateAgentSettings
}
import io.cequence.openaiscala.domain.NonOpenAIModelId

import scala.concurrent.Future

/**
 * Live end-to-end check of the Managed Agents API: create -> get -> list -> update ->
 * list-versions -> archive. Side-effecting: leaves one archived agent on the account.
 */
object AnthropicManagedAgentsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      created <- service.createAgent(
        AnthropicCreateAgentSettings(
          name = "openai-scala-client smoke agent",
          model = AgentModelConfig(NonOpenAIModelId.claude_fable_5),
          system = Some("You are a concise assistant."),
          tools = Seq(AgentTool.Toolset()),
          metadata = Map("source" -> "smoke-test")
        )
      )
      _ = println(
        s"created: id=${created.id} version=${created.version} tools=${created.tools.map(_.`type`)}"
      )

      fetched <- service.getAgent(created.id)
      _ = println(s"get: name=${fetched.name} system=${fetched.system}")

      listed <- service.listAgents(limit = Some(5))
      _ = println(s"list: count=${listed.data.size} nextPage=${listed.nextPage}")

      updated <- service.updateAgent(
        created.id,
        AnthropicUpdateAgentSettings(
          version = created.version,
          system = Some("You are a concise, friendly assistant.")
        )
      )
      _ = println(s"update: new version=${updated.version} system=${updated.system}")

      versions <- service.listAgentVersions(created.id)
      _ = println(s"versions: ${versions.data.map(_.version)}")

      archived <- service.archiveAgent(created.id)
      _ = println(s"archive: archivedAt=${archived.archivedAt}")
    } yield ()
}
