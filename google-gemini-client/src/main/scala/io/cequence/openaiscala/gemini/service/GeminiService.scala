package io.cequence.openaiscala.gemini.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.gemini.domain.response.{
  GenerateContentResponse,
  ListCachedContentsResponse,
  ListModelsResponse
}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{CachedContent, Content, Expiration}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

trait GeminiService extends CloseableService with GeminiServiceConsts {

  /**
   * The Gemini API supports content generation with images, audio, code, tools, and more. For
   * details on each of these features, read on and check out the task-focused sample code, or
   * read the comprehensive guides.
   *
   * @param contents
   *   For single-turn queries, this is a single instance. For multi-turn queries like chat,
   *   this is a repeated field that contains the conversation history and the latest request.
   * @param settings
   * @return
   *   generate content response
   * @see
   *   <a href="https://ai.google.dev/api/generate-content">Gemini Docs</a>
   */
  def generateContent(
    contents: Seq[Content],
    settings: GenerateContentSettings = DefaultSettings.GenerateContent
  ): Future[GenerateContentResponse]

  /**
   * The Gemini API supports content generation with images, audio, code, tools, and more. For
   * details on each of these features, read on and check out the task-focused sample code, or
   * read the comprehensive guides with streamed response.
   *
   * @param contents
   *   For single-turn queries, this is a single instance. For multi-turn queries like chat,
   *   this is a repeated field that contains the conversation history and the latest request.
   * @param settings
   * @return
   *   generate content response
   * @see
   *   <a href="https://ai.google.dev/api/generate-content">Gemini Docs</a>
   */
  def generateContentStreamed(
    contents: Seq[Content],
    settings: GenerateContentSettings = DefaultSettings.GenerateContent
  ): Source[GenerateContentResponse, NotUsed]

  /**
   * Lists the Models available through the Gemini API.
   *
   * @param pageSize
   *   The maximum number of Models to return (per page). If unspecified, 50 models will be
   *   returned per page. This method returns at most 1000 models per page, even if you pass a
   *   larger pageSize.
   * @param pageToken
   *   A page token, received from a previous models.list call. Provide the pageToken returned
   *   by one request as an argument to the next request to retrieve the next page. When
   *   paginating, all other parameters provided to models.list must match the call that
   *   provided the page token.
   *
   * @return
   * @see
   *   <a href="https://ai.google.dev/api/list-models">Gemini Docs</a>
   */
  def listModels(
    pageSize: Option[Int] = None,
    pageToken: Option[String] = None
  ): Future[ListModelsResponse]

  /**
   * Creates CachedContent resource.
   *
   * @param cachedContent
   * @return
   *   If successful, the response body contains a newly created instance of CachedContent.
   *
   * @see
   *   <a href="https://ai.google.dev/api/caching#method:-cachedcontents.create">Gemini
   *   Docs</a>
   */
  def createCachedContent(
    cachedContent: CachedContent
  ): Future[CachedContent]

  /**
   * Updates CachedContent resource (only expiration is updatable).
   *
   * @param name
   *   Optional. Identifier. The resource name referring to the cached content. Format:
   *   cachedContents/{id} It takes the form cachedContents/{cachedcontent}.
   * @param expiration
   * @return
   *   If successful, the response body contains an updated instance of CachedContent.
   * @see
   *   <a href="https://ai.google.dev/api/caching#method:-cachedcontents.patch">Gemini Docs</a>
   */
  def updateCachedContent(
    name: String,
    expiration: Expiration
  ): Future[CachedContent]

  /**
   * Lists CachedContents.
   *
   * @param pageSize
   *   Optional. The maximum number of cached contents to return. The service may return fewer
   *   than this value. If unspecified, some default (under maximum) number of items will be
   *   returned. The maximum value is 1000; values above 1000 will be coerced to 1000.
   * @param pageToken
   *   Optional. A page token, received from a previous cachedContents.list call. Provide this
   *   to retrieve the subsequent page.
   *
   * When paginating, all other parameters provided to cachedContents.list must match the call
   * that provided the page token.
   * @return
   *
   * @see
   *   <a href="https://ai.google.dev/api/caching#method:-cachedcontents.list">Gemini Docs</a>
   */
  def listCachedContents(
    pageSize: Option[Int] = None,
    pageToken: Option[String] = None
  ): Future[ListCachedContentsResponse]

  /**
   * Reads CachedContent resource.
   *
   * @param pageSize
   * @param pageToken
   * @return
   *
   * @see
   *   <a href="https://ai.google.dev/api/caching#method:-cachedcontents.get">Gemini Docs</a>
   */
  def getCachedContent(
    name: String
  ): Future[CachedContent]

  /**
   * Deletes CachedContent resource.
   *
   * @param name
   * @return
   *
   * @see
   *   <a href="https://ai.google.dev/api/caching#method:-cachedcontents.delete">Gemini
   *   Docs</a>
   */
  def deleteCachedContent(
    name: String
  ): Future[Unit]
}
