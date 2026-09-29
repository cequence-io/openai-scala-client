package io.cequence.openaiscala.vertexai.service.impl

import com.google.api.gax.rpc.ApiException
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException,
  OpenAIScalaTokenCountExceededException,
  OpenAIScalaUnauthorizedException
}
import io.cequence.openaiscala.service.OpenAIErrorCodes
import io.cequence.wsclient.domain.{CequenceWSTimeoutException, CequenceWSUnknownHostException}
import io.grpc.{StatusException, StatusRuntimeException}
import play.api.libs.json.Json

import java.util.concurrent.{CompletionException, ExecutionException}
import scala.annotation.tailrec
import scala.util.Try

/**
 * Classifies Vertex AI failures onto the `OpenAIScala*` exceptions by their canonical status
 * (`RESOURCE_EXHAUSTED`, `UNAVAILABLE`, ...) - the same names on every transport: the SDK's
 * gax `ApiException` (gRPC or REST), a raw gRPC `Status(Runtime)Exception`, and the
 * `error.status` of a REST error body (batch prediction); ws-client transport timeouts /
 * unknown hosts too. Retryable: rate limit, overload, timeout and server errors (`INTERNAL`,
 * `UNKNOWN`, `DATA_LOSS`); never an invalid request or bad credentials.
 */
object VertexAIErrors {

  private val TokenCountExceededMessages = Seq(
    "exceeds the maximum number of tokens",
    "request payload size exceeds the limit"
  )

  private def isTokenCountExceeded(message: String): Boolean = {
    val lower = message.toLowerCase
    TokenCountExceededMessages.exists(lower.contains)
  }

  // the depth of a cause chain searched for a gax / gRPC exception
  private val MaxCauseDepth = 32

  /**
   * The `OpenAIScala*` exception for a canonical status name (the original as the cause), or
   * None for an unknown name.
   */
  def fromStatus(
    status: String,
    message: String,
    cause: Throwable
  ): Option[OpenAIScalaClientException] = {
    def build(make: (String, Throwable) => OpenAIScalaClientException) =
      Some(make(message, cause))

    status match {
      case "RESOURCE_EXHAUSTED" => build(new OpenAIScalaRateLimitException(_, _))
      case "UNAVAILABLE"        => build(new OpenAIScalaEngineOverloadedException(_, _))
      case "DEADLINE_EXCEEDED"  => build(new OpenAIScalaClientTimeoutException(_, _))
      case "INTERNAL" | "UNKNOWN" | "DATA_LOSS" =>
        build(new OpenAIScalaServerErrorException(_, _))
      case "UNAUTHENTICATED" | "PERMISSION_DENIED" =>
        build(new OpenAIScalaUnauthorizedException(_, _))
      case "INVALID_ARGUMENT" | "OUT_OF_RANGE" | "FAILED_PRECONDITION"
          if isTokenCountExceeded(message) =>
        build(new OpenAIScalaTokenCountExceededException(_, _))
      case "INVALID_ARGUMENT" | "OUT_OF_RANGE" | "FAILED_PRECONDITION" | "NOT_FOUND" |
          "ALREADY_EXISTS" | "ABORTED" | "CANCELLED" | "UNIMPLEMENTED" =>
        build(new OpenAIScalaClientException(_, _))
      case _ => None
    }
  }

  /**
   * An SDK / transport failure as an `OpenAIScala*` exception: finds the gax `ApiException` or
   * gRPC status exception anywhere in the cause chain (the SDK and the Java futures wrap them
   * in `CompletionException`, `ExecutionException`, `UncheckedExecutionException`, ...). An
   * `OpenAIScalaClientException` passes through unchanged; anything without a status is
   * returned with its `CompletionException` / `ExecutionException` wrappers removed.
   */
  def toOpenAIException(e: Throwable): Throwable =
    e match {
      case already: OpenAIScalaClientException => already
      // ws-client transport failures (the batch-prediction REST calls)
      case timeout: CequenceWSTimeoutException =>
        new OpenAIScalaClientTimeoutException(timeout.getMessage, timeout)
      case unknownHost: CequenceWSUnknownHostException =>
        new OpenAIScalaClientUnknownHostException(unknownHost.getMessage, unknownHost)
      case _ =>
        statusOf(e, MaxCauseDepth).flatMap { case (status, message, source) =>
          fromStatus(status, message, source)
        }.getOrElse(unwrapFutureWrappers(e))
    }

  @tailrec
  private def unwrapFutureWrappers(e: Throwable): Throwable =
    e match {
      case wrapper @ (_: CompletionException | _: ExecutionException)
          if wrapper.getCause != null =>
        unwrapFutureWrappers(wrapper.getCause)
      case _ => e
    }

  @tailrec
  private def statusOf(
    e: Throwable,
    depth: Int
  ): Option[(String, String, Throwable)] =
    e match {
      case null              => None
      case _ if depth <= 0   => None
      case api: ApiException => Some((api.getStatusCode.getCode.name, api.getMessage, api))
      case grpc: StatusRuntimeException =>
        Some((grpc.getStatus.getCode.name, grpc.getMessage, grpc))
      case grpc: StatusException => Some((grpc.getStatus.getCode.name, grpc.getMessage, grpc))
      // getCause is null (never the throwable itself) at the end of a chain
      case other => statusOf(other.getCause, depth - 1)
    }

  /**
   * A REST error (batch prediction): classified by the body's `error.status` when present
   * (`{"error": {"code": 429, "message": ..., "status": "RESOURCE_EXHAUSTED"}}`), else - after
   * the Vertex token-count messages - by the shared HTTP-code policy of `OpenAIErrorCodes`.
   * The message is `Code <httpCode> : <error.message or the body>`.
   */
  def fromHttp(
    httpCode: Int,
    body: String
  ): OpenAIScalaClientException = {
    val error = Try(Json.parse(body)).toOption.map(_ \ "error")
    val status = error.flatMap(e => (e \ "status").asOpt[String])
    val rawMessage = error.flatMap(e => (e \ "message").asOpt[String]).getOrElse(body.trim)
    val message = s"Code $httpCode : $rawMessage"

    status
      .flatMap(fromStatus(_, message, null))
      .getOrElse(
        if (httpCode == 400 && isTokenCountExceeded(rawMessage))
          new OpenAIScalaTokenCountExceededException(message)
        else
          OpenAIErrorCodes.toException(httpCode, rawMessage)
      )
  }
}
