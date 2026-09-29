package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.CequenceWSHttpStatusException
import io.cequence.wsclient.service.{
  WSClientEngine,
  WSClientOutputStreamExtraAkka,
  WSClientWithEngineOutputStreamingBase
}
import play.api.libs.json.{JsNull, JsValue}

import scala.util.Try

/**
 * The base of every streamed service here. Two ways a stream reports an error, both classified
 * through the service's own `handleErrorCodes` - the same exceptions (and `Retryable`
 * verdicts) as its non-streamed calls:
 *
 *   - a non-2xx answer: ws-client (1.1.1+) fails the stream with a
 *     `CequenceWSHttpStatusException`, which the service-level `execJsonStream` /
 *     `execRawStream(endPoint, ...)` of `WSClientWithEngineOutputStreamingBase` route through
 *     `mapHttpStatusErrors`. Use those, not `engine.execJsonStream(site, ...)`; where an
 *     engine-level call is unavoidable, follow it with `.mapError(mapHttpStatusErrors)`
 *     (`StreamErrorMappingConventionSpec` enforces it)
 *   - an error frame inside a 200 stream (`{"error": {...}}`, e.g. a mid-stream overload):
 *     [[inBandStreamError]], with the status taken from the frame ([[InBandStreamErrors]])
 */
trait ClassifiedStreamingWSClient
    extends WSClientWithEngineOutputStreamingBase[
      WSClientEngine with WSClientOutputStreamExtraAkka
    ] {

  /**
   * The exception for an `{"error": ...}` frame of a stream (None if the frame is not one):
   * the service's classification of the status the frame carries, or a plain
   * `OpenAIScalaClientException` when it names none.
   */
  protected def inBandStreamError(json: JsValue): Option[Throwable] =
    (json \ "error").toOption.filter(_ != JsNull).map { error =>
      InBandStreamErrors
        .httpStatus(error)
        .map { status =>
          mapHttpStatusErrors(
            new CequenceWSHttpStatusException(
              s"In-band stream error (HTTP $status): $error",
              status,
              json.toString()
            )
          )
        }
        .getOrElse(new OpenAIScalaClientException(error.toString()))
    }
}

/**
 * The HTTP status an in-band stream error frame stands for - from its numeric `code` (Google,
 * Perplexity), its canonical `status` (Google: `RESOURCE_EXHAUSTED`, ...) or its `type`
 * (OpenAI: `server_error`, ...; Anthropic: `overloaded_error`, ...).
 */
object InBandStreamErrors {

  private val statusByName: Map[String, Int] = Map(
    // Google canonical statuses
    "INVALID_ARGUMENT" -> 400,
    "FAILED_PRECONDITION" -> 400,
    "OUT_OF_RANGE" -> 400,
    "UNAUTHENTICATED" -> 401,
    "PERMISSION_DENIED" -> 403,
    "NOT_FOUND" -> 404,
    "RESOURCE_EXHAUSTED" -> 429,
    "INTERNAL" -> 500,
    "UNKNOWN" -> 500,
    "DATA_LOSS" -> 500,
    "UNAVAILABLE" -> 503,
    "DEADLINE_EXCEEDED" -> 504
  )

  private val statusByType: Map[String, Int] = Map(
    // OpenAI
    "invalid_request_error" -> 400,
    "server_error" -> 500,
    "rate_limit_exceeded" -> 429,
    "requests" -> 429,
    "tokens" -> 429,
    "insufficient_quota" -> 429,
    // Anthropic
    "authentication_error" -> 401,
    "permission_error" -> 403,
    "not_found_error" -> 404,
    "request_too_large" -> 413,
    "rate_limit_error" -> 429,
    "api_error" -> 500,
    "overloaded_error" -> 529
  )

  def httpStatus(error: JsValue): Option[Int] = {
    val code = (error \ "code")
      .asOpt[Int]
      .orElse(
        (error \ "code").asOpt[String].flatMap(code => Try(code.toInt).toOption)
      )

    code
      .filter(code => code >= 400 && code <= 599)
      .orElse((error \ "status").asOpt[String].flatMap(statusByName.get))
      .orElse((error \ "type").asOpt[String].flatMap(statusByType.get))
  }
}
