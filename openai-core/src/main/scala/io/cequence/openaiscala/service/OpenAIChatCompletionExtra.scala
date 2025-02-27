package io.cequence.openaiscala.service

import akka.actor.Scheduler
import com.fasterxml.jackson.core.JsonParseException
import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaFormat
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{OpenAIScalaClientException, RetryHelpers, Retryable}
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
     * Important: pass an explicit list of models that support JSON schema if the default list
     * is not sufficient!
     */
    def createChatCompletionWithJSON[T: Format](
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings,
      failoverModels: Seq[String] = Nil,
      maxRetries: Option[Int] = Some(defaultMaxRetries),
      retryOnAnyError: Boolean = false,
      taskNameForLogging: Option[String] = None,
      jsonSchemaModels: Seq[String] = defaultModelsSupportingJsonSchema,
      parseJson: String => JsValue = defaultParseJsonOrThrow
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
          jsonSchemaModels
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

    private def defaultParseJsonOrThrow(
      jsonString: String
    ) = try {
      Json.parse(jsonString)
    } catch {
      case e: JsonParseException =>
        val message = "Failed to parse JSON response:\n" + jsonString
        logger.error(message)
        throw new OpenAIScalaClientException(message, e)
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
    ModelId.o3_mini,
    ModelId.o3_mini_2025_01_31,
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
    NonOpenAIModelId.gemini_exp_1206
  )

  def handleOutputJsonSchema(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings,
    taskNameForLogging: String,
    jsonSchemaModels: Seq[String] = defaultModelsSupportingJsonSchema
  ): (Seq[BaseMessage], CreateChatCompletionSettings) = {
    val jsonSchemaDef = settings.jsonSchema.getOrElse(
      throw new IllegalArgumentException("JSON schema is not defined but expected.")
    )
    val jsonSchemaJson = Json.toJson(jsonSchemaDef.structure)
    val jsonSchemaString = Json.prettyPrint(jsonSchemaJson)

    val (settingsFinal, addJsonToPrompt) = {
      // to be more robust we also match models with a suffix
      if (
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
