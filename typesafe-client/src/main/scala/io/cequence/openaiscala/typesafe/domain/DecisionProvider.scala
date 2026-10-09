package io.cequence.openaiscala.typesafe.domain

import scala.util.Try

/**
 * A host of decision models - TypeSafe's System One protocol, or OpenAI's Decisions API
 * (`protocol`) - the counterpart of the chat providers' `ProviderSettings`. The known hosts
 * are presets in `io.cequence.openaiscala.typesafe.service.DecisionProviderSettings`
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
 *   how the host takes the image parts of a state ([[DecisionImage]]), if it takes any - they
 *   are then checked before sending, and the OpenAI adapter maps the image content of user
 *   messages into the state
 * @param requestIdHeaders
 *   the response headers carrying the host's request id, by priority
 * @param name
 *   a short name for logs and messages - the host name when not set
 * @param apiKeyEnvFallbacks
 *   environment variables read, in order, when `apiKeyEnvVariable` is not set
 * @param maxImageTiles
 *   the largest image the host takes, in 32 x 32 tiles ([[DecisionImage.tiles]]) - a larger
 *   one is refused before sending; no preset needs one (Perplexity's decider timed out over
 *   2,048 tiles until 2026-10-06, now it scales any image)
 * @param protocol
 *   the wire format the host speaks
 * @param apiKeyRequired
 *   whether the host needs a key - a local server (llama.cpp) runs without one unless started
 *   with `--api-key`; with no key no `Authorization` header is sent
 */
final case class DecisionProvider(
  baseUrl: String,
  apiKeyEnvVariable: String,
  defaultModel: String,
  decisionsPath: String = DecisionProvider.SystemOnePath,
  models: DecisionModelListing = DecisionModelListing.TypeSafe,
  maxQuestions: Option[Int] = None,
  images: DecisionImages = DecisionImages.Unsupported,
  requestIdHeaders: Seq[String] = DecisionProvider.DefaultRequestIdHeaders,
  name: Option[String] = None,
  apiKeyEnvFallbacks: Seq[String] = Nil,
  maxImageTiles: Option[Int] = None,
  protocol: DecisionProtocol = DecisionProtocol.SystemOne,
  apiKeyRequired: Boolean = true
) {

  /** Whether the host reads images at all. */
  def readsImages: Boolean = images != DecisionImages.Unsupported

  /** The name in logs and messages. */
  def label: String =
    name.getOrElse(
      Try(new java.net.URI(baseUrl).getHost).toOption.flatMap(Option(_)).getOrElse(baseUrl)
    )

  /**
   * The API key: `apiKeyEnvVariable`, else the first of `apiKeyEnvFallbacks` that is set -
   * empty (no key) when none is and the host needs none (`apiKeyRequired = false`).
   *
   * @throws IllegalStateException
   *   when none is and the host needs one
   */
  def apiKeyFromEnv: String = {
    val keys = apiKeyEnvVariable +: apiKeyEnvFallbacks
    keys
      .flatMap(key => Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty))
      .headOption
      .orElse(if (apiKeyRequired) None else Some(""))
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

/** How a [[DecisionProvider]] takes the image parts of a state ([[DecisionImage]]). */
sealed trait DecisionImages

object DecisionImages {

  /** No images - the host reads an image part as plain JSON text (Jev, `d1:free`). */
  case object Unsupported extends DecisionImages

  /**
   * The parts where they are, anywhere in the state - Perplexity's decider, and OpenAI's
   * Decisions API (whose protocol turns them into `input_image`s).
   */
  case object InState extends DecisionImages

  /**
   * A top-level `images` array of data URLs (Liquid AI's `d1`, llama.cpp): the parts are
   * lifted out of the state into it, each replaced by an `[image n]` marker. Liquid's host
   * lifts the parts of an array state itself but reads one nested in an object, or a state
   * that is a lone part, as text; llama.cpp lifts only the parts of chat messages (live
   * 2026-10-07).
   */
  case object ImagesField extends DecisionImages
}

/** The wire format of a [[DecisionProvider]]. */
sealed trait DecisionProtocol

object DecisionProtocol {

  /**
   * TypeSafe's System One, which most hosts copy: `{model, state, questions: {name:
   * question}}` answered with `{model, answers: {name: answer}, usage}`.
   */
  case object SystemOne extends DecisionProtocol

  /**
   * OpenAI's Decisions API: `{model, input, questions: [named predicate / choice / score]}`
   * answered with `{model, answers: [...], usage}` - the System One questions and answers are
   * translated to and from it (a noul is a predicate; a refusal arrives as an
   * `UnknownAnswer("refusal", ...)`).
   */
  case object OpenAI extends DecisionProtocol
}

/** How a [[DecisionProvider]]'s `listModels` finds the host's models. */
sealed trait DecisionModelListing

object DecisionModelListing {

  /**
   * `GET v1/models` answering TypeSafe's `{"models": [{"name", "description",
   * "release_date"}]}` - TypeSafe, Liquid (with `input_modalities`), Vercel's AI Gateway.
   */
  case object TypeSafe extends DecisionModelListing

  /**
   * `GET v1/models` with these query parameters, answering OpenAI's `{"data": [{"id",
   * "description", "created", "architecture"}]}` - OpenRouter lists its decision models only
   * with `output_modalities=decisions`; llama.cpp lists all its models, so one whose
   * `architecture.output_modalities` lack `decisions` is left out.
   */
  final case class OpenAIStyle(query: Seq[(String, String)] = Nil) extends DecisionModelListing

  /**
   * A fixed list, for a host that lists no decision models of its own (Perplexity's
   * `/v1/models` lists its Agent API models).
   */
  final case class Fixed(models: Seq[ModelMetadata]) extends DecisionModelListing
}
