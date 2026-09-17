package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.domain._
import play.api.libs.json._

/**
 * Turns a JSON schema into System One questions and the answers back into a JSON value of that
 * schema - the heart of the OpenAI chat-completion adapter. System One does not generate text,
 * so only CLOSED-VOCABULARY schemas are supported; the planner refuses anything else up front,
 * naming every offending path:
 *
 *   - `boolean` -> a noul question (true when the probability reaches the threshold)
 *   - `string` with `enum` -> a choice question (the winning option)
 *   - `integer` / `number` with a numeric `enum`, or with `minimum` and `maximum` spanning at
 *     most [[SchemaQuestions.MaxRangeLevels]] whole numbers -> a score question over those
 *     values; an integer gets the most likely value, a number the probability-weighted
 *     expected value
 *   - `array` whose `items` are a string `enum` -> one noul per option (multi-select)
 *   - `object` -> its properties, recursively (question names are the dotted paths)
 *
 * The property `description` is the question's instructions (a humanised property name when
 * there is none); `required` is moot since every question is answered. A nullable type
 * (`["string", "null"]`) is treated as its non-null type - System One always answers.
 */
private[typesafe] object SchemaQuestions {

  /** The widest `minimum`..`maximum` range turned into score levels. */
  val MaxRangeLevels = 32

  sealed trait Slot

  final case class NoulSlot(question: String) extends Slot

  final case class ChoiceSlot(question: String) extends Slot

  /** `levels(i)` is the value of score level `i`. */
  final case class ScoreSlot(
    question: String,
    levels: Seq[BigDecimal],
    integer: Boolean
  ) extends Slot

  /** option -> the noul question asking whether it applies. */
  final case class MultiSelectSlot(options: Seq[(String, String)]) extends Slot

  final case class ObjectSlot(fields: Seq[(String, Slot)]) extends Slot

  final case class Plan(
    root: ObjectSlot,
    questions: Map[String, Question]
  )

  /**
   * @throws IllegalArgumentException
   *   when the schema (or a part of it) cannot be expressed as System One questions
   */
  def plan(schema: JsValue): Plan = {
    val questions = scala.collection.mutable.LinkedHashMap.empty[String, Question]
    val problems = scala.collection.mutable.ListBuffer.empty[String]

    def unsupported(
      path: Seq[String],
      reason: String
    ): Slot = {
      problems += s"${if (path.isEmpty) "<root>" else path.mkString(".")}: $reason"
      ObjectSlot(Nil)
    }

    def add(
      path: Seq[String],
      question: Question
    ): String = {
      val name = path.mkString(".")
      questions += name -> question
      name
    }

    def slot(
      path: Seq[String],
      schema: JsValue
    ): Slot = {
      val description = (schema \ "description").asOpt[String].filter(_.trim.nonEmpty)
      val instructions = description.getOrElse(humanize(path.lastOption.getOrElse("value")))

      if (
        Seq("anyOf", "oneOf", "allOf", "$ref", "not")
          .exists(k => (schema \ k).toOption.isDefined)
      )
        unsupported(path, "anyOf / oneOf / allOf / $ref are not supported")
      else
        typeOf(schema) match {
          case Some("boolean") =>
            NoulSlot(add(path, NoulQuestion(instructions)))

          case Some("string") =>
            stringEnum(schema) match {
              case Some(Right(options)) if options.size > ChoiceQuestion.MaxOptions =>
                unsupported(
                  path,
                  s"an enum with ${options.size} values - a choice allows at most " +
                    s"${ChoiceQuestion.MaxOptions}"
                )
              case Some(Right(options)) if options.nonEmpty =>
                ChoiceSlot(add(path, ChoiceQuestion.ofLabels(instructions, options: _*)))
              case Some(Right(_))      => unsupported(path, "an empty enum")
              case Some(Left(problem)) => unsupported(path, problem)
              case None =>
                unsupported(
                  path,
                  "a free-form string - System One does not generate text; use a string enum"
                )
            }

          case Some(t @ ("integer" | "number")) =>
            numericLevels(schema) match {
              case Right(levels) if levels.nonEmpty =>
                ScoreSlot(
                  // levels must be text / object / array on the wire - the values are kept here
                  add(
                    path,
                    ScoreQuestion(
                      levels.map(l => JsString(l.bigDecimal.stripTrailingZeros.toPlainString)),
                      Some(JsString(instructions))
                    )
                  ),
                  levels,
                  integer = t == "integer"
                )
              case Right(_)      => unsupported(path, "an empty enum")
              case Left(problem) => unsupported(path, problem)
            }

          case Some("array") =>
            (schema \ "items").toOption.flatMap(stringEnum) match {
              case Some(Right(options)) if options.nonEmpty =>
                MultiSelectSlot(options.map { option =>
                  option -> add(
                    path :+ s"[$option]",
                    NoulQuestion(s"Does '$option' apply? ($instructions)")
                  )
                })
              case Some(Right(_))      => unsupported(path, "an empty enum")
              case Some(Left(problem)) => unsupported(path, problem)
              case None =>
                unsupported(path, "an array whose items are not a string enum (multi-select)")
            }

          case Some("object") =>
            (schema \ "properties").asOpt[JsObject] match {
              case Some(properties) if properties.fields.nonEmpty =>
                ObjectSlot(properties.fields.toSeq.map { case (name, sub) =>
                  name -> slot(path :+ name, sub)
                })
              case _ => unsupported(path, "an object without properties")
            }

          case Some(other) => unsupported(path, s"type '$other' is not supported")
          case None        => unsupported(path, "no type")
        }
    }

    val root = slot(Nil, schema) match {
      case o: ObjectSlot => o
      case _ =>
        problems += "<root>: the schema must be an object"
        ObjectSlot(Nil)
    }

    require(
      problems.isEmpty,
      "The JSON schema cannot be answered by System One (only booleans, string enums, " +
        "numeric enums / small ranges, arrays of string enums and objects of those are):\n  " +
        problems.mkString("\n  ")
    )

    Plan(root, questions.toMap)
  }

  /** Folds the answers back into a JSON value of the schema. */
  def assemble(
    plan: Plan,
    answers: Map[String, Answer],
    noulThreshold: Double
  ): JsObject = {

    def answer[A](
      name: String
    )(
      pf: PartialFunction[Answer, A]
    ): A =
      answers.get(name) match {
        case Some(a) if pf.isDefinedAt(a) => pf(a)
        case Some(a) =>
          throw new IllegalStateException(s"Unexpected answer kind for '$name': $a")
        case None =>
          throw new IllegalStateException(
            s"No answer for '$name'; got: ${answers.keys.mkString(", ")}"
          )
      }

    def value(slot: Slot): JsValue =
      slot match {
        case NoulSlot(q) =>
          JsBoolean(answer(q) { case a: NoulAnswer => a.isYes(noulThreshold) })

        case ChoiceSlot(q) =>
          JsString(answer(q) { case a: ChoiceAnswer => a.choice })

        case ScoreSlot(q, levels, integer) =>
          answer(q) {
            case a: ScoreAnswer if integer => JsNumber(levels(a.mostLikelyLevel))
            case a: ScoreAnswer =>
              JsNumber(a.probabilities.foldLeft(BigDecimal(0)) { case (acc, (level, p)) =>
                acc + levels(level) * BigDecimal(p)
              })
          }

        case MultiSelectSlot(options) =>
          JsArray(options.collect {
            case (option, q) if answer(q) { case a: NoulAnswer => a.isYes(noulThreshold) } =>
              JsString(option)
          })

        case ObjectSlot(fields) =>
          JsObject(fields.map { case (name, s) => name -> value(s) })
      }

    value(plan.root).as[JsObject]
  }

  // `type` is a string or an array of them (nullable types) - the non-null one wins
  private def typeOf(schema: JsValue): Option[String] =
    (schema \ "type").toOption.flatMap {
      case JsString(t) => Some(t)
      case JsArray(ts) =>
        ts.collect { case JsString(t) if t != "null" => t }.toSeq match {
          case Seq(single) => Some(single)
          case _           => None
        }
      case _ => None
    }

  // None = no enum; Left = an enum with non-string values (refused, not silently trimmed);
  // duplicates are folded so the option count, the questions and the output agree
  private def stringEnum(schema: JsValue): Option[Either[String, Seq[String]]] =
    (schema \ "enum").asOpt[JsArray].map { values =>
      val strings = values.value.toSeq.collect { case JsString(s) => s }
      if (strings.size == values.value.size) Right(strings.distinct)
      else Left("a string enum with non-string values")
    }

  private def numericLevels(schema: JsValue): Either[String, Seq[BigDecimal]] =
    (schema \ "enum").asOpt[JsArray] match {
      case Some(values) =>
        val numbers = values.value.toSeq.collect { case JsNumber(n) => n }
        if (numbers.size == values.value.size) Right(numbers.distinct.sorted)
        else Left("a numeric enum with non-numeric values")

      case None =>
        (
          (schema \ "minimum").asOpt[BigDecimal],
          (schema \ "maximum").asOpt[BigDecimal]
        ) match {
          case (Some(min), Some(max)) if !min.isWhole || !max.isWhole =>
            Left("minimum / maximum must be whole numbers")
          case (Some(min), Some(max)) if max < min =>
            Left("maximum is below minimum")
          case (Some(min), Some(max)) if max - min + 1 > MaxRangeLevels =>
            Left(s"a minimum..maximum range wider than $MaxRangeLevels values")
          case (Some(min), Some(max)) =>
            // iterate in BigDecimal - the bounds may sit anywhere on the number line
            Right(Iterator.iterate(min)(_ + 1).takeWhile(_ <= max).toVector)
          case _ =>
            Left(
              "a number without an enum or a minimum..maximum range - System One rates on a " +
                "fixed set of levels"
            )
        }
    }

  /** `is_urgent` / `isUrgent` / `is-urgent` -> "Is urgent". */
  private[impl] def humanize(name: String): String = {
    val words = name
      .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
      .split("[_\\-\\s]+")
      .filter(_.nonEmpty)
      .map(_.toLowerCase)
    words.headOption.fold(name)(h => (h.capitalize +: words.tail).mkString(" "))
  }
}
