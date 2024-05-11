package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.service.AnthropicWSRequestHelper.AnthropicBetaHeader
import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaEngineOverloadedException, OpenAIScalaRateLimitException, OpenAIScalaServerErrorException, OpenAIScalaTokenCountExceededException, OpenAIScalaUnauthorizedException}
import io.cequence.openaiscala.service.ws.WSRequestExtHelper
import play.api.libs.json.{JsObject, JsValue}

import scala.concurrent.Future

object AnthropicWSRequestHelper {
  val AnthropicBetaHeader = "anthropic-beta"
}

trait AnthropicWSRequestHelper extends WSRequestExtHelper {

  // TODO: introduce Anthropic error model
  override protected def handleErrorCodes(
                                           httpCode: Int,
                                           message: String
                                         ): Nothing = {
    val errorMessage = s"Code ${httpCode} : ${message}"
    httpCode match {
      case 401 => throw new OpenAIScalaUnauthorizedException(errorMessage)
      case 429 => throw new OpenAIScalaRateLimitException(errorMessage)
      case 500 => throw new OpenAIScalaServerErrorException(errorMessage)
      case 503 => throw new OpenAIScalaEngineOverloadedException(errorMessage)
      case 400 =>
        if (
          message.contains("Please reduce your prompt; or completion length") ||
            message.contains("Please reduce the length of the messages")
        )
          throw new OpenAIScalaTokenCountExceededException(errorMessage)
        else
          throw new OpenAIScalaClientException(errorMessage)

      case _ => throw new OpenAIScalaClientException(errorMessage)
    }
  }

  protected def execBetaPOSTWithStatus(
                                    endPoint: PEP,
                                    endPointParam: Option[String] = None,
                                    params: Seq[(PT, Option[Any])] = Nil,
                                    bodyParams: Seq[(PT, Option[JsValue])] = Nil,
                                  ): Future[JsValue] = {
    execPOSTWithStatusAndHeaders(
      endPoint,
      endPointParam,
      params,
      bodyParams,
      headers = authHeaders ++ Seq(AnthropicBetaHeader -> "tools-2024-04-04")
    ).map(handleErrorResponse)
  }

}
