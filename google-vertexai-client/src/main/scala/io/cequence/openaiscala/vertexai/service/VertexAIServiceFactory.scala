package io.cequence.openaiscala.vertexai.service

import com.google.cloud.vertexai.VertexAI
import io.cequence.openaiscala.EnvHelper
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.vertexai.service.impl.OpenAIVertexAIChatCompletionService

import scala.concurrent.ExecutionContext

object VertexAIServiceFactory extends EnvHelper {

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
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey)
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService =
    new OpenAIVertexAIChatCompletionService(
      VertexAIServiceFactory(projectId, location)
    )

  private def apply(
    projectId: String = getEnvValue(projectIdKey),
    location: String = getEnvValue(locationIdKey)
  ): VertexAI =
    new VertexAI(projectId, location)
}
