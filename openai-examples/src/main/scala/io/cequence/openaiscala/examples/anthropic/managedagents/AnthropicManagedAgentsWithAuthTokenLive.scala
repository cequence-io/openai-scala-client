package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}

import scala.concurrent.Future

/**
 * Live control-plane probe of the Managed Agents API authenticated with a static OAuth/bearer
 * token via [[AnthropicServiceFactory.forAuthToken]] - instead of the usual `x-api-key` /
 * `ANTHROPIC_API_KEY` path used by [[AnthropicManagedAgentsExample]].
 *
 * Requires `ANTHROPIC_AUTH_TOKEN` set to a platform OAuth access token minted by `ant auth
 * login`:
 * {{{
 * export ANTHROPIC_AUTH_TOKEN=$(ant auth print-credentials --access-token)
 * }}}
 * `ANTHROPIC_API_KEY` is NOT read/relied upon here - this example only exercises the bearer
 * token path.
 *
 * Falls back to `CLAUDE_CODE_OAUTH_TOKEN` (a `claude setup-token` subscription token) when
 * `ANTHROPIC_AUTH_TOKEN` is unset. CAVEAT: such tokens are scoped to the Claude Code backend
 * and are documented as rejected by the public API - expect a 401 in that case. Subscription
 * usage for agents is sanctioned only through the Claude Agent SDK/CLI harness (the "Agent SDK
 * credit", 2026-06-15), so that fallback is best-effort only and not a substitute for `ant
 * auth login`.
 *
 * Kept deliberately read-only/minimal (list agents, list sessions) - no agents, environments,
 * or sessions are created - so it's a cheap way to confirm the token authenticates against the
 * control plane. See [[AnthropicManagedAgentsLive]] / [[AnthropicManagedSessionsLive]] for
 * full create/update/archive flows against the default `ANTHROPIC_API_KEY` auth.
 */
object AnthropicManagedAgentsWithAuthTokenLive extends AnthropicManagedAgentsExample {

  override protected val service: AnthropicService = AnthropicServiceFactory.forAuthToken()

  override protected def run: Future[_] =
    for {
      agents <- service.listAgents(limit = Some(5))
      _ = println(
        s"agents: count=${agents.data.size} names=${agents.data.take(5).map(_.name)}"
      )

      sessions <- service.listSessions(limit = Some(5))
      _ = println(s"sessions: count=${sessions.data.size} nextPage=${sessions.nextPage}")
    } yield ()
}
