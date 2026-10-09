package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.FutureHelpers.parallelize
import io.cequence.openaiscala.service.{JsonSchemaOf, QuotedText}
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps
import io.cequence.openaiscala.typesafe.domain.{Decision, NoulQuestion, Ranked, RerankSettings}
import io.cequence.openaiscala.typesafe.service.impl.SchemaQuestions
import play.api.libs.json.{JsError, JsString, JsSuccess, JsValue, Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Routines built on a decision service (TypeSafe's Jev, Liquid's d1, Perplexity's decider, the
 * decision models on OpenRouter - any `TypeSafeService`):
 *
 * {{{
 * import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
 *
 * service.decide[Triage](ticket)                      // a case class, decided in one call
 * service.rerank(query, passages)                     // passages, most helpful first
 * }}}
 */
object DecisionServiceExtra {

  implicit class DecisionServiceImplicits(service: TypeSafeService) {

    /**
     * Decides a case class: each field becomes a question - a `Boolean` a yes/no question, an
     * enum a choice, a small numeric range (`@JsonSchemaRange`, at most 10 whole numbers) a
     * scale, a `Seq` of an enum one yes/no question per option, a nested case class its own
     * fields - all asked in one call, and the answers come back as a `T` together with their
     * probabilities.
     *
     * {{{
     * case class Triage(
     *   @JsonSchemaDescription("Does the customer ask for a refund?") refund: Boolean,
     *   @JsonSchemaDescription("Which team should handle the ticket?") team: Team
     * )
     *
     * service.decide[Triage](ticket).map { decision =>
     *   decision.value.team                            // Team.Billing
     *   decision.choice("team").probabilities          // Map(Billing -> 0.91, ...)
     *   decision.noul("refund").noul                   // 0.97
     * }
     * }}}
     *
     * The questions come from `T`'s JSON schema - derived from the case class, its
     * descriptions from `@JsonSchemaDescription` (see `JsonSchemaOf`) - and are named by the
     * fields' paths; a field System One cannot ask about (free text, an unbounded number)
     * fails the call before it is sent. Preview the questions with
     * `TypeSafeChatMapping.toQuestions(JsonSchemaDef("decision", strict = true, schema))`.
     *
     * @param state
     *   what is decided about: text, a JSON object or an array
     * @param noulThreshold
     *   the probability at which a yes/no question reads as `true`
     */
    def decide[T: JsonSchemaOf: Reads](
      state: JsValue,
      model: String = service.defaultModel,
      noulThreshold: Double = CreateChatCompletionSettingsOps.DefaultNoulThreshold
    )(
      implicit ec: ExecutionContext
    ): Future[Decision[T]] =
      Future.unit.flatMap { _ =>
        require(
          noulThreshold >= 0 && noulThreshold <= 1,
          s"The noul threshold must be within 0..1, got $noulThreshold."
        )
        val plan = SchemaQuestions.plan(Json.toJson(JsonSchemaOf[T].schema))

        service.systemOne(state, plan.questions, model).map { response =>
          SchemaQuestions.assemble(plan, response.answers, noulThreshold).validate[T] match {
            case JsSuccess(value, _) =>
              Decision(value, response)

            case JsError(errors) =>
              throw new TypeSafeScalaClientException(
                "The decision does not read back as the requested type - does its JSON format use the " +
                  s"case class's field names? ${JsError.toJson(errors)}"
              )
          }
        }
      }

    /** [[decide]] over a text state. */
    def decide[T: JsonSchemaOf: Reads](
      state: String
    )(
      implicit ec: ExecutionContext
    ): Future[Decision[T]] =
      decide[T](JsString(state))

    /** [[decide]] over a text state, with an explicit model. */
    def decide[T: JsonSchemaOf: Reads](
      state: String,
      model: String
    )(
      implicit ec: ExecutionContext
    ): Future[Decision[T]] =
      decide[T](JsString(state), model)

    /** [[decide]] over a text state, with an explicit model and noul threshold. */
    def decide[T: JsonSchemaOf: Reads](
      state: String,
      model: String,
      noulThreshold: Double
    )(
      implicit ec: ExecutionContext
    ): Future[Decision[T]] =
      decide[T](JsString(state), model, noulThreshold)

    /**
     * Ranks passages by how much each helps answer the query - e.g. to re-rank what a vector
     * store retrieved before it goes into a prompt. Every passage is a yes/no question of its
     * own, its score the probability of yes; the passages are asked in batches (by count and
     * by size, see [[RerankSettings]]), a few at once, identical ones once and blank ones not
     * at all. A batch the host refuses as too long is split in two and asked again; any other
     * failure fails the call - wrap the service in `TypeSafeServiceAdapters.retry` to ride out
     * transient ones.
     *
     * @return
     *   the passages best first (ties in their input order), filtered by `minScore` / `topK`
     */
    def rerank(
      query: String,
      passages: Seq[String],
      settings: RerankSettings = RerankSettings()
    )(
      implicit ec: ExecutionContext
    ): Future[Seq[Ranked[String]]] =
      rerankBy(query, passages, settings)(identity)

    /** [[rerank]] for any items, by their text. */
    def rerankBy[T](
      query: String,
      items: Seq[T],
      settings: RerankSettings = RerankSettings()
    )(
      text: T => String
    )(
      implicit ec: ExecutionContext
    ): Future[Seq[Ranked[T]]] =
      Future.unit.flatMap { _ =>
        val texts = items.map(item => cut(text(item), settings.maxPassageChars))
        val asked = texts.filter(_.trim.nonEmpty).distinct
        val model = settings.model.getOrElse(service.defaultModel)

        parallelize(batches(query, asked, settings), Some(settings.parallelism)) { batch =>
          scoreBatch(service, query, batch, model, settings)
        }.map { scored =>
          val scores = scored.flatten.toMap
          val ranked = items
            .zip(texts)
            .zipWithIndex
            .map { case ((item, passage), index) =>
              Ranked(item, scores.getOrElse(passage, 0d), index)
            }
            .sortBy(ranked => (-ranked.score, ranked.index))

          val kept = settings.minScore.fold(ranked)(min => ranked.filter(_.score >= min))
          settings.topK.fold(kept)(kept.take)
        }
      }
  }

  private val Document = "document"

  // a passage is quoted as data, `document` tags inside it defused
  private val quoted = QuotedText(Document)

  // the question about a passage, the passage after it
  private def question(
    settings: RerankSettings,
    passage: String
  ): String =
    s"${settings.question}\n${quoted(Document, passage)}"

  private def cut(
    text: String,
    maxChars: Int
  ): String =
    if (text.length <= maxChars) text else text.take(maxChars) + "…"

  // the passages of one request, and the characters their questions take
  private final case class Batch(
    passages: Vector[String],
    chars: Int
  ) {
    def add(
      passage: String,
      size: Int
    ): Batch = Batch(passages :+ passage, chars + size)
  }

  // passages grouped greedily, each request within the count and the size limits
  private def batches(
    query: String,
    passages: Seq[String],
    settings: RerankSettings
  ): Seq[Seq[String]] = {
    val budget = settings.maxCharsPerRequest - query.length

    passages
      .foldLeft(Vector.empty[Batch]) { case (done, passage) =>
        val size = question(settings, passage).length
        done match {
          case init :+ last
              if last.passages.size < settings.maxPassagesPerRequest &&
                last.chars + size <= budget =>
            init :+ last.add(passage, size)
          case _ =>
            done :+ Batch(Vector(passage), size)
        }
      }
      .map(_.passages)
  }

  // the batch's passages and their probability of yes; the halves of a batch that is too long
  // one after the other, so a batch never has more than one request in flight
  private def scoreBatch(
    service: TypeSafeService,
    query: String,
    batch: Seq[String],
    model: String,
    settings: RerankSettings
  )(
    implicit ec: ExecutionContext
  ): Future[Seq[(String, Double)]] =
    Future.unit.flatMap { _ =>
      val questions = batch.zipWithIndex.map { case (passage, i) =>
        s"p$i" -> NoulQuestion(question(settings, passage))
      }.toMap
      service.systemOne(JsString(query), questions, model)
    }.map { response =>
      batch.zipWithIndex.map { case (passage, i) => passage -> response.noul(s"p$i").noul }
    }.recoverWith {
      case _: TypeSafeScalaTokenCountExceededException if batch.size > 1 =>
        val (left, right) = batch.splitAt(batch.size / 2)
        scoreBatch(service, query, left, model, settings).flatMap { leftScores =>
          scoreBatch(service, query, right, model, settings).map(leftScores ++ _)
        }
    }
}
