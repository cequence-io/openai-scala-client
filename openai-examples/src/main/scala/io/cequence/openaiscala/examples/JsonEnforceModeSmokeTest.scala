package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  BaseMessage,
  JsonSchema,
  ModelId,
  NonOpenAIModelId,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * `createChatCompletionWithJSON` with `enforceMode`, per model, three ways:
 *   - by the lists (`enforceMode = None`): every model here is listed in
 *     `models-supporting-json-schema`, so native json_schema;
 *   - `Some(json_object)`: the schema in the prompt with `response_format: json_object`,
 *     although the model is listed;
 *   - `Some(json_schema)` for a model taken as unlisted (`jsonSchemaModels = Seq("none")`):
 *     the schema sent natively anyway.
 *
 * Each line shows what the helper handed the service - the response format, whether the schema
 * rode natively or in the prompt - and the parsed answer. The models: gpt-5.4-mini, Claude
 * Haiku 5.5, Gemini 2.5 Flash-Lite and Jev (a decision model, whose adapter reads the schema
 * back from the prompt in JSON-object mode). Providers whose key is missing are skipped; exits
 * 1 when a case fails.
 */
object JsonEnforceModeSmokeTest {

  private case class City(
    continent: String,
    coastal: Boolean
  )
  private implicit val cityFormat: Format[City] = Json.format[City]

  private val schema = JsonSchemaDef(
    "city",
    strict = true,
    JsonSchema.Object(
      properties = Seq(
        "continent" -> JsonSchema.String(
          `enum` = Seq("Africa", "Asia", "Europe", "North America", "South America", "Oceania")
        ),
        "coastal" -> JsonSchema
          .Boolean(description = Some("Does the city lie on a sea coast?"))
      ),
      required = Seq("continent", "coastal")
    )
  )

  private val expected = City("Europe", coastal = false)

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    def has(env: String) = sys.env.get(env).exists(_.nonEmpty)

    val providers: Seq[(String, () => OpenAIChatCompletionService, String)] = Seq(
      ("OPENAI_SCALA_CLIENT_API_KEY", "openai", ModelId.gpt_5_4_mini),
      ("ANTHROPIC_API_KEY", "anthropic", NonOpenAIModelId.claude_haiku_5_5),
      ("GOOGLE_API_KEY", "gemini", NonOpenAIModelId.gemini_2_5_flash_lite),
      ("TYPESAFE_API_KEY", "typesafe", NonOpenAIModelId.jev_latest)
    ).collect {
      case (env, provider, model) if has(env) =>
        val factory: () => OpenAIChatCompletionService = provider match {
          case "openai"    => () => OpenAIServiceFactory()
          case "anthropic" => () => AnthropicServiceFactory.asOpenAI()
          case "gemini"    => () => GeminiServiceFactory.asOpenAI()
          case _           => () => TypeSafeServiceFactory.asOpenAI()
        }
        (provider, factory, model)
    }

    // (label, enforceMode, jsonSchemaModels, the response format expected on the call)
    val modes = Seq(
      ("by the lists", None, Nil, ChatCompletionResponseFormatType.json_schema),
      (
        "json_object enforced",
        Some(ChatCompletionResponseFormatType.json_object),
        Nil,
        ChatCompletionResponseFormatType.json_object
      ),
      (
        "json_schema enforced, unlisted",
        Some(ChatCompletionResponseFormatType.json_schema),
        Seq("none"),
        ChatCompletionResponseFormatType.json_schema
      )
    )

    def schemaInPrompt(messages: Seq[BaseMessage]): Boolean =
      messages.exists {
        case UserMessage(content, _) => content.contains("<output_json_schema>")
        case m: UserSeqMessage       => m.content.toString.contains("<output_json_schema>")
        case _                       => false
      }

    val run = providers.foldLeft(Future.successful(Seq.empty[(String, Boolean)])) {
      case (acc, (provider, factory, model)) =>
        acc.flatMap { lines =>
          // what the JSON helper hands the service
          var sentMessages: Seq[BaseMessage] = Nil
          var sentSettings: Option[CreateChatCompletionSettings] = None
          val service = OpenAIServiceAdapters.forChatCompletionService.chatCompletionInput(
            messages => { sentMessages = messages; messages },
            settings => { sentSettings = Some(settings); settings }
          )(factory())

          modes
            .foldLeft(Future.successful(lines)) {
              case (acc2, (label, enforceMode, jsonSchemaModels, expectedFormat)) =>
                acc2.flatMap { lines2 =>
                  sentMessages = Nil
                  sentSettings = None
                  service
                    .createChatCompletionWithJSON[City](
                      Seq(
                        UserMessage(
                          "Madrid: on which continent is it, and does it lie on a sea coast?"
                        )
                      ),
                      CreateChatCompletionSettings(
                        model,
                        max_tokens = Some(4000),
                        jsonSchema = Some(schema)
                      ),
                      maxRetries = Some(1),
                      jsonSchemaModels = jsonSchemaModels,
                      enforceMode = enforceMode
                    )
                    .transform { result =>
                      val format = sentSettings.flatMap(_.response_format_type)
                      val inPrompt = schemaInPrompt(sentMessages)
                      val native = sentSettings.exists(_.jsonSchema.isDefined)
                      val wireOk = format.contains(expectedFormat) &&
                        inPrompt == (expectedFormat == ChatCompletionResponseFormatType.json_object) &&
                        native == (expectedFormat == ChatCompletionResponseFormatType.json_schema)
                      val (ok, outcome) = result match {
                        case Success(city) => (wireOk && city == expected, city.toString)
                        case Failure(e) =>
                          (
                            false,
                            s"FAIL ${e.getClass.getSimpleName}: ${e.getMessage.take(160)}"
                          )
                      }
                      val mark = if (ok) "OK  " else "FAIL"
                      val sentFormat = format.map(_.toString).getOrElse("-")
                      val where =
                        if (native) "native" else if (inPrompt) "in prompt" else "nowhere"
                      val line =
                        f"$mark $provider%-9s $model%-22s $label%-30s sent $sentFormat%-11s schema $where%-9s -> $outcome"
                      Success(lines2 :+ (line -> ok))
                    }
                }
            }
            .andThen { case _ => service.close() }
        }
    }

    val lines = Try(Await.result(run, 10.minutes)).getOrElse(Nil)
    lines.foreach(l => println(l._1))
    val failed = lines.count(!_._2)
    println(s"${lines.size - failed} of ${lines.size} passed")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failed == 0 && lines.nonEmpty) 0 else 1)
  }
}
