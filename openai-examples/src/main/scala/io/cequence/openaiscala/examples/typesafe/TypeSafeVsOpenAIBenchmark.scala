package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAIServiceFactory}
import io.cequence.openaiscala.typesafe.domain.TypeSafeModelId
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Format, Json}

import scala.concurrent.Future

/**
 * Like-for-like: the SAME `createChatCompletionWithJSONFullResponse[Triage]` call issued
 * against Jev through `TypeSafeServiceFactory.asOpenAI()` and against an OpenAI model, from
 * one JVM and one warm connection pool, so a published side-by-side can be reproduced.
 *
 * Measured 2026-09-20 from a client in Europe, `REPS=7` after one discarded warm-up:
 *
 * Jev (`jev-latest`) wall p50 327 ms usage 473 in / 133 out OpenAI (`gpt-5.6-luna`) wall p50
 * 1097 ms usage 161 in / 31 out
 *
 * Both returned the same verdict. Four caveats belong with those numbers:
 *
 *   1. Wall clock from one location includes the network round trip on BOTH sides. Jev's own
 *      server compute is ~90 ms (`x-envoy-upstream-service-time`), so roughly 190 ms of its
 *      total is transport - "faster end to end from this client", not "the model is faster".
 *      2. Jev reports MORE input tokens because the adapter expands the schema into one typed
 *      question per property. Its cost is still far lower: $0.042 per 1M input, output free.
 *      3. The OpenAI side runs that model's defaults. GPT-5.6 is reasoning-first, so
 *      `reasoning_effort` moves its latency a lot; nothing is overridden here. 4. n=7 on a
 *      single ticket is illustrative, not a benchmark.
 *
 * Requires `TYPESAFE_API_KEY` and `OPENAI_SCALA_CLIENT_API_KEY`. `REPS` and `OPENAI_MODEL`
 * override the defaults.
 */
object TypeSafeVsOpenAIBenchmark extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = TypeSafeServiceFactory.asOpenAI()
  private val openAI = OpenAIServiceFactory()

  private val reps = sys.env.getOrElse("REPS", "5").toInt
  private val openAIModel = sys.env.getOrElse("OPENAI_MODEL", ModelId.gpt_5_6_luna)

  private case class Triage(
    department: String,
    is_urgent: Boolean,
    frustration: Int,
    topics: Seq[String]
  )
  private implicit val fmt: Format[Triage] = Json.format[Triage]

  private val schema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "department" -> JsonSchema.String(
            description = Some("Which team should handle this"),
            `enum` = Seq("billing", "technical", "sales")
          ),
          "is_urgent" -> JsonSchema.Boolean(Some("The message conveys time pressure")),
          "frustration" -> JsonSchema.Integer(
            Some("How frustrated the customer appears, 1 calm .. 5 furious"),
            minimum = Some(1),
            maximum = Some(5)
          ),
          "topics" -> JsonSchema.Array(
            JsonSchema.String(`enum` = Seq("payments", "integration", "pricing")),
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
      "I've been trying to connect my Stripe account for 3 days and it keeps failing. " +
        "I'm losing sales. Please help ASAP."
    )
  )

  private def timed(
    label: String,
    svc: OpenAIChatCompletionService,
    model: String
  ): Future[Unit] = {
    def once: Future[(Long, Triage, Option[(Int, Int)])] = {
      val t0 = System.nanoTime()
      svc
        .createChatCompletionWithJSONFullResponse[Triage](
          messages,
          CreateChatCompletionSettings(model = model).withJsonSchema(schema)
        )
        .map { case (triage, r) =>
          (
            (System.nanoTime() - t0) / 1000000,
            triage,
            r.usage.map(u => (u.prompt_tokens, u.completion_tokens.getOrElse(0)))
          )
        }
    }

    // one warm-up (TLS handshake + pool), then `reps` measured
    once.flatMap { _ =>
      (1 to reps).foldLeft(Future.successful(List.empty[(Long, Triage, Option[(Int, Int)])])) {
        (
          acc,
          _
        ) => acc.flatMap(xs => once.map(_ :: xs))
      }
    }.map { runs =>
      val ms = runs.map(_._1).sorted
      val (_, triage, usage) = runs.head
      println(f"$label%-46s model=$model")
      println(
        f"    wall p50 ${ms(ms.size / 2)}%5d ms   min ${ms.head}%5d   max ${ms.last}%5d   (n=${ms.size})"
      )
      println(s"    usage in/out ${usage.map { case (i, o) => s"$i/$o" }.getOrElse("n/a")}")
      println(s"    result $triage")
    }.recover { case e =>
      println(
        f"$label%-46s model=$model  FAILED: ${e.getClass.getSimpleName}: ${e.getMessage.take(200)}"
      )
    }
  }

  override protected def run: Future[_] =
    for {
      _ <- timed(
        "Jev via TypeSafeServiceFactory.asOpenAI()",
        service,
        TypeSafeModelId.jev_latest
      )
      _ <- timed("OpenAI via OpenAIServiceFactory()", openAI, openAIModel)
    } yield openAI.close()
}
