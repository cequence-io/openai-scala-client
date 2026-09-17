package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, TypeSafeModelId}
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Format, Json}

import scala.concurrent.Future

/**
 * System One through the OpenAI chat-completion interface: the JSON schema of the case class
 * becomes the questions, the messages the state, and `createChatCompletionWithJSON[T]` parses
 * the answers straight into `Triage` - so the same code can run against an LLM or Jev.
 *
 * Only closed-vocabulary schemas work (booleans, string enums, numeric enums / small ranges,
 * arrays of string enums, nested objects of those); a free-form string is refused up front.
 * The `jev-*` models are listed under `models-supporting-json-schema`, so the helper keeps the
 * request in json-schema mode.
 *
 * Requires `TYPESAFE_API_KEY`.
 */
object TypeSafeCreateChatCompletionWithJSON extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = TypeSafeServiceFactory.asOpenAI()

  private case class Triage(
    department: String,
    is_urgent: Boolean,
    frustration: Int,
    topics: Seq[String]
  )

  private implicit val triageFormat: Format[Triage] = Json.format[Triage]

  // `minimum` / `maximum` on the typed Integer exist for this adapter - the LLM providers'
  // structured output mostly ignores or rejects them
  private val triageSchema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "department" -> JsonSchema.String(
            description = Some("Which team should handle this"),
            `enum` = Seq("billing", "technical", "sales")
          ),
          "is_urgent" -> JsonSchema.Boolean(
            Some("The message conveys urgency or time-sensitivity")
          ),
          "frustration" -> JsonSchema.Integer(
            Some("How frustrated the customer appears, 1 (calm) to 5 (furious)"),
            minimum = Some(1),
            maximum = Some(5)
          ),
          "topics" -> JsonSchema.Array(
            JsonSchema.String(`enum` = Seq("payments", "integration", "pricing", "account")),
            description = Some("What the message is about")
          )
        ),
        required = Seq("department", "is_urgent", "frustration", "topics")
      )
    )
  )

  private val messages = Seq(
    SystemMessage("You triage inbound support tickets for a payments platform."),
    UserMessage(
      "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. " +
        "I'm losing sales. Please help ASAP."
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithJSONFullResponse[Triage](
        messages,
        CreateChatCompletionSettings(model = TypeSafeModelId.jev_latest)
          .withJsonSchema(triageSchema)
      )
      .map { case (triage, response) =>
        println(s"triage   : $triage")
        println(s"model    : ${response.model}, usage ${response.usage}")

        // the calibrated probabilities are one cast away
        response.originalResponse.collect { case r: SystemOneResponse =>
          println(s"department probabilities: ${r.choice("department").ranked}")
          println(f"frustration confidence  : ${r.score("frustration").confidence}%.2f")
        }
      }
}
