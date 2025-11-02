package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  GetInputTokensCountSettings,
  InputItemsResponse,
  InputTokensCount,
  Inputs,
  Response,
  DeleteResponse => ResponsesAPIDeleteResponse
}
import io.cequence.openaiscala.domain.SortOrder

import scala.concurrent.Future

/**
 * Service interface for OpenAI Responses API endpoints.
 *
 * The Responses API provides a unified interface for creating and managing model responses
 * with support for various tools including file search, web search, and custom functions.
 *
 *   - Available Functions:
 *
 * '''Create Response'''
 *   - [[createModelResponse]] - Creates a new model response from inputs with support for
 *     tools
 *   - [[https://platform.openai.com/docs/api-reference/responses/create API Doc]]
 *
 * '''Retrieve Response'''
 *   - [[getModelResponse]] - Retrieves a model response by its ID
 *   - [[https://platform.openai.com/docs/api-reference/responses/get API Doc]]
 *
 * '''Delete Response'''
 *   - [[deleteModelResponse]] - Deletes a model response by its ID
 *   - [[https://platform.openai.com/docs/api-reference/responses/delete API Doc]]
 *
 * '''Cancel Response'''
 *   - [[cancelModelResponse]] - Cancels a background model response (only for responses
 *     created with background=true)
 *   - [[https://platform.openai.com/docs/api-reference/responses/cancel API Doc]]
 *
 * '''Input Token Counts'''
 *   - [[getModelResponseInputTokenCounts]] - Gets input token counts for a given input and
 *     settings without creating a response
 *   - [[https://platform.openai.com/docs/api-reference/responses/input-tokens API Doc]]
 *
 * '''List Input Items'''
 *   - [[listModelResponseInputItems]] - Lists input items for a model response with pagination
 *     support
 *   - [[https://platform.openai.com/docs/api-reference/responses/input-items API Doc]]
 *
 * @see
 *   <a href="https://platform.openai.com/docs/api-reference/responses">OpenAI Responses API
 *   Doc</a>
 */
trait OpenAIResponsesService extends OpenAIServiceConsts {

  /**
   * Creates a new model response from the provided inputs.
   *
   * @param inputs
   *   The inputs for the response. Can be a single prompt, messages, or an existing
   *   conversation.
   * @param settings
   *   Configuration settings for the model response, including model, tools, and other
   *   parameters.
   * @return
   *   A Future containing the Response object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/create">OpenAI Doc</a>
   */
  def createModelResponse(
    inputs: Inputs,
    settings: CreateModelResponseSettings = DefaultSettings.CreateModelResponse
  ): Future[Response]

  /**
   * Retrieves a model response by its ID.
   *
   * @param responseId
   *   The ID of the response to retrieve.
   * @param include
   *   Optional list of related objects to include in the response.
   * @return
   *   A Future containing the Response object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/get">OpenAI Doc</a>
   */
  def getModelResponse(
    responseId: String,
    include: Seq[String] = Nil
  ): Future[Response]

  /**
   * Deletes a model response by its ID.
   *
   * @param responseId
   *   The ID of the response to delete.
   * @return
   *   A Future containing the DeleteResponse object, indicating the result of the deletion.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/delete">OpenAI Doc</a>
   */
  def deleteModelResponse(
    responseId: String
  ): Future[ResponsesAPIDeleteResponse]

  /**
   * Cancels a model response with the given ID. Only responses created with the background
   * parameter set to true can be cancelled.
   *
   * @param responseId
   *   The ID of the response to cancel.
   * @return
   *   A Future containing the Response object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/cancel">OpenAI Doc</a>
   */
  def cancelModelResponse(
    responseId: String
  ): Future[Response]

  /**
   * Get input token counts for a model response.
   *
   * @param inputs
   *   The inputs for the response. Can be a single prompt, messages, or an existing
   *   conversation.
   * @param settings
   *   Configuration settings for getting input token counts.
   * @return
   *   A Future containing the InputTokensCount object with the token count.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/input-tokens">OpenAI
   *   Doc</a>
   */
  def getModelResponseInputTokenCounts(
    inputs: Inputs,
    settings: GetInputTokensCountSettings = DefaultSettings.CreateModelResponseInputTokensCount
  ): Future[InputTokensCount]

  /**
   * List input items for a model response.
   *
   * @param responseId
   *   The ID of the response to retrieve input items for.
   * @param after
   *   An item ID to list items after, used in pagination.
   * @param before
   *   An item ID to list items before, used in pagination.
   * @param include
   *   Additional fields to include in the response.
   * @param limit
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20.
   * @param order
   *   The order to return the input items in. Default is asc.
   * @return
   *   A list of input item objects.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/input-items">OpenAI
   *   Doc</a>
   */
  def listModelResponseInputItems(
    responseId: String,
    after: Option[String] = None,
    before: Option[String] = None,
    include: Seq[String] = Nil,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None
  ): Future[InputItemsResponse]
}
