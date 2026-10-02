package io.cequence.openaiscala.typesafe.domain

import scala.util.Try

/**
 * A host of decision models that speaks TypeSafe's System One protocol - the counterpart of
 * the chat providers' `ProviderSettings`. The known hosts are presets in
 * `io.cequence.openaiscala.typesafe.service.DecisionProviderSettings`
 * (`TypeSafeServiceFactory(DecisionProviderSettings.openRouter)`); any other compatible host
 * takes one of its own:
 *
 * {{{
 * TypeSafeServiceFactory(DecisionProvider("https://api.upstage.ai/", "UPSTAGE_API_KEY", "solar-decide"))
 * }}}
 *
 * @param baseUrl
 *   the host; `decisionsPath` and `v1/models` are appended to it
 * @param apiKeyEnvVariable
 *   the environment variable holding the key, sent as `Authorization: Bearer <key>`
 * @param defaultModel
 *   the model of a call that names none
 * @param decisionsPath
 *   the endpoint that answers the questions - `v1/systemone` on TypeSafe and most hosts,
 *   `v1/decisions` on Perplexity
 * @param models
 *   how `listModels` finds the host's models
 * @param maxQuestions
 *   the most questions the host takes per request - checked before sending
 * @param images
 *   whether the host reads OpenAI-style image parts in the state ([[DecisionImage]]) - they
 *   are then checked before sending, and the OpenAI adapter maps the image content of user
 *   messages into the state
 * @param requestIdHeaders
 *   the response headers carrying the host's request id, by priority
 * @param name
 *   a short name for logs and messages - the host name when not set
 * @param apiKeyEnvFallbacks
 *   environment variables read, in order, when `apiKeyEnvVariable` is not set
 */
final case class DecisionProvider(
  baseUrl: String,
  apiKeyEnvVariable: String,
  defaultModel: String,
  decisionsPath: String = DecisionProvider.SystemOnePath,
  models: DecisionModelListing = DecisionModelListing.TypeSafe,
  maxQuestions: Option[Int] = None,
  images: Boolean = false,
  requestIdHeaders: Seq[String] = DecisionProvider.DefaultRequestIdHeaders,
  name: Option[String] = None,
  apiKeyEnvFallbacks: Seq[String] = Nil
) {

  /** The name in logs and messages. */
  def label: String =
    name.getOrElse(
      Try(new java.net.URI(baseUrl).getHost).toOption.flatMap(Option(_)).getOrElse(baseUrl)
    )

  /**
   * The API key: `apiKeyEnvVariable`, else the first of `apiKeyEnvFallbacks` that is set.
   *
   * @throws IllegalStateException
   *   when none is
   */
  def apiKeyFromEnv: String = {
    val keys = apiKeyEnvVariable +: apiKeyEnvFallbacks
    keys
      .flatMap(key => Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty))
      .headOption
      .getOrElse(
        throw new IllegalStateException(
          s"${keys.mkString(" or ")} environment variable expected but not set. Alternatively, " +
            "you can pass the key explicitly to the factory method."
        )
      )
  }
}

object DecisionProvider {

  /** TypeSafe's endpoint, which most hosts copy. */
  val SystemOnePath = "v1/systemone"

  /** TypeSafe's own request id header, then the `x-request-id` most other hosts send. */
  val DefaultRequestIdHeaders: Seq[String] = Seq("x-typesafe-request-id", "x-request-id")
}

/** How a [[DecisionProvider]]'s `listModels` finds the host's models. */
sealed trait DecisionModelListing

object DecisionModelListing {

  /**
   * `GET v1/models` answering TypeSafe's `{"models": [{"name", "description",
   * "release_date"}]}` - TypeSafe, Liquid, Vercel's AI Gateway.
   */
  case object TypeSafe extends DecisionModelListing

  /**
   * `GET v1/models` with these query parameters, answering OpenAI's `{"data": [{"id",
   * "description", "created"}]}` - OpenRouter lists its decision models only with
   * `output_modalities=decisions`.
   */
  final case class OpenAIStyle(query: Seq[(String, String)] = Nil) extends DecisionModelListing

  /**
   * A fixed list, for a host that lists no decision models of its own (Perplexity's
   * `/v1/models` lists its Agent API models).
   */
  final case class Fixed(models: Seq[ModelMetadata]) extends DecisionModelListing
}
