package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  EnvironmentConfig,
  Networking,
  Packages
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateEnvironmentSettings,
  AnthropicUpdateEnvironmentSettings
}

import scala.concurrent.Future

/**
 * Live end-to-end check of the Environments API: create -> get -> list -> update -> archive ->
 * delete a cloud environment.
 */
object AnthropicManagedEnvironmentsLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      created <- service.createEnvironment(
        AnthropicCreateEnvironmentSettings(
          name = "openai-scala-client smoke env",
          config = Some(
            EnvironmentConfig.Cloud(
              networking = Some(Networking.Unrestricted),
              packages = Some(Packages(pip = Seq("pandas")))
            )
          ),
          description = Some("smoke-test env")
        )
      )
      _ = println(s"created: id=${created.id} config=${created.config}")

      fetched <- service.getEnvironment(created.id)
      _ = println(s"get: ${fetched.name}")

      listed <- service.listEnvironments(limit = Some(5))
      _ = println(s"list: count=${listed.data.size} nextPage=${listed.nextPage}")

      updated <- service.updateEnvironment(
        created.id,
        AnthropicUpdateEnvironmentSettings(description = Some("updated smoke-test env"))
      )
      _ = println(s"update: description=${updated.description}")

      archived <- service.archiveEnvironment(created.id)
      _ = println(s"archive: archivedAt=${archived.archivedAt}")

      deleted <- service.deleteEnvironment(created.id)
      _ = println(s"delete: ${deleted.`type`}")
    } yield ()
}
