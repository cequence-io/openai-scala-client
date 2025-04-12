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
  ChatRole,
  ModelId,
  NonOpenAIModelId,
  UserMessage
}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}
import com.fasterxml.jackson.core.JsonProcessingException

object OpenAIChatCompletionExtra {

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
      failureMessage: String
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[ChatCompletionResponse] = {
      val failoverSettings = failoverModels.map(model => settings.copy(model = model))
      val allSettingsInOrder = Seq(settings) ++ failoverSettings

      implicit val retrySettings: RetrySettings =
        RetrySettings(maxRetries = maxRetries.getOrElse(0))

      (openAIChatCompletionService
        .createChatCompletion(messages, _))
        .retryOnFailureOrFailover(
          // model is used only for logging
          normalAndFailoverInputsAndMessages =
            allSettingsInOrder.map(settings => (settings, settings.model)),
          failureMessage = Some(failureMessage),
          log = Some(logger.warn),
          isRetryable = isRetryable(retryOnAnyError)
        )
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
      jsonSchemaModels: Seq[String] = defaultModelsSupportingJsonSchema,
      enforceJsonSchemaMode: Boolean = false,
      parseJson: String => JsValue = defaultParseJsonOrRepair
    )(
      implicit ec: ExecutionContext,
      scheduler: Scheduler
    ): Future[T] = {
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
          failureMessage = s"${taskNameForLoggingFinal.capitalize} failed."
        )
        .map { response =>
          val content = response.contentHead
          val contentTrimmed = content.trim.stripPrefix("```json").stripSuffix("```").trim
          val contentJson = contentTrimmed.dropWhile(char => char != '{' && char != '[')
          val json = parseJson(contentJson)

          logger.debug(
            s"${taskNameForLoggingFinal.capitalize} finished in " + (new java.util.Date().getTime - start.getTime) + " ms."
          )

          json.as[T]
        }
    }

    private def defaultParseJsonOrRepair(
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

  private val defaultModelsSupportingJsonSchema = Seq(
    ModelId.gpt_4_5_preview,
    ModelId.gpt_4_5_preview_2025_02_27,
    ModelId.gpt_4o,
    ModelId.gpt_4o_2024_08_06,
    ModelId.gpt_4o_2024_11_20,
    ModelId.o1,
    ModelId.o1_2024_12_17,
    ModelId.o1_pro,
    ModelId.o1_pro_2025_03_19,
    ModelId.o3_mini,
    ModelId.o3_mini_high,
    ModelId.o3_mini_2025_01_31,
    NonOpenAIModelId.gemini_2_5_pro_preview_03_25,
    NonOpenAIModelId.gemini_2_5_pro_exp_03_25,
    NonOpenAIModelId.gemini_2_0_flash,
    NonOpenAIModelId.gemini_2_0_flash_001,
    NonOpenAIModelId.gemini_2_0_pro_exp_02_05,
    NonOpenAIModelId.gemini_2_0_pro_exp,
    NonOpenAIModelId.gemini_2_0_flash_001,
    NonOpenAIModelId.gemini_2_0_flash,
    NonOpenAIModelId.gemini_2_0_flash_exp,
    NonOpenAIModelId.gemini_1_5_flash_8b_exp_0924,
    NonOpenAIModelId.gemini_1_5_flash_8b_exp_0827,
    NonOpenAIModelId.gemini_1_5_flash_8b_latest,
    NonOpenAIModelId.gemini_1_5_flash_8b_001,
    NonOpenAIModelId.gemini_1_5_flash_8b,
    NonOpenAIModelId.gemini_1_5_flash_002,
    NonOpenAIModelId.gemini_1_5_flash,
    NonOpenAIModelId.gemini_1_5_flash_001,
    NonOpenAIModelId.gemini_1_5_flash_latest,
    NonOpenAIModelId.gemini_1_5_pro,
    NonOpenAIModelId.gemini_1_5_pro_002,
    NonOpenAIModelId.gemini_1_5_pro_001,
    NonOpenAIModelId.gemini_1_5_pro_latest,
    NonOpenAIModelId.gemini_exp_1206,
    NonOpenAIModelId.grok_2,
    NonOpenAIModelId.grok_2_1212,
    NonOpenAIModelId.grok_2_latest,
    NonOpenAIModelId.grok_3,
    NonOpenAIModelId.grok_3_beta,
    NonOpenAIModelId.grok_3_latest,
    NonOpenAIModelId.grok_3_fast,
    NonOpenAIModelId.grok_3_fast_beta,
    NonOpenAIModelId.grok_3_fast_latest,
    NonOpenAIModelId.grok_3_mini,
    NonOpenAIModelId.grok_3_mini_beta,
    NonOpenAIModelId.grok_3_mini_latest,
    NonOpenAIModelId.grok_3_mini_fast,
    NonOpenAIModelId.grok_3_mini_fast_beta,
    NonOpenAIModelId.grok_3_mini_fast_latest,
    // NonOpenAIModelId.cerebras_llama_4_scout_17b_16e_instruct
  )

  def handleOutputJsonSchema(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    taskNameForLogging: String,
    jsonSchemaModels: Seq[String] = defaultModelsSupportingJsonSchema,
    enforceJsonSchemaMode: Boolean = false
  ): (Seq[BaseMessage], CreateChatCompletionSettings) = {
    val jsonSchemaDef = settings.jsonSchema.getOrElse(
      throw new IllegalArgumentException("JSON schema is not defined but expected.")
    )
    val jsonSchemaJson = Json.toJson(jsonSchemaDef.structure)
    val jsonSchemaString = Json.prettyPrint(jsonSchemaJson)

    val (settingsFinal, addJsonToPrompt) = {
      // to be more robust we also match models with a suffix
      if (
        enforceJsonSchemaMode ||
        jsonSchemaModels.exists(model =>
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
          case _ => throw new IllegalArgumentException("Invalid message type")
        }

        logger.debug(s"Appended a JSON schema to a message:\n${newUserMessage.content}")

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
}
