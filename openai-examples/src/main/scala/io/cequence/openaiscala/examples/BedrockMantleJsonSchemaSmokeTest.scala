package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import io.cequence.openaiscala.service.{OpenAIChatCompletionExtra, OpenAIServiceFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Verifies JSON-schema / structured output for the Bedrock `bedrock-mantle` OpenAI models.
 *
 *   1. Network-free: confirms that adding the dotted Bedrock ids to
 *      `models-supporting-json-schema` makes the chat-completion JSON helper pick OpenAI's
 *      NATIVE `json_schema` response_format (instead of the `json_object`+prompt fallback) for
 *      `openai.gpt-oss-120b`. A control (unlisted) model still falls back to `json_object`.
 *      NOTE: this config list is only consulted by the chat-completion JSON helpers - it has
 *      no effect on the Responses API, which always sends the schema verbatim.
 *
 * 2. Live: structured output for `openai.gpt-5.5` through the Responses API (via the
 * chat-completion adapter). Requires `AWS_BEARER_TOKEN_BEDROCK` + `AWS_BEDROCK_REGION`.
 */
object BedrockMantleJsonSchemaSmokeTest {

  private val countrySchema = JsonSchemaDef(
    name = "country",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "capital" -> JsonSchema.String(),
          "population" -> JsonSchema.Integer()
        ),
        required = Seq("capital", "population")
      )
    )
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: scala.concurrent.ExecutionContext = system.dispatcher

    // ---- 1. network-free config-routing check ----
    println("=== config routing (models-supporting-json-schema) ===")
    def modeFor(model: String): Option[ChatCompletionResponseFormatType] = {
      val settings =
        CreateChatCompletionSettings(model = model, jsonSchema = Some(countrySchema))
      OpenAIChatCompletionExtra
        .handleOutputJsonSchema(Seq(UserMessage("x")), settings, "smoke")
        ._2
        .response_format_type
    }
    val listed = modeFor(NonOpenAIModelId.bedrock_openai_gpt_oss_120b)
    val control = modeFor("totally-unknown-model-xyz")
    println(s"openai.gpt-oss-120b -> $listed (expected: Some(json_schema), i.e. native mode)")
    println(s"unknown model       -> $control (expected: Some(json_object), i.e. fallback)")
    require(
      listed.contains(ChatCompletionResponseFormatType.json_schema),
      "Bedrock gpt-oss id was NOT routed to native json_schema - check the config entry"
    )
    require(
      control.contains(ChatCompletionResponseFormatType.json_object),
      "control model should fall back to json_object"
    )
    println("OK: config entry takes effect for the chat-completion JSON helper.\n")

    // ---- 2. live gpt-5.5 structured output via Responses API ----
    println("=== live gpt-5.5 structured output (Responses API) ===")
    val service = OpenAIServiceFactory.forBedrockMantle(isOpenAIModel = true)
    val chatService = OpenAIResponsesChatCompletionService(service)
    try {
      val response = Await.result(
        chatService.createChatCompletion(
          messages = Seq(
            SystemMessage("You return structured data."),
            UserMessage("Give the capital and population of France.")
          ),
          settings = CreateChatCompletionSettings(
            model = NonOpenAIModelId.bedrock_openai_gpt_5_5,
            reasoning_effort = Some(ReasoningEffort.low),
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
            jsonSchema = Some(countrySchema)
          )
        ),
        4.minutes
      )
      println(s"Response: ${response.contentHead}")
    } finally {
      chatService.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
