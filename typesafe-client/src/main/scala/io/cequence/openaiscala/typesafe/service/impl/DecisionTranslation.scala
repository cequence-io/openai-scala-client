package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.domain.decisions.{
  ChoiceProbability,
  CreateDecisionSettings,
  Decision,
  DecisionAnswer,
  DecisionChoice,
  DecisionContent,
  DecisionInput,
  DecisionLevel,
  DecisionMessage,
  DecisionQuestion,
  DecisionValue,
  LevelProbability
}
import io.cequence.openaiscala.domain.responsesapi.UsageInfo
import io.cequence.openaiscala.typesafe.domain.{
  Answer,
  ChoiceAnswer,
  ChoiceQuestion,
  DecisionImage,
  NoulAnswer,
  NoulQuestion,
  Question,
  ScoreAnswer,
  ScoreQuestion,
  SystemOneResponse,
  UnknownAnswer,
  Usage
}
import io.cequence.openaiscala.typesafe.service.TypeSafeService
import org.slf4j.LoggerFactory
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * System One's questions and answers in the terms of OpenAI's Decisions API - for a System One
 * service on OpenAI's host (`DecisionProtocol.OpenAI`):
 *
 *   - the state: text as it is; JSON as its compact text, each image part in it replaced by an
 *     `[image n]` marker and sent as an `input_image` after the text (`DecisionImage.lift`) -
 *     a state of text and image parts only (the chat adapter's) is one message of those parts
 *   - a noul -> a predicate (its yes / no criteria appended to the instructions), a choice ->
 *     a choice (an option's criteria its description), a score -> a score (its levels the
 *     labels)
 *   - the answers back by name: a predicate -> a noul, a choice -> a choice, a score -> a
 *     score (the legend taken from the question), a refusal -> `UnknownAnswer("refusal", ...)`
 *
 * [[OpenAIToSystemOne]] is the other direction.
 */
private[service] object SystemOneToOpenAI {

  def input(state: JsValue): DecisionInput =
    state match {
      case JsString(text) =>
        DecisionInput.Text(text)

      case JsArray(parts) if parts.nonEmpty && parts.forall(isPart) =>
        DecisionInput.Messages(Seq(DecisionMessage(parts.map(content).toSeq)))

      case json =>
        DecisionImage.lift(json) match {
          case (_, Nil) =>
            DecisionInput.Text(Json.stringify(json))
          case (marked, images) =>
            DecisionInput.Messages(
              Seq(
                DecisionMessage(
                  DecisionContent.InputText(Json.stringify(marked)) +:
                    images.map(DecisionContent.InputImage(_))
                )
              )
            )
        }
    }

  def question(
    name: String,
    question: Question
  ): DecisionQuestion =
    question match {
      case NoulQuestion(instructions, criteria) =>
        val conditions = criteria.toSeq.flatMap { criteria =>
          criteria.yes.map(yes => s"Yes when: ${text(yes)}").toSeq ++
            criteria.no.map(no => s"No when: ${text(no)}").toSeq
        }
        DecisionQuestion.Predicate(
          (instructions.map(text).toSeq ++ conditions).mkString("\n"),
          Some(name)
        )

      case ChoiceQuestion(criteria, instructions) =>
        DecisionQuestion.Choice(
          instructions.map(text).getOrElse(""),
          criteria.toSeq.map { case (option, description) =>
            DecisionChoice(DecisionValue.Text(option), description.map(text))
          },
          Some(name)
        )

      case ScoreQuestion(levels, instructions) =>
        DecisionQuestion.Score(
          instructions.map(text).getOrElse(""),
          levels.map(level),
          Some(name)
        )
    }

  /** The answers by the names of the questions asked. */
  def response(
    decision: Decision,
    questions: Seq[(String, Question)]
  ): SystemOneResponse = {
    val asked = questions.toMap

    // by name; one without a name by its position (the answers come in the questions' order)
    val answers = decision.answers.zipWithIndex.flatMap { case (answer, index) =>
      answer.name.orElse(questions.lift(index).map(_._1)).map { name =>
        name -> systemOneAnswer(answer, asked.get(name))
      }
    }.toMap

    SystemOneResponse(
      decision.model,
      answers,
      Usage(
        decision.usage.map(_.inputTokens),
        decision.usage.map(_.outputTokens)
      )
    )
  }

  // a part of a chat message mapped to the state: its text, or an image
  private def isPart(json: JsValue): Boolean =
    json.isInstanceOf[JsString] || isImage(json)

  // an image part as DecisionImage defines it: `type` image_url AND a string URL
  private def isImage(json: JsValue): Boolean =
    json match {
      case obj: JsObject => DecisionImage.url(obj).isDefined
      case _             => false
    }

  private def content(part: JsValue): DecisionContent =
    part match {
      case JsString(text) => DecisionContent.InputText(text)
      case image =>
        DecisionContent.InputImage(
          image.asOpt[JsObject].flatMap(DecisionImage.url).getOrElse("")
        )
    }

  // a score level: text as the label, `{label, description}` as it is, other JSON as its text
  private def level(json: JsValue): DecisionLevel =
    (json \ "label").asOpt[String] match {
      case Some(label) => DecisionLevel(label, (json \ "description").asOpt[String])
      case None        => DecisionLevel(text(json))
    }

  private def text(json: JsValue): String =
    json match {
      case JsString(text) => text
      case other          => Json.stringify(other)
    }

  private def systemOneAnswer(
    answer: DecisionAnswer,
    question: Option[Question]
  ): Answer =
    answer match {
      case DecisionAnswer.Predicate(_, probability) =>
        NoulAnswer(probability)

      case DecisionAnswer.Choice(_, choice, probabilities, confidence) =>
        ChoiceAnswer(
          option(choice),
          confidence,
          probabilities.map(p => option(p.value) -> p.probability).toMap
        )

      case DecisionAnswer.Score(_, score, probabilities, confidence) =>
        val levels =
          question.collect { case ScoreQuestion(levels, _) => levels }.getOrElse(Nil)
        ScoreAnswer(
          score,
          confidence,
          probabilities.map { p =>
            p.value -> levels.lift(p.value).getOrElse(JsString(p.label))
          }.toMap,
          probabilities.map(p => p.value -> p.probability).toMap
        )

      case DecisionAnswer.Refusal(name) =>
        UnknownAnswer("refusal", Json.obj("type" -> "refusal", "name" -> name))

      case DecisionAnswer.Unknown(_, kind, raw) =>
        UnknownAnswer(kind, raw)
    }

  private def option(value: DecisionValue): String =
    OpenAIToSystemOne.option(value)
}

/**
 * OpenAI's Decisions API in System One's terms - `createDecision` on a System One host (Jev,
 * Liquid's d1, Perplexity's decider, OpenRouter, llama.cpp):
 *
 *   - the input -> the state: text as it is; a message -> its text, or (with images) an array
 *     of its parts, the images as `DecisionImage` parts (their `detail` dropped); several
 *     messages -> an array of those
 *   - a predicate -> a noul, a choice -> a choice (a boolean value as its text, so `true` and
 *     `"true"` in one question are refused), a score -> a score (a level with a description as
 *     `{label, description}`, else its label)
 *   - the questions keyed by their names, an unnamed one (or one named "") by `question_<n>`
 *     (its position from 1) - duplicate names are refused, as OpenAI refuses them
 *   - the answers back in the questions' order with their names, values and level labels as
 *     asked; `UnknownAnswer("refusal")` -> a refusal; the usage as the Responses API's; the
 *     request id carried over
 *   - no model -> the service's default; a `safety_identifier` and image `detail`s are dropped
 *     with a warning (System One has neither)
 *
 * [[SystemOneToOpenAI]] is the other direction.
 */
private[service] object OpenAIToSystemOne {

  private val logger = LoggerFactory.getLogger(getClass)

  /** A question as asked: its System One key and question, and the original. */
  final case class Asked(
    key: String,
    question: Question,
    original: DecisionQuestion
  )

  /**
   * `createDecision` answered by `service.systemOne`.
   *
   * @param imageRefusal
   *   why images are refused, when the host reads none
   */
  def createDecision(
    service: TypeSafeService,
    imageRefusal: Option[String]
  )(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings
  )(
    implicit ec: ExecutionContext
  ): Future[Decision] =
    Future.unit.flatMap { _ =>
      // fails fast (IllegalArgumentException) before any I/O
      imageRefusal.filter(_ => imageUrls(input).nonEmpty).foreach { why =>
        throw new IllegalArgumentException(why)
      }
      val asked = this.asked(questions)
      val dropped = unsupported(input, settings)
      if (dropped.nonEmpty)
        logger.warn(
          s"${dropped.mkString(" and ")} dropped - a System One host takes no such setting."
        )

      service
        .systemOne(
          state(input),
          ListMap(asked.map(a => a.key -> a.question): _*),
          settings.model.getOrElse(service.defaultModel)
        )
        .map(decision(_, asked))
    }

  def state(input: DecisionInput): JsValue =
    input match {
      case DecisionInput.Text(text)             => JsString(text)
      case DecisionInput.Messages(Seq(message)) => messageState(message)
      case DecisionInput.Messages(messages)     => JsArray(messages.map(messageState))
    }

  def asked(questions: Seq[DecisionQuestion]): Seq[Asked] = {
    val names = questions.flatMap(_.name).filter(_.nonEmpty)
    val repeated = names.diff(names.distinct).distinct
    require(
      repeated.isEmpty,
      s"Question names must be unique - ${repeated
          .mkString(", ")} ${if (repeated.size == 1) "repeats" else "repeat"}."
    )

    // an unnamed question takes the first free key of its position
    questions.zipWithIndex
      .foldLeft((Vector.empty[Asked], names.toSet)) {
        case ((done, taken), (question, index)) =>
          val key = question.name.filter(_.nonEmpty).getOrElse {
            val base = s"question_${index + 1}"
            (Iterator
              .single(base) ++ Iterator.from(2).map(n => s"${base}_$n")).find(!taken(_)).get
          }
          (done :+ Asked(key, systemOneQuestion(question), question), taken + key)
      }
      ._1
  }

  def decision(
    response: SystemOneResponse,
    asked: Seq[Asked]
  ): Decision =
    Decision(
      response.model,
      asked.flatMap(a => response.answers.get(a.key).map(decisionAnswer(_, a.original))),
      response.usage.input_tokens.map { in =>
        val out = response.usage.output_tokens.getOrElse(0)
        UsageInfo(inputTokens = in, outputTokens = out, totalTokens = in + out)
      },
      response.requestId
    )

  /** A choice value as System One's option name. */
  def option(value: DecisionValue): String =
    value match {
      case DecisionValue.Text(text) => text
      case DecisionValue.Bool(flag) => flag.toString
    }

  private def messageState(message: DecisionMessage): JsValue =
    DecisionImage.messageState(message.content.map {
      case DecisionContent.InputText(text)    => Left(text)
      case DecisionContent.InputImage(url, _) => Right(url)
    })

  private def imageUrls(input: DecisionInput): Seq[String] =
    input match {
      case DecisionInput.Text(_) => Nil
      case DecisionInput.Messages(messages) =>
        messages.flatMap(_.content).collect { case DecisionContent.InputImage(url, _) => url }
    }

  private def unsupported(
    input: DecisionInput,
    settings: CreateDecisionSettings
  ): Seq[String] = {
    val details = input match {
      case DecisionInput.Messages(messages) =>
        messages.flatMap(_.content).exists {
          case DecisionContent.InputImage(_, detail) => detail.isDefined
          case _                                     => false
        }
      case _ => false
    }
    settings.safetyIdentifier.map(_ => "The safety_identifier").toSeq ++
      (if (details) Seq("the images' detail") else Nil)
  }

  private def systemOneQuestion(question: DecisionQuestion): Question =
    question match {
      case DecisionQuestion.Predicate(instructions, _) =>
        NoulQuestion(instructions)

      case DecisionQuestion.Choice(instructions, choices, _) =>
        val options = choices.map(choice => option(choice.value))
        val repeated = options.diff(options.distinct).distinct
        require(
          repeated.isEmpty,
          s"A choice's options must differ as text on a System One host - ${repeated.mkString(", ")} " +
            "would be one option (a boolean value goes as its text)."
        )
        ChoiceQuestion(
          ListMap(choices.map { choice =>
            option(choice.value) -> choice.description.map[JsValue](JsString(_))
          }: _*),
          Some(JsString(instructions))
        )

      case DecisionQuestion.Score(instructions, levels, _) =>
        ScoreQuestion(
          levels.map {
            case DecisionLevel(label, Some(description)) =>
              Json.obj("label" -> label, "description" -> description)
            case DecisionLevel(label, None) => JsString(label)
          },
          Some(JsString(instructions))
        )
    }

  private def decisionAnswer(
    answer: Answer,
    question: DecisionQuestion
  ): DecisionAnswer = {
    val name = question.name

    answer match {
      case NoulAnswer(probability) =>
        DecisionAnswer.Predicate(name, probability)

      case ChoiceAnswer(choice, confidence, probabilities) =>
        // the values as asked, in the question's order; an option nobody asked for as text
        val asked = question match {
          case DecisionQuestion.Choice(_, choices, _) => choices.map(_.value)
          case _                                      => Nil
        }
        val values = asked.map(value => option(value) -> value).toMap
        def value(option: String) = values.getOrElse(option, DecisionValue.Text(option))
        val ordered = asked.map(option).filter(probabilities.contains) ++
          probabilities.keys.toSeq.filterNot(values.contains).sorted

        DecisionAnswer.Choice(
          name,
          value(choice),
          ordered.map(o => ChoiceProbability(value(o), probabilities(o))),
          confidence
        )

      case ScoreAnswer(score, confidence, legend, probabilities) =>
        val labels = question match {
          case DecisionQuestion.Score(_, levels, _) => levels.map(_.label)
          case _                                    => Nil
        }
        DecisionAnswer.Score(
          name,
          score,
          probabilities.toSeq.sortBy(_._1).map { case (level, probability) =>
            LevelProbability(
              level,
              labels
                .lift(level)
                .orElse(legend.get(level).map(labelOf))
                .getOrElse(level.toString),
              probability
            )
          },
          confidence
        )

      case UnknownAnswer("refusal", _) =>
        DecisionAnswer.Refusal(name)

      case UnknownAnswer(kind, raw) =>
        DecisionAnswer.Unknown(name, kind, raw)
    }
  }

  private def labelOf(level: JsValue): String =
    level match {
      case JsString(text) => text
      case other          => (other \ "label").asOpt[String].getOrElse(Json.stringify(other))
    }
}
