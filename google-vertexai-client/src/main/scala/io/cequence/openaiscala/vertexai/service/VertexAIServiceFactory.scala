package io.cequence.openaiscala.vertexai.service

import com.google.cloud.vertexai.VertexAI
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.vertexai.service.impl.OpenAIVertexAIChatCompletionService

import scala.concurrent.ExecutionContext

object VertexAIServiceFactory {

  private val projectIdKey = "VERTEXAI_PROJECT_ID"
  private val locationIdKey = "VERTEXAI_LOCATION"

  /**
   * Create a new instance of the [[OpenAIChatCompletionService]] wrapping the AnthropicService
   *
   * @param projectId
   * @param location
   * @param ec
   * @return
   */
  def asOpenAI(
    projectId: String = getEnvValueSafe(projectIdKey),
    location: String = getEnvValueSafe(locationIdKey)
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    new OpenAIVertexAIChatCompletionService(
      VertexAIServiceFactory(projectId, location)
    )

  private def apply(
    projectId: String = getEnvValueSafe(projectIdKey),
    location: String = getEnvValueSafe(locationIdKey)
  ): VertexAI =
    new VertexAI(projectId, location)

  private def getEnvValueSafe(key: String): String =
    Option(System.getenv(key)).getOrElse(
      throw new IllegalStateException(
        s"${key} environment variable expected but not set. Alternatively, you can pass the value explicitly to the factory method."
      )
    )
}
