package io.cequence.openaiscala.typesafe.service

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import scala.util.Try

/**
 * Classifies the TypeSafe API's HTTP errors into the [[TypeSafeScalaClientException]]
 * hierarchy (see its scaladoc for the status / body table) and unpacks the body's `detail`
 * into the message the way the official SDK does. `TypeSafeServiceImpl` calls [[toException]]
 * from its own error path so the request id can ride along (ws-client's `handleErrorCodes`
 * hook sees neither the headers nor the rich response, so it is not used).
 */
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

    // (message, httpCode, errorType, requestId) -> the exception of the right kind
    type Build =
      (String, Option[Int], Option[String], Option[String]) => TypeSafeScalaClientException

    val build: Build = httpCode match {
      case 400 if kind.contains("max_tokens_exceeded") =>
        new TypeSafeScalaTokenCountExceededException(_, null, _, _, _)
      case 400 if kind.isDefined => new TypeSafeScalaApiUsageException(_, null, _, _, _)
      case 400 | 422 =>
        val violations = json.map(HandleTypeSafeErrorCodes.violations).getOrElse(Nil)
        new TypeSafeScalaInvalidRequestException(_, null, _, _, _, violations)
      case 401 | 403           => new TypeSafeScalaUnauthorizedException(_, null, _, _, _)
      case 404 | 405           => new TypeSafeScalaNotFoundException(_, null, _, _, _)
      case 408                 => new TypeSafeScalaClientTimeoutException(_, null, _, _, _)
      case 429                 => new TypeSafeScalaRateLimitException(_, null, _, _, _)
      case 503 | 529           => new TypeSafeScalaEngineOverloadedException(_, null, _, _, _)
      case code if code >= 500 => new TypeSafeScalaServerErrorException(_, null, _, _, _)
      case _                   => new TypeSafeScalaClientException(_, null, _, _, _)
    }

    build(errorMessage, Some(httpCode), kind, requestId)
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
