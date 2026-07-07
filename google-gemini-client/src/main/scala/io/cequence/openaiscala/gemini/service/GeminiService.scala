package io.cequence.openaiscala.gemini.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.gemini.domain.response.{
  GenerateContentResponse,
  ListCachedContentsResponse,
  ListModelsResponse
}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{
  BatchRequestItem,
  CachedContent,
  Content,
  Expiration,
  GeminiFile,
  GenerateContentBatch,
  ListBatchesResponse
}

import java.io.File
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

  // -- Batches (Batch Mode) --

  /**
   * Creates a batch of `generateContent` requests for asynchronous processing (`POST
   * /v1beta/models/{model}:batchGenerateContent`) at 50% of standard cost with a 24h
   * turnaround target. Results are retained for 6 weeks and a batch expires after 48 hours in
   * a non-terminal state.
   *
   * Small batches are sent inline (limited to 20 MB total); when the payload exceeds the
   * inline limit - or [[useFileInput]] is set - the requests are automatically uploaded as a
   * JSONL file via the Files API (2 GB limit) and the batch reads from it, in which case the
   * results arrive as a downloadable file (`output.responsesFile`, see [[downloadFile]])
   * instead of inlined responses.
   *
   * @param displayName
   *   Human-readable name of the batch.
   * @param requests
   *   Requests, each with a unique key (for matching responses, which may come out of request
   *   order) and its contents.
   * @param settings
   *   Model plus generation settings applied to every request in the batch.
   * @param priority
   *   Optional priority - batches with a higher value are processed first (default 0).
   * @param useFileInput
   *   Force file-based input even for small batches.
   * @return
   *   the created batch (state `BATCH_STATE_PENDING`); poll with [[getBatch]] until terminal.
   * @see
   *   <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch Docs</a>
   */
  def createBatchGenerateContent(
    displayName: String,
    requests: Seq[BatchRequestItem],
    settings: GenerateContentSettings = DefaultSettings.GenerateContent,
    priority: Option[Long] = None,
    useFileInput: Boolean = false
  ): Future[GenerateContentBatch]

  /**
   * Retrieves a batch (`GET /v1beta/batches/{id}`). Poll until [[GenerateContentBatch.state]]
   * is terminal (`BATCH_STATE_SUCCEEDED`, `_FAILED`, `_CANCELLED`, or `_EXPIRED`); on success,
   * inline results are available at `output.inlinedResponses` (or `output.responsesFile` for
   * file-based input).
   *
   * @param name
   *   Batch resource name (`batches/{id}`) as returned on creation.
   * @see
   *   <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch Docs</a>
   */
  def getBatch(name: String): Future[GenerateContentBatch]

  /**
   * Lists batches (`GET /v1beta/batches`).
   *
   * @see
   *   <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch Docs</a>
   */
  def listBatches(
    pageSize: Option[Int] = None,
    pageToken: Option[String] = None
  ): Future[ListBatchesResponse]

  /**
   * Cancels a batch (`POST /v1beta/batches/{id}:cancel`).
   *
   * @param name
   *   Batch resource name (`batches/{id}`).
   * @see
   *   <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch Docs</a>
   */
  def cancelBatch(name: String): Future[Unit]

  /**
   * Deletes a batch (`DELETE /v1beta/batches/{id}`).
   *
   * @param name
   *   Batch resource name (`batches/{id}`).
   * @see
   *   <a href="https://ai.google.dev/gemini-api/docs/batch-api">Gemini Batch Docs</a>
   */
  def deleteBatch(name: String): Future[Unit]

  // -- Files --

  /**
   * Uploads a file via the Files API (`POST /upload/v1beta/files`, resumable protocol). Files
   * are retained for 48 hours; up to 2 GB per file and 20 GB per project.
   *
   * @see
   *   <a href="https://ai.google.dev/api/files">Gemini Files API Docs</a>
   */
  def uploadFile(
    file: File,
    displayName: Option[String] = None,
    mimeType: Option[String] = None
  ): Future[GeminiFile]

  /**
   * Retrieves a file's metadata (`GET /v1beta/files/{id}`).
   *
   * @param name
   *   File resource name (`files/{id}`).
   * @see
   *   <a href="https://ai.google.dev/api/files">Gemini Files API Docs</a>
   */
  def getFile(name: String): Future[GeminiFile]

  /**
   * Deletes a file (`DELETE /v1beta/files/{id}`).
   *
   * @param name
   *   File resource name (`files/{id}`).
   * @see
   *   <a href="https://ai.google.dev/api/files">Gemini Files API Docs</a>
   */
  def deleteFile(name: String): Future[Unit]

  /**
   * Downloads the content of a generated file, e.g. batch results (`GET
   * /download/v1beta/{name}:download?alt=media`).
   *
   * @param name
   *   File resource name (`files/{id}`).
   * @see
   *   <a href="https://ai.google.dev/api/files">Gemini Files API Docs</a>
   */
  def downloadFile(name: String): Future[String]
}
