package io.cequence.openaiscala.examples

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceFactory}
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * What the providers do with `minimum` / `maximum` / `enum` on `JsonSchema.Integer` /
 * `JsonSchema.Number` - the fields the TypeSafe adapter needs for its score levels. The schema
 * asks for a hotel rating between 30 and 40, an unusual range no model would pick on its own,
 * so an in-range answer means the constraint was actually read.
 *
 * Results 2026-09-17 (`OPENAI_SCALA_CLIENT_API_KEY` + `ANTHROPIC_API_KEY`):
 *
 *   - OpenAI, `strict = true`: min/max and enum accepted AND honoured (40 / 35) on
 *     gpt-5.4-mini and gpt-6-astra
 *   - OpenAI, `strict = false`: accepted but ignored - the same prompt answered 4
 *   - Anthropic: `minimum` / `maximum` rejected with a 400 ("For 'integer' type, properties
 *     maximum, minimum are not supported") on Haiku 4.5 and Fable 5.1; `enum` honoured (40).
 *     The adapter now STRIPS the bounds with a warning (`dropNumericBounds`), so those rows
 *     succeed here with an unconstrained answer (4) instead of failing
 *
 * Gemini and Vertex AI are not probed here: their adapters read only the description, so the
 * constraints never reach the wire.
 */
object NumericSchemaBoundsProbe {

  private def schema(
    property: JsonSchema,
    strict: Boolean
  ) = JsonSchemaDef(
    name = "rating",
    strict = strict,
    structure = Left(
      JsonSchema.Object(
        properties = Seq("stars" -> property),
        required = Seq("stars")
      )
    )
  )

  // the range is deliberately unusual, so an in-range answer means the bound was read
  private val bounded =
    JsonSchema.Integer(Some("The rating"), minimum = Some(30), maximum = Some(40))

  private val enumerated =
    JsonSchema.Integer(Some("The rating"), `enum` = Seq(30L, 35L, 40L))

  private val plain = JsonSchema.Integer(Some("The rating"))

  private val prompt = "Rate this hotel: 'The room was clean and the staff friendly.'"

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val openAI = OpenAIServiceFactory()
    val anthropic = AnthropicServiceFactory.asOpenAI()

    def probe(
      name: String,
      service: OpenAIChatCompletionService,
      model: String,
      property: JsonSchema,
      strict: Boolean
    ): Future[Unit] = {
      val settings = CreateChatCompletionSettings(
        model = model,
        response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
        jsonSchema = Some(schema(property, strict))
      )
      service
        .createChatCompletion(Seq(UserMessage(prompt)), settings)
        .map(r => println(s"[OK]     $name: ${r.contentHead.trim.take(120)}"))
        .recover { case e =>
          println(s"[REJECT] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
        }
    }

    val numberBounded =
      JsonSchema.Number(Some("The rating"), minimum = Some(30), maximum = Some(40))
    val numberEnum = JsonSchema.Number(Some("The rating"), `enum` = Seq(30, 35, 40))

    val all = for {
      _ <- probe(
        "openai 5.4-mini strict=true  int min/max",
        openAI,
        ModelId.gpt_5_4_mini,
        bounded,
        true
      )
      _ <- probe(
        "openai 5.4-mini strict=false int min/max",
        openAI,
        ModelId.gpt_5_4_mini,
        bounded,
        false
      )
      _ <- probe(
        "openai 5.4-mini strict=true  int enum",
        openAI,
        ModelId.gpt_5_4_mini,
        enumerated,
        true
      )
      _ <- probe(
        "openai 5.4-mini strict=true  num min/max",
        openAI,
        ModelId.gpt_5_4_mini,
        numberBounded,
        true
      )
      _ <- probe(
        "openai 5.4-mini strict=true  num enum",
        openAI,
        ModelId.gpt_5_4_mini,
        numberEnum,
        true
      )
      _ <- probe(
        "openai 6-astra  strict=true  int min/max",
        openAI,
        ModelId.gpt_6_astra,
        bounded,
        true
      )
      _ <- probe(
        "openai 6-astra  strict=true  num enum",
        openAI,
        ModelId.gpt_6_astra,
        numberEnum,
        true
      )
      _ <- probe(
        "anthropic haiku4.5 int min/max",
        anthropic,
        NonOpenAIModelId.claude_haiku_4_5,
        bounded,
        false
      )
      _ <- probe(
        "anthropic haiku4.5 num min/max",
        anthropic,
        NonOpenAIModelId.claude_haiku_4_5,
        numberBounded,
        false
      )
      _ <- probe(
        "anthropic haiku4.5 int enum",
        anthropic,
        NonOpenAIModelId.claude_haiku_4_5,
        enumerated,
        false
      )
      _ <- probe(
        "anthropic haiku4.5 num enum",
        anthropic,
        NonOpenAIModelId.claude_haiku_4_5,
        numberEnum,
        false
      )
      _ <- probe(
        "anthropic fable5.1 int min/max",
        anthropic,
        NonOpenAIModelId.claude_fable_5_1,
        bounded,
        false
      )
    } yield ()

    try Await.result(all, 5.minutes)
    finally {
      openAI.close()
      anthropic.close()
    }

    // what the client actually puts on the wire
    println("\nwire form of the bounded property:")
    println(
      Json.prettyPrint(
        Json.toJson[JsonSchema](bounded)(io.cequence.openaiscala.JsonFormats.jsonSchemaFormat)
      )
    )
  }
}
