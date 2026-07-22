package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}

import scala.concurrent.Future

/**
 * Live control-plane probe of the Managed Agents API authenticated via an `ant auth` OAuth
 * profile with automatic token refresh - [[AnthropicServiceFactory.forOAuthProfile]] - instead
 * of the usual `x-api-key` / `ANTHROPIC_API_KEY` path used by
 * [[AnthropicManagedAgentsExample]].
 *
 * Requires a prior `ant auth login`. The profile is resolved (in order) from:
 *   - the `profile` argument (not used here, so defaulted to `None`)
 *   - the `ANTHROPIC_PROFILE` env var
 *   - `<config-dir>/active_config`
 *   - `"default"`
 *
 * where `<config-dir>` is `configDir` if given, else the `ANTHROPIC_CONFIG_DIR` env var, else
 * `~/.config/anthropic`. Unlike [[AnthropicManagedAgentsWithAuthTokenLive]]'s static token,
 * profile credentials auto-refresh: an expiring access token is exchanged via `POST
 * /v1/oauth/token` and the refreshed token is written back to the profile's credentials file,
 * so long-running processes stay authenticated without manual intervention.
 *
 * Kept deliberately read-only/minimal (list agents, list sessions) - no agents, environments,
 * or sessions are created - so it's a cheap way to confirm the profile authenticates against
 * the control plane. See [[AnthropicManagedAgentsLive]] / [[AnthropicManagedSessionsLive]] for
 * full create/update/archive flows against the default `ANTHROPIC_API_KEY` auth.
 */
object AnthropicManagedAgentsWithOAuthProfileLive extends AnthropicManagedAgentsExample {

  override protected val service: AnthropicService = AnthropicServiceFactory.forOAuthProfile()

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
