package io.cequence.openaiscala.service

import akka.actor.Scheduler
import io.cequence.jsonrepair.JsonRepair
import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaFormat
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{RetryHelpers, Retryable}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionBatchTypedResultItem,
  ChatRole,
  TextContent,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsObject, JsValue, Json}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import com.fasterxml.jackson.core.JsonProcessingException
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.JsonSchema.JsonSchemaOrMap
import io.cequence.wsclient.JsonUtil

object OpenAIChatCompletionExtra extends OpenAIServiceConsts with HasOpenAIConfig {

  protected val logger: Logger =
    LoggerFactory.getLogger(this.getClass.getSimpleName.stripSuffix("$"))

  private val defaultMaxRetries = 5

  implicit class OpenAIChatCompletionImplicits(
    openAIChatCompletionService: OpenAIChatCompletionService
  ) extends RetryHelpers {

    def createChatCompletionWithFailover(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String],
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      failureMessage: String,
      filterModels: Option[Seq[String] => Future[Seq[String]]] = None
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[ChatCompletionResponse] =
      createChatCompletionWithFailoverSettings(
        messages,
        settings,
        failoverModels.map(model => settings.copy(model = model)),
        maxRetries,
        retryOnAnyError,
        failureMessage,
        filterModels
      )

    def createChatCompletionWithFailoverSettings(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverSettings: Seq[CreateChatCompletionSettings],
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      failureMessage: String,
      filterModels: Option[Seq[String] => Future[Seq[String]]] = None
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[ChatCompletionResponse] = {
      val allSettingsInOrder = settings +: failoverSettings

      for {
        filteredSettings <- filterModels match {
          case Some(filter) =>
            val allModels = allSettingsInOrder.map(_.model)
            filter(allModels).map { filteredModelNames =>
              allSettingsInOrder.filter(s => filteredModelNames.contains(s.model))
            }
          case None =>
            Future.successful(allSettingsInOrder)
        }

        _ <-
          if (filteredSettings.isEmpty)
            Future.failed(
              new OpenAIScalaClientException(
                "At least one model/settings must remain after filtering."
              )
            )
          else Future.successful(())

        response <- {
          implicit val retrySettings: RetrySettings =
            RetrySettings(maxRetries = maxRetries.getOrElse(0))

          (openAIChatCompletionService
            .createChatCompletion(messages, _))
            .retryOnFailureOrFailover(
              // model is used only for logging
              normalAndFailoverInputsAndMessages =
                filteredSettings.map(settings => (settings, settings.model)),
              failureMessage = Some(failureMessage),
              log = Some(logger.warn),
              isRetryable = isRetryable(retryOnAnyError),
              includeExceptionMessage = true
            )
        }
      } yield response
    }

    /**
     * Creates a chat completion that returns a JSON-parseable response and converts it to the
     * specified type T.
     *
     * This function handles:
     *   - Proper JSON schema formatting based on model capabilities
     *   - Automatic failover to alternative models if specified
     *   - Retry logic for transient errors
     *   - JSON response parsing and validation
     *   - Logging of operation progress and timing
     *
     * **Important:** pass an explicit list of models that support JSON schema if the default
     * list is not sufficient or use `enforceJsonSchemaMode` if you are sure that the model
     * supports it.
     *
     * @tparam T
     *   The target type to deserialize the JSON response into (must have an implicit Format)
     * @param messages
     *   The chat messages (prompts) to send to the model
     * @param settings
     *   Chat completion settings including model, temperature, etc.
     * @param failoverModels
     *   Alternative models to try if the primary model fails
     * @param maxRetries
     *   Maximum number of retry attempts (defaults to 5)
     * @param retryOnAnyError
     *   If true, retry on any error; if false, only on errors marked as Retryable. Default is
     *   false.
     * @param taskNameForLogging
     *   Optional name to identify this task in logs
     * @param jsonSchemaModels
     *   Models that support JSON schema mode (important to specify if using non-default
     *   models)
     * @param enforceJsonSchemaMode
     *   If true, use OpenAI's native JSON schema mode for compatible models
     * @param parseJson
     *   Custom JSON parsing function (defaults to standard parser with error handling)
     * @return
     *   Future containing the parsed response of type T
     */
    def createChatCompletionWithJSON[T: Format](
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String] = Nil,
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      taskNameForLogging: Option[String] = None,
      jsonSchemaModels: Seq[String] = Nil,
      enforceJsonSchemaMode: Boolean = false,
      parseJson: String => JsValue = defaultParseJsonOrRepair,
      filterModels: Option[Seq[String] => Future[Seq[String]]] = None
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] =
      createChatCompletionWithJSONFullResponse[T](
        messages,
        settings,
        failoverModels,
        maxRetries,
        retryOnAnyError,
        taskNameForLogging,
        jsonSchemaModels,
        enforceJsonSchemaMode,
        parseJson,
        filterModels
      ).map(_._1)

    /**
     * Creates a chat completion that returns a JSON-parseable response, converts it to the
     * specified type T, and also returns the full [[ChatCompletionResponse]].
     *
     * Same as [[createChatCompletionWithJSON]] but returns a tuple of `(T,
     * ChatCompletionResponse)` so callers can inspect usage, finish reason, model, and other
     * response metadata.
     *
     * @return
     *   Future containing a tuple of the parsed response of type T and the raw
     *   ChatCompletionResponse
     */
    def createChatCompletionWithJSONFullResponse[T: Format](
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String] = Nil,
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      taskNameForLogging: Option[String] = None,
      jsonSchemaModels: Seq[String] = Nil,
      enforceJsonSchemaMode: Boolean = false,
      parseJson: String => JsValue = defaultParseJsonOrRepair,
      filterModels: Option[Seq[String] => Future[Seq[String]]] = None
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[(T, ChatCompletionResponse)] = {
      val start = new java.util.Date()

      val taskNameForLoggingFinal = taskNameForLogging.getOrElse("JSON-based chat-completion")

      val (messagesFinal, settingsFinal) = if (settings.jsonSchema.isDefined) {
        handleOutputJsonSchema(
          messages,
          settings,
          taskNameForLoggingFinal,
          jsonSchemaModels,
          enforceJsonSchemaMode
        )
      } else {
        (messages, settings)
      }

      openAIChatCompletionService
        .createChatCompletionWithFailover(
          messagesFinal,
          settingsFinal,
          failoverModels,
          maxRetries,
          retryOnAnyError,
          failureMessage = s"${taskNameForLoggingFinal.capitalize} failed.",
          filterModels
        )
        .flatMap { response =>
          val outcome = parseJsonResponse[T](response, taskNameForLoggingFinal, parseJson)

          logger.debug(
            s"${taskNameForLoggingFinal.capitalize} finished in " + (new java.util.Date().getTime - start.getTime) + " ms."
          )

          outcome match {
            case Right(result) => Future.successful(result)
            case Left(errorMessage) =>
              Future.failed(new OpenAIScalaClientException(errorMessage))
          }
        }
    }

    private def isRetryable(
      retryOnAnyError: Boolean
    ): Throwable => Boolean =
      if (retryOnAnyError) { _ =>
        true
      } else {
        case Retryable(_) => true
        case _            => false
      }
  }

  implicit class OpenAIChatCompletionBatchImplicits(
    openAIChatCompletionService: OpenAIChatCompletionService
      with OpenAIChatCompletionBatchService
  ) {

    /**
     * Convenience for small/interactive batches: runs the whole chat-completion batch flow in
     * one call - submits the requests via
     * [[OpenAIChatCompletionBatchService.createChatCompletionBatch]], polls
     * [[OpenAIChatCompletionBatchService.getChatCompletionBatch]] until the batch is done, and
     * returns the results. Works with every service/adapter implementing the batch endpoints
     * (OpenAI, Anthropic, Gemini, Vertex AI).
     *
     * '''For large production batches (thousands of requests), prefer the split flow''' -
     * batches take up to 24h and blocking a process on them is fragile. Submit with
     * `createChatCompletionBatch` (returns a durable, provider-scoped batch id), persist the
     * id, and later - typically from a different process - check `getChatCompletionBatch(id)`
     * and fetch `retrieveChatCompletionBatchResults(id)` once done. All four providers resolve
     * the id statelessly, so nothing but the id needs to survive between submission and
     * retrieval.
     *
     * @param requests
     *   Requests to batch, each with a unique `customId` and its messages.
     * @param settings
     *   Chat-completion settings (model, ...) shared by all requests in the batch.
     * @param pollingInterval
     *   How often to poll the batch status (default 30 seconds).
     * @param timeout
     *   Optional max wait; on expiry the future fails (the batch keeps processing - its id is
     *   included in the error message so it can be picked up later).
     * @param deleteBatchAfterUse
     *   Whether to delete the batch - including all the files it created or staged - once the
     *   results are retrieved (default false). On OpenAI the batch's input/output/error files
     *   are deleted; the batch object itself remains listed (no delete endpoint).
     * @return
     *   the batch results - one item per request, each carrying either a response or an error
     */
    def createChatCompletionBatchAndWaitForResults(
      requests: Seq[ChatCompletionBatchRequest],
      settings: CreateChatCompletionSettings,
      pollingInterval: FiniteDuration = 30.seconds,
      timeout: Option[FiniteDuration] = None,
      deleteBatchAfterUse: Boolean = false
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[Seq[ChatCompletionBatchResultItem]] = {
      val deadline = timeout.map(t => System.currentTimeMillis() + t.toMillis)
      val model = settings.model

      def pollUntilDone(batchId: String): Future[ChatCompletionBatchInfo] =
        openAIChatCompletionService.getChatCompletionBatch(batchId, model).flatMap { info =>
          if (info.isDone)
            Future.successful(info)
          else if (deadline.exists(System.currentTimeMillis() > _))
            Future.failed(
              new OpenAIScalaClientException(
                s"Chat-completion batch '$batchId' did not finish within the requested timeout of ${timeout.get}. " +
                  "The batch keeps processing - poll getChatCompletionBatch with this id to pick it up later."
              )
            )
          else {
            logger.debug(
              s"Chat-completion batch '$batchId' still in progress (${info.providerStatus}) - polling again in $pollingInterval."
            )
            akka.pattern.after(pollingInterval, scheduler)(pollUntilDone(batchId))
          }
        }

      def cleanup(batchId: String): Future[Unit] =
        if (deleteBatchAfterUse)
          openAIChatCompletionService.deleteChatCompletionBatch(batchId, model).recover {
            case e =>
              logger.warn(
                s"Failed to delete the chat-completion batch '$batchId': ${e.getMessage}"
              )
          }
        else
          Future.successful(())

      for {
        batch <- openAIChatCompletionService.createChatCompletionBatch(requests, settings)

        _ = logger.debug(
          s"Chat-completion batch '${batch.id}' with ${requests.size} request(s) submitted."
        )

        finishedBatch <- pollUntilDone(batch.id)

        results <- finishedBatch.status match {
          case ChatCompletionBatchStatus.Completed | ChatCompletionBatchStatus.Cancelled =>
            // cancelled batches may still contain partial results
            openAIChatCompletionService
              .retrieveChatCompletionBatchResults(batch.id, model)
              .transformWith(result => cleanup(batch.id).transform(_ => result))

          case failedStatus =>
            Future.failed(
              new OpenAIScalaClientException(
                s"Chat-completion batch '${batch.id}' finished with the status '$failedStatus' (provider status: '${finishedBatch.providerStatus}')."
              )
            )
        }
      } yield results
    }

    /**
     * Typed sibling of [[createChatCompletionBatchAndWaitForResults]] - the batch counterpart
     * of `createChatCompletionWithJSONFullResponse`: runs the whole batch flow and JSON-parses
     * each response into `T`, returning one typed item per request '''in request order'''
     * (provider batch results are unordered - re-association by `customId` happens here).
     *
     * JSON schema handling mirrors the synchronous path: when `settings.jsonSchema` is
     * defined, [[handleOutputJsonSchema]] is applied to '''each''' request's messages (the
     * prompt-appendix fallback for non-json-schema models is per-message) and once to the
     * shared settings (that half depends only on the model, so it is identical across
     * requests).
     *
     * Per-item outcomes never fail the whole future: a provider-side error passes through as
     * `Left`, an unparseable response maps to `Left(code = "response_parse_error")`, and a
     * request whose `customId` is absent from the results maps to `Left(code =
     * "missing_result")`. The future itself fails only for batch-level errors (submit failure,
     * terminal `Failed`/`Expired` status, or `timeout` - the timeout error message carries the
     * batch id so it can be picked up later).
     *
     * @param requests
     *   Requests to batch, each with a unique `customId` and its messages.
     * @param settings
     *   Chat-completion settings (model, jsonSchema, ...) shared by all requests.
     */
    def createChatCompletionBatchWithJSON[T: Format](
      requests: Seq[ChatCompletionBatchRequest],
      settings: CreateChatCompletionSettings,
      pollingInterval: FiniteDuration = 30.seconds,
      timeout: Option[FiniteDuration] = None,
      deleteBatchAfterUse: Boolean = false,
      taskNameForLogging: Option[String] = None,
      jsonSchemaModels: Seq[String] = Nil,
      enforceJsonSchemaMode: Boolean = false,
      parseJson: String => JsValue = defaultParseJsonOrRepair
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[Seq[ChatCompletionBatchTypedResultItem[T]]] = {
      require(
        requests.map(_.customId).distinct.size == requests.size,
        "Batch request custom ids must be unique."
      )

      val taskNameForLoggingFinal =
        taskNameForLogging.getOrElse("JSON-based chat-completion batch")

      def parseItem(
        customId: String,
        response: ChatCompletionResponse
      ): ChatCompletionBatchTypedResultItem[T] = {
        val outcome =
          try {
            parseJsonResponse[T](
              response,
              taskNameForLoggingFinal,
              parseJson,
              itemLabelForLogging = Some(customId)
            ).left.map(errorMessage =>
              ChatCompletionBatchError(errorMessage, code = Some("response_parse_error"))
            )
          } catch {
            case e: Exception =>
              Left(
                ChatCompletionBatchError(
                  s"Failed to parse the response as JSON: ${e.getMessage}",
                  code = Some("response_parse_error")
                )
              )
          }

        ChatCompletionBatchTypedResultItem(customId, outcome)
      }

      if (requests.isEmpty)
        Future.successful(Nil)
      else {
        // mirror the sync JSON path: adapt each request's messages and the shared settings
        val (requestsFinal, settingsFinal) = if (settings.jsonSchema.isDefined) {
          val adapted = requests.map { request =>
            val (newMessages, newSettings) = handleOutputJsonSchema(
              request.messages,
              settings,
              taskNameForLoggingFinal,
              jsonSchemaModels,
              enforceJsonSchemaMode
            )
            (request.copy(messages = newMessages), newSettings)
          }
          // the settings transformation depends only on the model, hence identical for all
          (adapted.map(_._1), adapted.head._2)
        } else {
          (requests, settings)
        }

        createChatCompletionBatchAndWaitForResults(
          requestsFinal,
          settingsFinal,
          pollingInterval,
          timeout,
          deleteBatchAfterUse
        ).map { items =>
          val itemsByCustomId = items.map(item => item.customId -> item).toMap

          // emit results in request order; a request with no result gets a typed error
          requests.map { request =>
            itemsByCustomId.get(request.customId) match {
              case Some(item) =>
                item.result match {
                  case Right(response) => parseItem(request.customId, response)
                  case Left(error) =>
                    ChatCompletionBatchTypedResultItem[T](request.customId, Left(error))
                }

              case None =>
                ChatCompletionBatchTypedResultItem[T](
                  request.customId,
                  Left(
                    ChatCompletionBatchError(
                      s"No result returned for the custom id '${request.customId}'.",
                      code = Some("missing_result")
                    )
                  )
                )
            }
          }
        }
      }
    }
  }

  private def getJsonSchemaModelsFromConfig(): Seq[String] = {
    import scala.collection.JavaConverters._
    val configPath = s"$configPrefix.models-supporting-json-schema"
    if (clientConfig.hasPath(configPath)) {
      clientConfig.getStringList(configPath).asScala.toSeq
    } else {
      Nil
    }
  }

  /**
   * Shared JSON tail of the typed sync
   * ([[OpenAIChatCompletionImplicits.createChatCompletionWithJSONFullResponse]]) and batch
   * ([[OpenAIChatCompletionBatchImplicits.createChatCompletionBatchWithJSON]]) paths: cleans
   * up the response content, parses it, logs an empty-object diagnostic (incl. usage), and
   * converts it to `T`. Exceptions thrown by `parseJson` propagate to the caller - the sync
   * path lets them fail the future, the batch path maps them to a per-item error.
   */
  private def parseJsonResponse[T: Format](
    response: ChatCompletionResponse,
    taskNameForLogging: String,
    parseJson: String => JsValue,
    itemLabelForLogging: Option[String] = None
  ): Either[String, (T, ChatCompletionResponse)] = {
    val contentJson = cleanupJsonContent(response.contentHead)
    val json = parseJson(contentJson)

    // logging only block
    json match {
      case obj: JsObject if obj.fields.isEmpty =>
        val stopReason = response.choices.headOption.flatMap(_.finish_reason).getOrElse("N/A")
        val usageInfo = response.usage.map { u =>
          val reasoning = u.completion_tokens_details.flatMap(_.reasoning_tokens).getOrElse(0)
          s"input: ${u.prompt_tokens}, output: ${u.completion_tokens.getOrElse(0)}, reasoning: $reasoning"
        }.getOrElse("N/A")
        val itemPart =
          itemLabelForLogging.map(label => s" for the request '$label'").getOrElse("")

        logger.error(
          s"${taskNameForLogging.capitalize} returned an empty JSON object$itemPart. Stop reason: $stopReason, model: ${response.model}, usage: [$usageInfo]."
        )
      case _ =>
    }

    json
      .asOpt[T]
      .map(value => Right((value, response)))
      .getOrElse(
        Left(s"Failed to parse JSON response into the expected type. Response: $contentJson")
      )
  }

  def cleanupJsonContent(content: String): String = {
    val trimmed = content.trim.stripPrefix("```json").stripSuffix("```").trim
    trimmed.dropWhile(char => char != '{' && char != '[')
  }

  def defaultParseJsonOrRepair(
    jsonString: String
  ): JsValue = try {
    Json.parse(jsonString)
  } catch {
    case e: JsonProcessingException =>
      logger.error(
        s"Failed to parse JSON response normally; attempting to repair it now. Error: ${e.getMessage()}."
      )
      val repairedJsonString = JsonRepair.repair(jsonString)
      Json.parse(repairedJsonString)
  }

  def handleOutputJsonSchema(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    taskNameForLogging: String,
    jsonSchemaModels: Seq[String] = Nil,
    enforceJsonSchemaMode: Boolean = false
  ): (Seq[BaseMessage], CreateChatCompletionSettings) = {
    // Use explicitly provided JSON schema models if available,
    // otherwise fall back to models configured in openai-scala-client.conf
    val jsonSchemaModelsFinal =
      if (jsonSchemaModels.nonEmpty)
        jsonSchemaModels
      else
        getJsonSchemaModelsFromConfig()

    val jsonSchemaDef = settings.jsonSchema.getOrElse(
      throw new IllegalArgumentException("JSON schema is not defined but expected.")
    )
    val jsonSchemaJson = Json.toJson(jsonSchemaDef.structure)
    val jsonSchemaString = Json.prettyPrint(jsonSchemaJson)

    val (settingsFinal, addJsonToPrompt) = {
      // to be more robust we also match models with a suffix
      if (
        enforceJsonSchemaMode ||
        jsonSchemaModelsFinal.exists(model =>
          settings.model.equals(model) || settings.model.endsWith("-" + model)
        )
      ) {
        logger.debug(
          s"Using OpenAI json schema mode for ${taskNameForLogging} and the model '${settings.model}' - name: ${jsonSchemaDef.name}, strict: ${jsonSchemaDef.strict}, structure:\n${jsonSchemaString}"
        )

        (
          settings.copy(
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema)
          ),
          false
        )
      } else {
        // otherwise we failover to json object format and pass json schema to the user prompt

        logger.debug(
          s"Using JSON object mode for ${taskNameForLogging} and the model '${settings.model}'. Also passing a JSON schema as part of a user prompt."
        )

        (
          settings.copy(
            response_format_type = Some(ChatCompletionResponseFormatType.json_object),
            jsonSchema = None
          ),
          true
        )
      }
    }

    val messagesFinal = if (addJsonToPrompt) {
      if (messages.nonEmpty && messages.last.role == ChatRole.User) {
        val outputJSONFormatAppendix =
          s"""
             |
             |<output_json_schema>
             |${jsonSchemaString}
             |</output_json_schema>""".stripMargin

        val newUserMessage = messages.last match {
          case x: UserMessage =>
            x.copy(
              content = x.content + outputJSONFormatAppendix
            )
          case x: UserSeqMessage =>
            // Append the schema as a new TextContent block so VLM messages
            // (UserSeqMessage with FileContent / ImageURLContent attached) still work.
            x.copy(
              content = x.content :+ TextContent(outputJSONFormatAppendix)
            )
          case _ => throw new IllegalArgumentException("Invalid message type")
        }

        logger.debug(s"Appended a JSON schema to a message:\n${newUserMessage match {
            case m: UserMessage    => m.content
            case m: UserSeqMessage => m.content.toString
            case other             => other.toString
          }}")

        messages.dropRight(1) :+ newUserMessage
      } else {
        val outputJSONFormatAppendix =
          s"""<output_json_schema>
             |${jsonSchemaString}
             |</output_json_schema>""".stripMargin

        logger.debug(
          s"Appended a JSON schema to an empty message:\n${outputJSONFormatAppendix}"
        )

        // need to create a new user message
        messages :+ UserMessage(outputJSONFormatAppendix)
      }
    } else {
      messages
    }

    (messagesFinal, settingsFinal)
  }

  def toStrictSchema(jsonSchema: JsonSchemaOrMap): Map[String, Any] = {
    val schemaMap: Map[String, Any] = jsonSchema match {
      case Left(schema) =>
        val json = Json.toJson(schema).as[JsObject]
        JsonUtil.toValueMap(json)

      case Right(schema) => schema
    }

    // set "additionalProperties" -> false on "object" types if strict
    def addFlagAux(map: Map[String, Any]): Map[String, Any] = {
      val newMap = map.map { case (key, value) =>
        val unwrappedValue = value match {
          case Some(value) => value
          case other       => other
        }

        val newValue = unwrappedValue match {
          case obj: Map[String, Any] =>
            addFlagAux(obj)

          case other =>
            other
        }
        key -> newValue
      }

      if (Seq("object", Some("object")).contains(map.getOrElse("type", ""))) {
        newMap + ("additionalProperties" -> false)
      } else
        newMap
    }

    addFlagAux(schemaMap)
  }

  /**
   * Typed variant of [[toStrictSchema]]: forces `additionalProperties = Some(false)` on every
   * nested [[JsonSchema.Object]] (overrides any caller-supplied value, as OpenAI strict mode
   * requires).
   */
  def toStrictSchema(jsonSchema: JsonSchema): JsonSchema =
    JsonSchema.setAdditionalPropertiesToFalse(jsonSchema, overrideExisting = true)
}
