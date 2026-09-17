package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.ProviderErrorDetails
import io.cequence.wsclient.domain.CequenceWSException

/**
 * The exceptions the TypeSafe service throws, classified by HTTP status and by the body's
 * `detail.error_type` (collected against the live API, 2026-09-17):
 *
 * | status    | body                                                                                                           | exception                                                                             |
 * |:----------|:---------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------|
 * | 401 / 403 | `{"detail":{"error_type":"authentication_error",...}}`                                                         | [[TypeSafeScalaUnauthorizedException]]                                                |
 * | 400       | `{"detail":{"error_type":"max_tokens_exceeded"}}` (~32k input tokens)                                          | [[TypeSafeScalaTokenCountExceededException]]                                          |
 * | 400       | `{"detail":{"error_type":"api_usage_error","message":...}}` (unknown model, invalid JSON, feature not enabled) | [[TypeSafeScalaApiUsageException]]                                                    |
 * | 400       | `{"detail":"Choice question must have at least one choice: q"}` and other question-shape errors                | [[TypeSafeScalaInvalidRequestException]]                                              |
 * | 422       | `{"detail":[{"loc":[...],"msg":...},...]}` (schema validation)                                                 | [[TypeSafeScalaInvalidRequestException]] with `violations`                            |
 * | 404 / 405 | `{"detail":"Not Found"}`                                                                                       | [[TypeSafeScalaNotFoundException]]                                                    |
 * | 408       |                                                                                                                | [[TypeSafeScalaClientTimeoutException]]                                               |
 * | 429       | rate limit (250k tokens/s, 1,200 req/min)                                                                      | [[TypeSafeScalaRateLimitException]]                                                   |
 * | 503 / 529 | Overloaded                                                                                                     | [[TypeSafeScalaEngineOverloadedException]]                                            |
 * | other 5xx |                                                                                                                | [[TypeSafeScalaServerErrorException]]                                                 |
 * | transport | timeout / unknown host                                                                                         | [[TypeSafeScalaClientTimeoutException]] / [[TypeSafeScalaClientUnknownHostException]] |
 *
 * Every instance carries the `httpCode`, the body's `errorType` and the
 * `x-typesafe-request-id` (`requestId`) when known. [[TypeSafeRetryable]] says which ones are
 * worth retrying; the OpenAI adapter repacks them onto the shared `OpenAIScala*` hierarchy.
 */
class TypeSafeScalaClientException(
  message: String,
  cause: Throwable = null,
  val httpCode: Option[Int] = None,
  val errorType: Option[String] = None,
  val requestId: Option[String] = None
) extends CequenceWSException(message, cause)
    with ProviderErrorDetails

/** 401 (bad key) or 403 (no key). */
class TypeSafeScalaUnauthorizedException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/**
 * 400 `max_tokens_exceeded`: the state and the questions exceed the ~32k-token input limit.
 */
class TypeSafeScalaTokenCountExceededException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/**
 * 400 `api_usage_error`: the request is well-formed but asks for something the account or the
 * API cannot do - an unknown model, invalid JSON, a question type not enabled for the org.
 */
class TypeSafeScalaApiUsageException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** One entry of a 422 validation list: `questions.q.criteria` -> "Field required". */
final case class TypeSafeViolation(
  path: String,
  message: String
)

/**
 * The request body was rejected: a 422 schema validation (see `violations`) or a 400 on the
 * shape of a question (empty choice, more than 255 options, noul without instructions or
 * criteria, ...). The client fails fast on the shapes it knows about, so this mostly signals a
 * rule the client does not mirror yet.
 */
class TypeSafeScalaInvalidRequestException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None,
  val violations: Seq[TypeSafeViolation] = Nil
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** 404 / 405 - a wrong base URL or path. */
class TypeSafeScalaNotFoundException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** 408, or a client-side request timeout. Retryable. */
class TypeSafeScalaClientTimeoutException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** The API host could not be resolved. Not retryable. */
class TypeSafeScalaClientUnknownHostException(
  message: String,
  cause: Throwable = null
) extends TypeSafeScalaClientException(message, cause)

/** 429. Retryable with backoff, as the docs ask. */
class TypeSafeScalaRateLimitException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** 5xx other than the overload statuses. Retryable. */
class TypeSafeScalaServerErrorException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaClientException(message, cause, httpCode, errorType, requestId)

/** 529 (TypeSafe's "Overloaded") or 503. Retryable. */
class TypeSafeScalaEngineOverloadedException(
  message: String,
  cause: Throwable = null,
  httpCode: Option[Int] = None,
  errorType: Option[String] = None,
  requestId: Option[String] = None
) extends TypeSafeScalaServerErrorException(message, cause, httpCode, errorType, requestId)

/**
 * Which [[TypeSafeScalaClientException]]s a retry may fix: rate limits, overloads, other
 * server errors and timeouts - the set the official SDKs retry (429 / 500 / 502 / 503 / 504).
 */
object TypeSafeRetryable {

  def unapply(t: Throwable): Option[TypeSafeScalaClientException] =
    t match {
      case e: TypeSafeScalaClientException if apply(e) => Some(e)
      case _                                           => None
    }

  def apply(t: TypeSafeScalaClientException): Boolean =
    t match {
      case _: TypeSafeScalaRateLimitException     => true
      case _: TypeSafeScalaServerErrorException   => true // incl. EngineOverloaded
      case _: TypeSafeScalaClientTimeoutException => true
      case _                                      => false
    }
}
