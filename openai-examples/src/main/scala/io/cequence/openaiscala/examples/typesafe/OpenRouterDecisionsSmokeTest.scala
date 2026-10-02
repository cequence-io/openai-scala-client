package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeService,
  TypeSafeServiceFactory
}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import play.api.libs.json.{Format, JsString, JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live walkthrough of the decision models on OpenRouter, through one key and TypeSafe's
 * protocol - `TypeSafeServiceFactory(DecisionProviderSettings.openRouter)`:
 *
 *   - the decision models OpenRouter lists (`listModels`, `output_modalities=decisions`)
 *   - the same three questions (a noul, a choice, a score about a product review) on each of
 *     them, with the latency - Respan's Span-01 takes noul questions only, over a text state
 *     or a conversation trace (`{"input": [messages], "output": message}`), so it gets those
 *   - the questions as a JSON schema through the OpenAI interface on d1
 *     (`asOpenAI(DecisionProviderSettings.openRouter)` + `createChatCompletionWithJSON[T]`)
 *   - a latency benchmark of the models that answer (`DecisionModelBenchmark`, one shared
 *     engine; pass `nobench` to skip it)
 *
 * Requires `OPENROUTER_API_KEY`; a run costs a fraction of a cent.
 */
object OpenRouterDecisionsSmokeTest {

  private case class Review(
    defect: Boolean,
    sentiment: String,
    severity: Int
  )

  private implicit val reviewFormat: Format[Review] = Json.format[Review]

  private val reviewText =
    "The headphones sound great, but the battery stopped charging after two weeks."

  private val review: JsValue =
    Json.obj("title" -> "Battery died after two weeks", "review" -> reviewText)

  private val questions: Map[String, Question] = Map(
    "defect" -> NoulQuestion("Does the review report a product defect?"),
    "sentiment" -> ChoiceQuestion(
      "What is the overall sentiment of the review?",
      "positive" -> "Mostly satisfied",
      "mixed" -> "Praise and complaints in one review",
      "negative" -> "Mostly dissatisfied"
    ),
    "severity" -> ScoreQuestion(
      "How severe is the reported problem?",
      "Cosmetic",
      "Inconvenient",
      "Product unusable"
    )
  )

  private val reviewSchema = JsonSchemaDef(
    name = "review",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "defect" -> JsonSchema.Boolean(Some("Does the review report a product defect?")),
          "sentiment" -> JsonSchema.String(
            Some("What is the overall sentiment of the review?"),
            `enum` = Seq("positive", "mixed", "negative")
          ),
          "severity" -> JsonSchema.Integer(
            Some("How severe is the reported problem, 0 (cosmetic) to 2 (product unusable)?"),
            minimum = Some(0),
            maximum = Some(2)
          )
        ),
        required = Seq("defect", "sentiment", "severity")
      )
    )
  )

  // Respan's Span-01 judges with nouls only (plain-string instructions), over a text state or a
  // conversation trace
  private def isSpan(model: String) = model.startsWith("respan/")

  private val spanQuestions: Map[String, Question] = Map(
    "defect" -> NoulQuestion("Does the review report a product defect?"),
    "praise" -> NoulQuestion("Does the review praise the sound?")
  )

  private def summary(response: SystemOneResponse): String =
    if (response.answers.contains("sentiment")) {
      val sentiment = response.choice("sentiment")
      f"defect ${response.noul("defect").noul}%.3f | ${sentiment.choice} " +
        f"${sentiment.probabilities.getOrElse(sentiment.choice, 0.0)}%.2f | severity " +
        f"${response.score("severity").score}%.2f | ${response.model} | ${response.usage}"
    } else
      f"defect ${response.noul("defect").noul}%.3f | praise ${response.noul("praise").noul}%.3f" +
        s" | ${response.model} | ${response.usage}"

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val engine = WSClientEngineRegistry(TransportSettings())
    val openRouter =
      TypeSafeServiceFactory.withEngine(engine, DecisionProviderSettings.openRouter)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.openRouter)

    // one service per model on the shared engine, the model as its default
    def serviceFor(model: String): TypeSafeService =
      TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openRouter.copy(defaultModel = model)
      )

    val run = for {
      models <- openRouter.listModels
      _ = println(s"[models] ${models.size}: ${models.map(_.name).mkString(", ")}")

      answered <- models.foldLeft(Future.successful(Seq.empty[String])) {
        (
          acc,
          model
        ) =>
          acc.flatMap { done =>
            val start = System.nanoTime()
            val call =
              if (isSpan(model.name))
                openRouter.systemOne(JsString(reviewText), spanQuestions, model.name)
              else openRouter.systemOne(review, questions, model.name)
            call.map { response =>
              val ms = (System.nanoTime() - start) / 1000000
              println(f"[${model.name}%-40s] $ms%5d ms | ${summary(response)}")
              done :+ model.name
            }.recover { case NonFatal(e) =>
              println(f"[${model.name}%-40s] FAILED: ${e.getMessage.take(160)}")
              done
            }
          }
      }

      triage <- asOpenAI.createChatCompletionWithJSON[Review](
        Seq(UserMessage(review.toString)),
        CreateChatCompletionSettings(model = TypeSafeModelId.openrouter_liquid_d1)
          .withJsonSchema(reviewSchema)
      )
      _ = println(s"[via OpenAI, d1] $triage")

      _ <-
        if (args.contains("nobench")) Future.successful(())
        else
          DecisionModelBenchmark.run(
            answered
              .filterNot(isSpan)
              .filterNot(_ == TypeSafeModelId.openrouter_jev_1_13)
              .map(model => model.split('/').last -> serviceFor(model)),
            Json.stringify(review),
            questions,
            runs = 5
          )
    } yield answered.size -> models.size

    val passed =
      try {
        val (answered, listed) = Await.result(run, 15.minutes)
        println(s"[summary] $answered of $listed models answered")
        answered > 0
      } catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          false
      }

    asOpenAI.close()
    openRouter.close()
    engine.close()
    Await.result(system.terminate(), 30.seconds)
    println(if (passed) "ALL PASSED" else "FAILED")
    System.exit(if (passed) 0 else 1)
  }
}
