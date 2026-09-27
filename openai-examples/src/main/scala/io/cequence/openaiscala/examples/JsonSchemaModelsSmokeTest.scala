package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{JsonSchema, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * `createChatCompletionWithJSON` in native json_schema mode (taken from
 * `models-supporting-json-schema`) for the models added to that list on 2026-09-26, each on
 * its provider: gpt-4o-mini and gpt-5-search-api (OpenAI), gemini-2.5-flash-lite (Gemini),
 * grok-4.7 (xAI), and a GPT-6 model on Bedrock mantle (`openai.gpt-6-luna`, us-east-1).
 * Providers whose key is missing are skipped.
 */
object JsonSchemaModelsSmokeTest {

  private case class Capital(capital: String)
  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val schema = JsonSchemaDef(
    "capital",
    strict = true,
    JsonSchema
      .Object(properties = Seq("capital" -> JsonSchema.String()), required = Seq("capital"))
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)

    val cases: Seq[(String, () => OpenAIChatCompletionService, String)] =
      (if (has("OPENAI_SCALA_CLIENT_API_KEY"))
         Seq("gpt-4o-mini", "gpt-5-search-api")
           .map(m => ("openai", () => OpenAIServiceFactory(), m))
       else Nil) ++
        (if (has("GOOGLE_API_KEY"))
           Seq(
             (
               "gemini",
               () => GeminiServiceFactory.asOpenAI(),
               NonOpenAIModelId.gemini_2_5_flash_lite
             )
           )
         else Nil) ++
        (if (has("GROK_API_KEY"))
           Seq(
             (
               "grok",
               () => OpenAIChatCompletionServiceFactory(ChatProviderSettings.grok),
               NonOpenAIModelId.grok_4_7
             )
           )
         else Nil) ++
        (if (has("AWS_BEDROCK_BEARER_TOKEN"))
           Seq(
             (
               "bedrock-mantle",
               () =>
                 OpenAIServiceFactory.forBedrock(
                   auth = BedrockAuth.BearerToken(sys.env("AWS_BEDROCK_BEARER_TOKEN")),
                   region = "us-east-1",
                   isOpenAIModel = true
                 ),
               "openai.gpt-6-luna"
             )
           )
         else Nil)

    val results = Future.sequence(cases.map { case (provider, service, model) =>
      val s = service()
      s.createChatCompletionWithJSON[Capital](
        Seq(UserMessage("Capital of Norway?")),
        CreateChatCompletionSettings(
          model,
          max_tokens = Some(2000),
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(schema)
        )
      ).transform { result =>
        s.close()
        val status = result match {
          case Success(c) => s"OK $c"
          case Failure(e) => s"FAIL ${e.getMessage.take(200)}"
        }
        Success(f"$provider%-15s $model%-24s $status")
      }
    })

    Try(Await.result(results, 5.minutes)).foreach(_.foreach(println))
    Await.result(system.terminate(), 30.seconds)
    System.exit(0)
  }
}
