package io.cequence.openaiscala.typesafe

import io.cequence.openaiscala.typesafe.domain._
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * Play JSON codecs for the System One wire format (OpenAPI 0.2.0 at
 * `https://api.typesafe.ai/openapi.json`). Questions are written the way the official SDKs
 * write them - `type` first, unset `instructions` omitted, an undescribed choice option as
 * `null` - and answers are read leniently: unknown fields are ignored and an unknown answer
 * `type` becomes [[UnknownAnswer]].
 */
object JsonFormats {

  private def optField(
    name: String,
    value: Option[JsValue]
  ): JsObject =
    value.fold(Json.obj())(v => Json.obj(name -> v))

  // (json \ name).validate loses the field name from the error path when the field is absent,
  // and a JsSuccess carries a path that flatMap prepends to whatever follows it - read through
  // these so every error, present or missing, is reported at exactly its own path
  private def at[T](
    name: String,
    result: JsResult[T]
  ): JsResult[T] =
    result match {
      case JsSuccess(value, _) => JsSuccess(value)
      case error: JsError      => error.repath(JsPath \ name)
    }

  private def field[T: Reads](
    json: JsValue,
    name: String
  ): JsResult[T] =
    (json \ name) match {
      case JsDefined(value) => at(name, value.validate[T])
      case _: JsUndefined   => JsError(JsPath \ name, "error.path.missing")
    }

  private def fieldOpt[T: Reads](
    json: JsValue,
    name: String
  ): JsResult[Option[T]] =
    (json \ name) match {
      case JsDefined(JsNull) => JsSuccess(None)
      case JsDefined(value)  => at(name, value.validate[T].map(Some(_)))
      case _: JsUndefined    => JsSuccess(None)
    }

  implicit val noulCriteriaFormat: Format[NoulCriteria] = Format(
    Reads { json =>
      for {
        yes <- fieldOpt[JsValue](json, "true")
        no <- fieldOpt[JsValue](json, "false")
      } yield NoulCriteria(yes, no)
    },
    Writes(criteria => optField("true", criteria.yes) ++ optField("false", criteria.no))
  )

  implicit val questionFormat: Format[Question] = Format(
    Reads { json =>
      def instructions = fieldOpt[JsValue](json, "instructions")

      field[String](json, "type").flatMap {
        case "noul" =>
          for {
            i <- instructions
            c <- fieldOpt[NoulCriteria](json, "criteria")
          } yield NoulQuestion(i, c)

        case "choice" =>
          for {
            c <- field[JsObject](json, "criteria")
            i <- instructions
          } yield ChoiceQuestion(
            ListMap(c.fields.toSeq.map { case (label, description) =>
              label -> (if (description == JsNull) None else Some(description))
            }: _*),
            i
          )

        case "score" =>
          for {
            c <- field[Seq[JsValue]](json, "criteria")
            i <- instructions
          } yield ScoreQuestion(c, i)

        case other =>
          JsError(JsPath \ "type", s"Unknown question type '$other'.")
      }
    },
    Writes {
      case NoulQuestion(instructions, criteria) =>
        Json.obj("type" -> "noul") ++
          optField("instructions", instructions) ++
          optField("criteria", criteria.map(Json.toJson(_)))

      case ChoiceQuestion(criteria, instructions) =>
        Json.obj("type" -> "choice") ++
          optField("instructions", instructions) ++
          Json.obj(
            "criteria" -> JsObject(criteria.toSeq.map { case (label, description) =>
              label -> description.getOrElse[JsValue](JsNull)
            })
          )

      case ScoreQuestion(criteria, instructions) =>
        Json.obj("type" -> "score") ++
          optField("instructions", instructions) ++
          Json.obj("criteria" -> criteria)
    }
  )

  implicit val systemOneRequestFormat: Format[SystemOneRequest] = Format(
    Reads { json =>
      for {
        state <- field[JsValue](json, "state")
        model <- field[String](json, "model")
        questions <- field[Map[String, Question]](json, "questions")
      } yield SystemOneRequest(state, model, questions)
    },
    Writes { request =>
      Json.obj(
        "state" -> request.state,
        "model" -> request.model,
        "questions" -> JsObject(request.questions.toSeq.map { case (name, question) =>
          name -> Json.toJson(question)
        })
      )
    }
  )

  // "legend" / "probabilities" of a score answer are keyed by the level ("0", "1", ...) - read
  // them as ints, and point at the offending key when one is not
  private def intKeyed[V: Reads](
    json: JsValue,
    name: String
  ): JsResult[Map[Int, V]] =
    field[JsObject](json, name).flatMap { obj =>
      at(
        name,
        obj.fields.foldLeft[JsResult[Map[Int, V]]](JsSuccess(Map.empty)) {
          case (acc, (key, value)) =>
            for {
              map <- acc
              level <- Try(key.toInt).fold(
                _ => JsError(JsPath \ key, "expected an integer score level"),
                JsSuccess(_)
              )
              v <- at(key, value.validate[V])
            } yield map + (level -> v)
        }
      )
    }

  private def intKeyedJson[V: Writes](map: Map[Int, V]): JsObject =
    JsObject(
      map.toSeq.sortBy(_._1).map { case (level, v) => level.toString -> Json.toJson(v) }
    )

  implicit val answerFormat: Format[Answer] = Format(
    Reads { json =>
      field[String](json, "type").flatMap {
        case "noul" =>
          field[Double](json, "noul").map(NoulAnswer(_))

        case "choice" =>
          for {
            choice <- field[String](json, "choice")
            confidence <- field[Double](json, "confidence")
            probabilities <- field[Map[String, Double]](json, "probabilities")
          } yield ChoiceAnswer(choice, confidence, probabilities)

        case "score" =>
          for {
            score <- field[Double](json, "score")
            confidence <- field[Double](json, "confidence")
            legend <- intKeyed[JsValue](json, "legend")
            probabilities <- intKeyed[Double](json, "probabilities")
          } yield ScoreAnswer(score, confidence, legend, probabilities)

        case other =>
          json.validate[JsObject].map(UnknownAnswer(other, _))
      }
    },
    Writes {
      case NoulAnswer(noul) =>
        Json.obj("type" -> "noul", "noul" -> noul)

      case ChoiceAnswer(choice, confidence, probabilities) =>
        Json.obj(
          "type" -> "choice",
          "choice" -> choice,
          "probabilities" -> probabilities,
          "confidence" -> confidence
        )

      case ScoreAnswer(score, confidence, legend, probabilities) =>
        Json.obj(
          "type" -> "score",
          "score" -> score,
          "legend" -> intKeyedJson(legend),
          "probabilities" -> intKeyedJson(probabilities),
          "confidence" -> confidence
        )

      case UnknownAnswer(kind, raw) =>
        raw + ("type" -> JsString(kind))
    }
  )

  implicit val usageFormat: Format[Usage] = Format(
    Reads { json =>
      for {
        input <- fieldOpt[Int](json, "input_tokens")
        output <- fieldOpt[Int](json, "output_tokens")
      } yield Usage(input, output)
    },
    Writes { usage =>
      optField("input_tokens", usage.input_tokens.map(JsNumber(_))) ++
        optField("output_tokens", usage.output_tokens.map(JsNumber(_)))
    }
  )

  // requestId is transport metadata (a response header), not part of the JSON body
  implicit val systemOneResponseFormat: Format[SystemOneResponse] = Format(
    Reads { json =>
      for {
        model <- field[String](json, "model")
        answers <- field[Map[String, Answer]](json, "answers")
        usage <- field[Usage](json, "usage")
      } yield SystemOneResponse(model, answers, usage)
    },
    Writes { response =>
      Json.obj(
        "model" -> response.model,
        "answers" -> JsObject(response.answers.toSeq.map { case (name, answer) =>
          name -> Json.toJson(answer)
        }),
        "usage" -> response.usage
      )
    }
  )

  implicit val modelMetadataFormat: Format[ModelMetadata] = Format(
    Reads { json =>
      for {
        name <- field[String](json, "name")
        description <- field[String](json, "description")
        releaseDate <- field[String](json, "release_date")
      } yield ModelMetadata(name, description, releaseDate)
    },
    Writes { model =>
      Json.obj(
        "name" -> model.name,
        "description" -> model.description,
        "release_date" -> model.release_date
      )
    }
  )
}
