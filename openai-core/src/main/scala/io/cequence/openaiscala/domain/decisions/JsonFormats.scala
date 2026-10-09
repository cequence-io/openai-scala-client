package io.cequence.openaiscala.domain.decisions

import io.cequence.openaiscala.domain.responsesapi.JsonFormats.usageInfoFormat
import io.cequence.openaiscala.domain.responsesapi.UsageInfo
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
 * The JSON of the Decisions API: the request body written, the answers read leniently (a
 * future answer type is kept as [[DecisionAnswer.Unknown]]).
 */
object JsonFormats {

  implicit val decisionValueFormat: Format[DecisionValue] = Format(
    Reads {
      case JsString(text)  => JsSuccess(DecisionValue.Text(text))
      case JsBoolean(flag) => JsSuccess(DecisionValue.Bool(flag))
      case other           => JsError(s"A choice value is text or a boolean, got $other.")
    },
    Writes {
      case DecisionValue.Text(text) => JsString(text)
      case DecisionValue.Bool(flag) => JsBoolean(flag)
    }
  )

  private implicit val decisionContentWrites: Writes[DecisionContent] = Writes {
    case DecisionContent.InputText(text) =>
      Json.obj("type" -> "input_text", "text" -> text)
    case DecisionContent.InputImage(imageUrl, detail) =>
      Json.obj("type" -> "input_image", "image_url" -> imageUrl) ++
        optional("detail", detail.map(_.toString))
  }

  implicit val decisionInputWrites: Writes[DecisionInput] = Writes {
    case DecisionInput.Text(text) =>
      JsString(text)
    case DecisionInput.Messages(messages) =>
      JsArray(
        messages.map(message => Json.obj("role" -> "user", "content" -> message.content))
      )
  }

  implicit val decisionQuestionWrites: OWrites[DecisionQuestion] = OWrites { question =>
    val ofType = question match {
      case _: DecisionQuestion.Predicate =>
        Json.obj("type" -> "predicate")
      case choice: DecisionQuestion.Choice =>
        Json.obj(
          "type" -> "choice",
          "choices" -> choice.choices.map { option =>
            Json.obj("value" -> option.value) ++ optional("description", option.description)
          }
        )
      case score: DecisionQuestion.Score =>
        Json.obj(
          "type" -> "score",
          "levels" -> score.levels.map { level =>
            Json.obj("label" -> level.label) ++ optional("description", level.description)
          }
        )
    }

    ofType ++ Json.obj("instructions" -> question.instructions) ++
      optional("name", question.name)
  }

  private implicit val choiceProbabilityReads: Reads[ChoiceProbability] =
    Json.reads[ChoiceProbability]

  private implicit val levelProbabilityReads: Reads[LevelProbability] =
    Json.reads[LevelProbability]

  implicit val decisionAnswerReads: Reads[DecisionAnswer] = Reads { json =>
    val name = (json \ "name").asOpt[String]

    (json \ "type").validate[String].flatMap {
      case "predicate" =>
        (json \ "probability").validate[Double].map(DecisionAnswer.Predicate(name, _))

      case "choice" =>
        for {
          choice <- (json \ "choice").validate[DecisionValue]
          probabilities <- (json \ "probabilities").validate[Seq[ChoiceProbability]]
          confidence <- (json \ "confidence").validate[Double]
        } yield DecisionAnswer.Choice(name, choice, probabilities, confidence)

      case "score" =>
        for {
          score <- (json \ "score").validate[Double]
          probabilities <- (json \ "probabilities").validate[Seq[LevelProbability]]
          confidence <- (json \ "confidence").validate[Double]
        } yield DecisionAnswer.Score(name, score, probabilities, confidence)

      case "refusal" =>
        JsSuccess(DecisionAnswer.Refusal(name))

      case other =>
        json.validate[JsObject].map(DecisionAnswer.Unknown(name, other, _))
    }
  }

  // the request id is a header, read by the service
  implicit val decisionReads: Reads[Decision] = (
    (__ \ "model").read[String] and
      (__ \ "answers").read[Seq[DecisionAnswer]] and
      (__ \ "usage").readNullable[UsageInfo]
  )(
    (
      model,
      answers,
      usage
    ) => Decision(model, answers, usage)
  )

  /** The body of `POST /v1/decisions` (`gpt-6-luna` when the settings name no model). */
  def createDecisionBody(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings
  ): JsObject =
    Json.obj(
      "model" -> settings.model.getOrElse[String](CreateDecisionSettings.DefaultModel),
      "input" -> input,
      "questions" -> questions
    ) ++ optional("safety_identifier", settings.safetyIdentifier)

  private def optional(
    key: String,
    value: Option[String]
  ): JsObject =
    value.fold(Json.obj())(text => Json.obj(key -> text))
}
