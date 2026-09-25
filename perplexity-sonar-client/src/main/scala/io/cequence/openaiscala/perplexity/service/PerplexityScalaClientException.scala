package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.ProviderErrorDetails
import io.cequence.wsclient.domain.CequenceWSException
import play.api.libs.json.{JsValue, Json}

import scala.util.Try

/**
 * The exceptions the Perplexity service throws, classified by HTTP status and the body's
 * `error.type` - every error body is `{"error": {"message", "type", "code"}}` with `code` =
 * the HTTP status (bodies collected against the live API, 2026-09-25):
 *
 * | status    | `error.type` (examples)                                                                         | exception                                                                                 |
 * |:----------|:------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------|
 * | 400       | `invalid_model` ("Invalid model 'nope'" - an unknown preset or model)                           | [[PerplexityScalaInvalidModelException]]                                                  |
 * | 400 / 422 | `invalid_request` (validation failed: empty input, unknown tool type, bad effort, bad JSON)     | [[PerplexityScalaInvalidRequestException]]                                                |
 * | 401 / 403 | `invalid_api_key`                                                                               | [[PerplexityScalaUnauthorizedException]]                                                  |
 * | 404 / 405 | `not_found` ("resource not found")                                                              | [[PerplexityScalaNotFoundException]]                                                      |
 * | 408       |                                                                                                 | [[PerplexityScalaClientTimeoutException]]                                                 |
 * | 429       | `request_rate_limit_exceeded` (the per-response endpoints - cancel, files - are throttled hard) | [[PerplexityScalaRateLimitException]]                                                     |
 * | 503 / 529 |                                                                                                 | [[PerplexityScalaEngineOverloadedException]]                                              |
 * | other 5xx |                                                                                                 | [[PerplexityScalaServerErrorException]]                                                   |
 * | transport | timeout / unknown host                                                                          | [[PerplexityScalaClientTimeoutException]] / [[PerplexityScalaClientUnknownHostException]] |
 *
 * Every instance carries the `httpCode`, the body's `errorType` and Perplexity's
 * `x-request-id` (`requestId`) when known. [[PerplexityRetryable]] says which ones are worth
 * retrying.
 */
class PerplexityScalaClientException(
  message: String,
  cause: Throwable = null,
  val httpCode: Option[Int] = None,
  val errorType: Option[String] = None,
  val requestId: Option[String] = None
) extends CequenceWSException(message, cause)
    with ProviderErrorDetails

/** 401 (invalid or revoked key) or 403. */
class PerplexityScalaUnauthorizedException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/**
 * 400 / 422: the request was rejected - a validation failure (`invalid_request`: empty input,
 * an unknown tool type, an invalid reasoning effort, malformed JSON, ...).
 */
class PerplexityScalaInvalidRequestException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/** 400 `invalid_model`: an unknown preset or model id. */
class PerplexityScalaInvalidModelException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaInvalidRequestException(
      message,
      cause,
      httpCode,
      errorType,
      requestId
    )

/**
 * 404 / 405: an unknown or unstored response / file id, one of another account, or a wrong
 * path. The retrieve / download calls return `None` instead.
 */
class PerplexityScalaNotFoundException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/** 408, or a client-side request timeout. Retryable. */
class PerplexityScalaClientTimeoutException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/** The API host could not be resolved. Not retryable. */
class PerplexityScalaClientUnknownHostException(
  message: String,
  cause: Throwable = null
) extends PerplexityScalaClientException(message, cause)

/** 429 `request_rate_limit_exceeded`. Retryable with backoff. */
class PerplexityScalaRateLimitException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/** A 5xx, typically transient. Retryable. */
class PerplexityScalaServerErrorException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaClientException(message, cause, httpCode, errorType, requestId)

/** 503 / 529 - overloaded. Retryable. */
class PerplexityScalaEngineOverloadedException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends PerplexityScalaServerErrorException(message, cause, httpCode, errorType, requestId)

/** Which Perplexity exceptions are worth retrying: rate limits, 5xx and timeouts. */
object PerplexityRetryable {

  def unapply(t: Throwable): Option[PerplexityScalaClientException] =
    t match {
      case e: PerplexityScalaClientException if apply(e) => Some(e)
      case _                                             => None
    }

  def apply(t: PerplexityScalaClientException): Boolean =
    t match {
      case _: PerplexityScalaRateLimitException     => true
      case _: PerplexityScalaServerErrorException   => true // incl. EngineOverloaded
      case _: PerplexityScalaClientTimeoutException => true
      case _                                        => false
    }
}

/**
 * Classifies Perplexity's HTTP errors into the [[PerplexityScalaClientException]] hierarchy
 * (see its scaladoc for the table), with `error.message` as the readable part of the message.
 */
object HandlePerplexityErrorCodes {

  def toException(
    httpCode: Int,
    body: String,
    requestId: Option[String] = None
  ): PerplexityScalaClientException = {
    val json = Try(Json.parse(body)).toOption
    val kind = json.flatMap(errorType)
    val errorMessage =
      s"Code ${httpCode} : ${json.flatMap(extractMessage).getOrElse(body.trim)}" +
        requestId.fold("")(id => s" [request $id]")

    // (message, httpCode, errorType, requestId) -> the exception of the right kind
    type Build =
      (String, Option[Int], Option[String], Option[String]) => PerplexityScalaClientException

    val build: Build = httpCode match {
      case 400 if kind.contains("invalid_model") =>
        new PerplexityScalaInvalidModelException(_, null, _, _, _)
      case 400 | 422 => new PerplexityScalaInvalidRequestException(_, null, _, _, _)
      case 401 | 403 => new PerplexityScalaUnauthorizedException(_, null, _, _, _)
      case 404 | 405 => new PerplexityScalaNotFoundException(_, null, _, _, _)
      case 408       => new PerplexityScalaClientTimeoutException(_, null, _, _, _)
      case 429       => new PerplexityScalaRateLimitException(_, null, _, _, _)
      case 503 | 529 => new PerplexityScalaEngineOverloadedException(_, null, _, _, _)
      case code if code >= 500 => new PerplexityScalaServerErrorException(_, null, _, _, _)
      case _                   => new PerplexityScalaClientException(_, null, _, _, _)
    }

    build(errorMessage, Some(httpCode), kind, requestId)
  }

  /**
   * An error body's JSON: `{"error": {...}}` classified by its `code` (the HTTP status - a raw
   * stream never sees the status itself), or None when it carries no usable code.
   */
  def fromErrorBody(json: JsValue): Option[PerplexityScalaClientException] = {
    val error = json \ "error"
    error
      .\("code")
      .asOpt[Int]
      .orElse((error \ "code").asOpt[String].flatMap(code => Try(code.toInt).toOption))
      .map(toException(_, json.toString()))
  }

  /** `error.type` of a Perplexity error body, e.g. `invalid_model`. */
  def errorType(body: String): Option[String] =
    Try(Json.parse(body)).toOption.flatMap(errorType)

  private def errorType(json: JsValue): Option[String] =
    (json \ "error" \ "type").asOpt[String]

  private def extractMessage(json: JsValue): Option[String] =
    (json \ "error" \ "message")
      .asOpt[String]
      .orElse((json \ "error").asOpt[String])
      .orElse((json \ "message").asOpt[String])
      .orElse((json \ "detail").asOpt[String])
}
