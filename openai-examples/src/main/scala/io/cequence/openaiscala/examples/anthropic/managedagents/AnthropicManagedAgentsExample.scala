package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

/**
 * Base for Managed Agents examples. Uses the same `Example` framework
 * ([[io.cequence.openaiscala.examples.ExampleBase]]) as the other examples, but parameterized
 * to the full [[AnthropicService]] so the managed-agents endpoints (agents, environments,
 * sessions, deployments, vaults, memory stores) are available — the plain `Example` trait is
 * hard-wired to `OpenAIService` and would not expose them.
 *
 * All examples require `ANTHROPIC_API_KEY` with the `managed-agents-2026-04-01` beta enabled.
 *
 * Note: `ExampleBase` calls `System.exit` on completion, which sbt's TrapExit can swallow when
 * running via `runMain` — run these from IntelliJ for reliable console output.
 */
trait AnthropicManagedAgentsExample extends ExampleBase[AnthropicService] {
  override protected val service: AnthropicService = AnthropicServiceFactory()
}
