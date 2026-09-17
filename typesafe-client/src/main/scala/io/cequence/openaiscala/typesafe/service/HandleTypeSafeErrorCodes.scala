package io.cequence.openaiscala.typesafe.service

import io.cequence.wsclient.service.WSClient
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import scala.util.Try

/**
 * Classifies the TypeSafe API's HTTP errors into the [[TypeSafeScalaClientException]]
 * hierarchy (see its scaladoc for the status / body table) and unpacks the body's `detail`
 * into the message the way the official SDK does. Mixed into the service so ws-client's error
 * path lands here; [[TypeSafeServiceImpl]] calls [[HandleTypeSafeErrorCodes.toException]]
 * directly to add the request id.
 */
trait HandleTypeSafeErrorCodes extends WSClient {

  override protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw HandleTypeSafeErrorCodes.toException(httpCode, message)
}

object HandleTypeSafeErrorCodes {

  def toException(
    httpCode: Int,
    body: String,
    requestId: Option[String] = None
  ): TypeSafeScalaClientException = {
    val json = Try(Json.parse(body)).toOption
    val kind = json.flatMap(errorType)
    val errorMessage =
      s"Code ${httpCode} : ${json.flatMap(extractMessage).getOrElse(body)}" +
        requestId.fold("")(id => s" [request $id]")

    httpCode match {
      case 400 if kind.contains("max_tokens_exceeded") =>
        new TypeSafeScalaTokenCountExceededException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 400 if kind.isDefined =>
        new TypeSafeScalaApiUsageException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 400 | 422 =>
        new TypeSafeScalaInvalidRequestException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId,
          violations = json.map(violations).getOrElse(Nil)
        )
      case 401 | 403 =>
        new TypeSafeScalaUnauthorizedException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 404 | 405 =>
        new TypeSafeScalaNotFoundException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 408 =>
        new TypeSafeScalaClientTimeoutException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 429 =>
        new TypeSafeScalaRateLimitException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case 503 | 529 =>
        new TypeSafeScalaEngineOverloadedException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case code if code >= 500 =>
        new TypeSafeScalaServerErrorException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
      case _ =>
        new TypeSafeScalaClientException(
          errorMessage,
          httpCode = Some(httpCode),
          errorType = kind,
          requestId = requestId
        )
    }
  }

  /**
   * The human-readable part of an error body - `{"detail": {"message": ...}}` for auth and
   * usage errors, `{"detail": "..."}` for question-shape errors, a 422 validation list
   * rendered as `state: Field required; questions.q.criteria: Field required`, `{"detail":
   * {"error_type": ...}}` when that is all there is (`max_tokens_exceeded`), plus the generic
   * `error` / `message` shapes - or the body itself when it is none of those.
   */
  def extractMessage(body: String): String =
    Try(Json.parse(body)).toOption.flatMap(extractMessage).getOrElse(body)

  /** `detail.error_type` of a TypeSafe error body, e.g. `max_tokens_exceeded`. */
  def errorType(body: String): Option[String] =
    Try(Json.parse(body)).toOption.flatMap(errorType)

  private def errorType(json: JsValue): Option[String] =
    (json \ "detail" \ "error_type").asOpt[String]

  private def extractMessage(json: JsValue): Option[String] =
    json match {
      case JsString(text) => Some(text).filter(_.nonEmpty)

      case obj: JsObject =>
        val error = obj \ "error"
        val message = obj \ "message"
        val detail = obj \ "detail"

        error
          .asOpt[String]
          .orElse((error \ "message").asOpt[String])
          .orElse(message.asOpt[String])
          .orElse(detail.asOpt[String])
          .orElse((detail \ "message").asOpt[String])
          .orElse(Some(violations(json)).filter(_.nonEmpty).map(render))
          .orElse((detail \ "error_type").asOpt[String])

      case _ => None
    }

  /** The entries of a 422 validation list, paths without the `body` prefix. */
  private def violations(json: JsValue): Seq[TypeSafeViolation] =
    (json \ "detail").asOpt[JsArray].map(_.value.toSeq).getOrElse(Nil).flatMap { entry =>
      (entry \ "msg").asOpt[String].map { msg =>
        val path = (entry \ "loc")
          .asOpt[Seq[JsValue]]
          .getOrElse(Nil)
          .collect {
            case JsString(item) if item != "body"   => item
            case other: play.api.libs.json.JsNumber => other.value.toString
          }
          .mkString(".")
        TypeSafeViolation(path, msg)
      }
    }

  private def render(violations: Seq[TypeSafeViolation]): String =
    violations.map { v =>
      if (v.path.nonEmpty) s"${v.path}: ${v.message}" else v.message
    }.mkString("; ")
}
