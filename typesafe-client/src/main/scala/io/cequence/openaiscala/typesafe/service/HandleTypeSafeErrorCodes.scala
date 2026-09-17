package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala._
import io.cequence.wsclient.service.WSClient
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import scala.util.Try

/**
 * Maps the TypeSafe API's HTTP errors onto the shared exception hierarchy: 401/403 to
 * [[OpenAIScalaUnauthorizedException]], 429 to [[OpenAIScalaRateLimitException]], 529
 * (TypeSafe's "Overloaded") and 503 to [[OpenAIScalaEngineOverloadedException]], any other 5xx
 * to [[OpenAIScalaServerErrorException]] - all of them [[Retryable]] except the first - a 400
 * `max_tokens_exceeded` (the ~32k-token input limit) to
 * [[OpenAIScalaTokenCountExceededException]], and other 400/404/422 to a plain
 * [[OpenAIScalaClientException]]. The body's `detail` is unpacked into the message the way the
 * official SDK does it.
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
    message: String
  ): OpenAIScalaClientException = {
    val errorMessage = s"Code ${httpCode} : ${extractMessage(message)}"

    httpCode match {
      // the state + questions exceed the ~32k-token input limit: the body is only
      // {"detail":{"error_type":"max_tokens_exceeded"}}
      case 400 if errorType(message).contains("max_tokens_exceeded") =>
        new OpenAIScalaTokenCountExceededException(errorMessage)
      case 401 | 403           => new OpenAIScalaUnauthorizedException(errorMessage)
      case 408                 => new OpenAIScalaClientTimeoutException(errorMessage)
      case 429                 => new OpenAIScalaRateLimitException(errorMessage)
      case 503 | 529           => new OpenAIScalaEngineOverloadedException(errorMessage)
      case code if code >= 500 => new OpenAIScalaServerErrorException(errorMessage)
      case _                   => new OpenAIScalaClientException(errorMessage)
    }
  }

  /**
   * The human-readable part of an error body - `{"detail": {"message": ...}}` for auth errors,
   * `{"detail": [{"loc": ["body", "state"], "msg": "Field required"}]}` for 422 validation
   * errors (rendered as `state: Field required`), plus the generic `error` / `message` shapes
   * \- or the body itself when it is none of those.
   */
  def extractMessage(body: String): String =
    Try(Json.parse(body)).toOption.flatMap(extractMessage).getOrElse(body)

  /** `detail.error_type` of a TypeSafe error body, e.g. `max_tokens_exceeded`. */
  def errorType(body: String): Option[String] =
    Try(Json.parse(body)).toOption.flatMap(json =>
      (json \ "detail" \ "error_type").asOpt[String]
    )

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
          .orElse(detail.asOpt[JsArray].flatMap(validationErrors))
          // some errors carry only a type: {"detail":{"error_type":"max_tokens_exceeded"}}
          .orElse((detail \ "error_type").asOpt[String])

      case _ => None
    }

  private def validationErrors(entries: JsArray): Option[String] = {
    val parts = entries.value.toSeq.flatMap { entry =>
      (entry \ "msg").asOpt[String].map { msg =>
        val path = (entry \ "loc")
          .asOpt[Seq[JsValue]]
          .getOrElse(Nil)
          .collect {
            case JsString(item) if item != "body"   => item
            case other: play.api.libs.json.JsNumber => other.value.toString
          }
          .mkString(".")

        if (path.nonEmpty) s"$path: $msg" else msg
      }
    }

    Some(parts.mkString("; ")).filter(_.nonEmpty)
  }
}
