package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.typesafe.service.impl.TypeSafeServiceImpl
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts

import scala.concurrent.ExecutionContext

/**
 * Creates [[TypeSafeService]] instances. Defaults come from the same environment variables the
 * official TypeSafe SDKs read: `TYPESAFE_API_KEY` (required), `TYPESAFE_BASE_URL` (optional,
 * `https://api.typesafe.ai`) and `TYPESAFE_DEFAULT_MODEL` (optional, `jev-latest`).
 *
 * {{{
 * implicit val ec = ExecutionContext.global
 *
 * val typeSafe = TypeSafeServiceFactory()   // TYPESAFE_API_KEY from the env
 *
 * typeSafe.systemOne(
 *   state = "I was charged twice. Please fix this ASAP.",
 *   questions = Map(
 *     "department" -> ChoiceQuestion("Which team?", "billing" -> "Payments", "technical" -> "Bugs"),
 *     "is_urgent" -> NoulQuestion("Does this convey urgency?")
 *   )
 * ).map { response =>
 *   response.choice("department").choice   // "billing"
 *   response.noul("is_urgent").noul        // 0.97
 * }
 * }}}
 */
object TypeSafeServiceFactory extends EnvHelper {

  import TypeSafeServiceConsts._

  /**
   * A service on its own PRIVATE engine (HTTP client + actor system), closed with the service.
   *
   * @param timeouts
   *   client-level timeouts (milliseconds); the official SDKs default to 10 s per attempt,
   *   which System One's ~100 ms answers rarely approach
   */
  def apply(
    apiKey: String = getEnvValue(apiKeyEnvKey),
    baseUrl: String = baseUrlFromEnv,
    defaultModel: String = defaultModelFromEnv,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(apiKey, baseUrl, defaultModel, timeouts)

  /**
   * A service on a CALLER-SUPPLIED, SITE-STATELESS engine - e.g. one shared with other
   * providers via `WSClientEngineRegistry()` / `StreamedEngineRegistry.outputStreamed()` - so
   * several services share one connection pool and actor system. Closing such a service does
   * NOT close the shared engine; close the engine once, when done with all services using it.
   */
  def withEngine(
    engine: WSClientEngine,
    apiKey: String = getEnvValue(apiKeyEnvKey),
    baseUrl: String = baseUrlFromEnv,
    defaultModel: String = defaultModelFromEnv
  )(
    implicit ec: ExecutionContext
  ): TypeSafeService =
    new TypeSafeServiceImpl(apiKey, baseUrl, defaultModel, externalEngine = Some(engine))

  private def baseUrlFromEnv: String = envOrElse(baseUrlEnvKey, defaultBaseUrl)

  private def defaultModelFromEnv: String =
    envOrElse(defaultModelEnvKey, TypeSafeServiceConsts.defaultModel)

  private def envOrElse(
    key: String,
    default: String
  ): String =
    Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)
}
