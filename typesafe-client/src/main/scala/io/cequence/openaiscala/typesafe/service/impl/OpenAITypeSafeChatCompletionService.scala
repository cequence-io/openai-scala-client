package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps
import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaWrites
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.service.{OpenAIChatCompletionExtra, OpenAIChatCompletionService}
import io.cequence.openaiscala.typesafe.domain.SystemOneResponse
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.typesafe.service.{TypeSafeChatMapping, TypeSafeService}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

import java.{util => ju}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * System One behind the OpenAI chat-completion interface, for STRUCTURED OUTPUT only: the
 * request must use `response_format_type = json_schema` with a `jsonSchema`, the schema is
 * turned into questions ([[SchemaQuestions]]), the messages become the `state` (see
 * [[TypeSafeChatMapping]]: system messages as `instructions`, JSON user messages embedded as
 * JSON, several turns as a `conversation`) and the answers are folded into the assistant
 * message's content as a JSON document of that schema. This is what
 * `createChatCompletionWithJSON[T]` (and a `ModelGuardrail`) needs. For a model the JSON
 * helper does not list as json_schema-capable (`models-supporting-json-schema` /
 * `jsonSchemaModels`
 *   - e.g. a new dated build or another host's id) it falls back to JSON-object mode with the
 *     schema appended to the prompt; that schema is read back from there
 *     (`OpenAIChatCompletionExtra.jsonSchemaFromPrompt`) and kept out of the state.
 *
 * The full [[SystemOneResponse]] (probabilities, confidences) rides in `originalResponse`.
 * Native `TypeSafeScala*` exceptions are repacked onto the `OpenAIScala*` hierarchy
 * ([[repackAsOpenAIException]]), the native one kept as the cause.
 *
 * Of the standard `CreateChatCompletionSettings` only `model` (a System One model or alias),
 * `response_format_type` (which must be `json_schema`), `jsonSchema` (the questions) and `n`
 * (only 1) mean anything here, plus the TypeSafe-specific noul threshold in `extra_params`
 * (`setTypeSafeNoulThreshold`). Everything else - the sampling knobs, penalties, `max_tokens`,
 * `stop`, `seed`, `logprobs`, `reasoning_effort`, `user` / `store` / `metadata` and so on - is
 * DROPPED with a warning naming it, because System One does not sample text. `n > 1`, tools,
 * streaming and non-text message content are refused outright - image content too, unless
 * `imageInput` says the host reads it (Perplexity, OpenAI, Liquid's `d1`, llama.cpp).
 *
 * @param imageInput
 *   whether the image parts of user messages go into the state
 *   ([[TypeSafeChatMapping.toState]])
 */
private[service] class OpenAITypeSafeChatCompletionService(
  underlying: TypeSafeService,
  imageInput: Boolean = false
)(
  implicit ec: ExecutionContext
) extends OpenAIChatCompletionService {

  private val logger = LoggerFactory.getLogger(classOf[OpenAITypeSafeChatCompletionService])

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    ToolApprovalSettingsOps.refusingDecisions(settings, "The TypeSafe System One adapter") {
      Future
        .fromTry(Try {
          // the messages and the schema they are asked about - in json_object mode, the JSON
          // helper's fallback for a model it does not list as json_schema-capable, the schema
          // closes the prompt
          val (messagesFinal, schema) = (settings.response_format_type match {
            case Some(ChatCompletionResponseFormatType.json_schema) =>
              val schemaDef = settings.jsonSchema.getOrElse(
                fail("response_format_type is json_schema but no jsonSchema was set.")
              )
              Some(messages -> Json.toJson(schemaDef.structure))

            case Some(ChatCompletionResponseFormatType.json_object) =>
              OpenAIChatCompletionExtra.jsonSchemaFromPrompt(messages)

            case _ => None
          }).getOrElse(
            fail(
              "TypeSafe System One answers structured questions only: set " +
                s"response_format_type = json_schema and a jsonSchema (got ${settings.response_format_type
                    .getOrElse("none")})."
            )
          )

          OpenAITypeSafeChatCompletionService
            .unsupportedSettingsMessage(settings)
            .foreach(logger.warn)

          settings.n
            .filter(_ > 1)
            .foreach(n => fail(s"n = $n is not supported; System One answers once."))

          val plan = TypeSafeChatMapping.plan(schema)

          (
            plan,
            TypeSafeChatMapping.toState(messagesFinal, imageInput),
            settings.typeSafeNoulThreshold
          )
        })
        .flatMap { case (plan, state, threshold) =>
          underlying.systemOne(state, plan.questions, settings.model).map { response =>
            toChatCompletionResponse(
              response,
              SchemaQuestions.assemble(plan, response.answers, threshold)
            )
          }
        }
        // outside the flatMap: systemOne may also refuse a request before sending, by throwing
        .recoverWith(repackAsOpenAIException)
    }

  private def toChatCompletionResponse(
    response: SystemOneResponse,
    content: JsValue
  ): ChatCompletionResponse = {
    val input = response.usage.input_tokens
    val output = response.usage.output_tokens

    ChatCompletionResponse(
      id = response.requestId.getOrElse(""),
      created = new ju.Date(),
      model = response.model,
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = AssistantMessage(Json.stringify(content)),
          index = 0,
          finish_reason = Some("stop"),
          logprobs = None
        )
      ),
      usage = Some(
        UsageInfo(
          prompt_tokens = input.getOrElse(0),
          total_tokens = input.getOrElse(0) + output.getOrElse(0),
          completion_tokens = output
        )
      ),
      originalResponse = Some(response)
    )
  }

  private def fail(message: String): Nothing =
    throw new OpenAIScalaClientException(message)

  override def close(): Unit = underlying.close()
}

private[service] object OpenAITypeSafeChatCompletionService {

  /**
   * The settings that carry no meaning for System One, in the order they are declared on
   * `CreateChatCompletionSettings` - everything except `model`, `response_format_type`,
   * `jsonSchema`, `n` and the TypeSafe-specific `extra_params` entries.
   */
  private[impl] def unsupportedSettings(
    settings: CreateChatCompletionSettings
  ): Seq[String] = {
    val standard = Seq(
      "temperature" -> settings.temperature.isDefined,
      "top_p" -> settings.top_p.isDefined,
      "stop" -> settings.stop.nonEmpty,
      "max_tokens" -> settings.max_tokens.isDefined,
      "presence_penalty" -> settings.presence_penalty.isDefined,
      "frequency_penalty" -> settings.frequency_penalty.isDefined,
      "logit_bias" -> settings.logit_bias.nonEmpty,
      "logprobs" -> settings.logprobs.isDefined,
      "top_logprobs" -> settings.top_logprobs.isDefined,
      "user" -> settings.user.isDefined,
      "seed" -> settings.seed.isDefined,
      "store" -> settings.store.isDefined,
      "reasoning_effort" -> settings.reasoning_effort.isDefined,
      "verbosity" -> settings.verbosity.isDefined,
      "service_tier" -> settings.service_tier.isDefined,
      "parallel_tool_calls" -> settings.parallel_tool_calls.isDefined,
      "metadata" -> settings.metadata.nonEmpty
    ).collect { case (name, true) => name }

    val extra = settings.extra_params.keys.toSeq.sorted
      .filterNot(_ == CreateChatCompletionSettingsOps.NoulThresholdParam)
      .map(key => s"extra_params.$key")

    standard ++ extra
  }

  /** `None` when there is nothing to warn about. */
  private[impl] def unsupportedSettingsMessage(
    settings: CreateChatCompletionSettings
  ): Option[String] = {
    val dropped = unsupportedSettings(settings)

    if (dropped.isEmpty) None
    else
      Some(
        s"Dropping ${dropped.mkString(", ")} because " +
          (if (dropped.size == 1) "it is" else "they are") +
          " not supported by TypeSafe System One (Jev): it answers the schema's questions " +
          "with calibrated probabilities and does not sample text, so only 'model', " +
          "'response_format_type' = json_schema, 'jsonSchema', 'n' = 1 and the noul threshold " +
          "(setTypeSafeNoulThreshold) are used."
      )
  }
}
