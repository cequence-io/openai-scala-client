package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.service.JsonSchemaShape
import io.cequence.openaiscala.typesafe.domain._
import play.api.libs.json._

import scala.collection.immutable.ListMap

/**
 * Turns a JSON schema into System One questions and the answers back into a JSON value of that
 * schema - the heart of the OpenAI chat-completion adapter. System One does not generate text,
 * so only CLOSED-VOCABULARY schemas are supported; the planner refuses anything else up front,
 * naming every offending path:
 *
 *   - `boolean` -> a noul question (true when the probability reaches the threshold)
 *   - `string` with `enum` -> a choice question (the winning option)
 *   - `integer` / `number` with a numeric `enum`, or with `minimum` and `maximum` spanning at
 *     most [[SchemaQuestions.MaxScoreLevels]] whole numbers -> a score question over those
 *     values; an integer gets the most likely value, a number the probability-weighted
 *     expected value
 *   - `array` whose `items` are a string `enum` -> one noul per option (multi-select)
 *   - `object` of exactly the `number` properties `top`, `bottom`, `left` and `right`, each a
 *     fraction (`minimum` 0, `maximum` 1 - a `BoundingBox`, or a case class of your own with
 *     `@JsonSchemaRange(0, 1)` edges) -> a box question (TypeSafe's Jev 1.14, with an image in
 *     the request): the edges as fractions of the image, the object's description the
 *     instructions ("the coffee mug in the photo"). The range is the opt-in - four free
 *     numbers with those names (margins, insets) are planned as any other numbers
 *   - any other `object` -> its properties, recursively
 *
 * Questions are named by their paths (`questionName`).
 *
 * The property `description` is the question's instructions (a humanised property name when
 * there is none); `required` is moot since every question is answered. A nullable type
 * (`["string", "null"]`) is treated as its non-null type - System One always answers.
 *
 * '''Confidence fields.''' A `number` property named `<base>_confidence` or `<base>Confidence`
 * (case-sensitive suffix) with a sibling `<base>` in the same object - at any depth - asks for
 * System One's confidence in that sibling's answer instead of a question of its own; both
 * spellings may be declared for one base. It is filled with (rounded to 4 decimals, half-up,
 * and placed right after the base field):
 *
 *   - `boolean`: the probability of the emitted answer - `noul` when it reads as true (at or
 *     above the noul threshold), else `1 - noul`
 *   - string enum / numeric enum or range: the answer's `confidence` (how peaked its
 *     distribution is)
 *   - array of a string enum: the minimum over its options, each as for a boolean - the
 *     weakest in-or-out decision bounds the set
 *   - box: the box's `confidence` (none when the model gives none - the field is left out)
 *   - object: the minimum over every question underneath it (none when a box underneath has
 *     none)
 *
 * A `*_confidence` / `*Confidence` property WITHOUT such a sibling is an ordinary property
 * (and, as a number without levels, refused); one that is not a `number` is refused. When a
 * question under the base has no usable answer, or a box no confidence, the confidence field
 * is left out with a warning saying which.
 */
private[typesafe] object SchemaQuestions {

  private val logger = org.slf4j.LoggerFactory.getLogger("SchemaQuestions")

  private val ConfidenceSuffixes = Seq("_confidence", "Confidence")

  /** The most levels of a score question - a wider numeric enum or range is refused. */
  val MaxScoreLevels: Int = ScoreQuestion.MaxLevels

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

  /** A `{top, bottom, left, right}` object answered by a box question. */
  final case class BoxSlot(question: String) extends Slot

  /**
   * @param confidenceFields
   *   base field -> the confidence fields asking for the confidence in its answer (see the
   *   object's scaladoc), in declaration order
   */
  final case class ObjectSlot(
    fields: Seq[(String, Slot)],
    confidenceFields: Map[String, Seq[String]] = Map.empty
  ) extends Slot

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
      problems += s"${if (path.isEmpty) "<root>" else questionName(path)}: $reason"
      ObjectSlot(Nil)
    }

    def add(
      path: Seq[String],
      question: Question
    ): String = {
      val name = questionName(path)
      // questionName is injective, so this never fires - but an overwrite would leave two
      // fields reading one answer
      require(!questions.contains(name), s"Two schema paths are named '$name'")
      // only a top-level property named "" gets an empty name, which the API refuses
      if (name.isEmpty)
        problems += "<root>: a property with an empty name - System One needs a question name"
      questions += name -> question
      name
    }

    def slot(
      path: Seq[String],
      schema: JsValue
    ): Slot = {
      val description = (schema \ "description").asOpt[String].filter(_.trim.nonEmpty)
      val name = humanize(path.lastOption.getOrElse("value"))
      val instructions = description.getOrElse(name)

      // the instructions without the enum values' descriptions, and those by value
      def valueDescriptions(values: Seq[String]): (String, Map[String, String]) =
        description.fold(name -> Map.empty[String, String]) { text =>
          val (own, byValue) = JsonSchemaShape.splitValueDescriptions(text, values)
          (if (own.trim.isEmpty) name else own, byValue)
        }

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
                // a value's description is its option's criteria
                val (own, byValue) = valueDescriptions(options)
                ChoiceSlot(
                  add(
                    path,
                    ChoiceQuestion(
                      ListMap(
                        options.map(option => option -> byValue.get(option).map(JsString)): _*
                      ),
                      Some(JsString(own))
                    )
                  )
                )
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
                // each option's question with its own description, not every option's
                val (own, byValue) = valueDescriptions(options)
                MultiSelectSlot(options.map { option =>
                  option -> add(
                    path :+ s"[$option]",
                    NoulQuestion(
                      s"Does '$option' apply? ($own)" +
                        byValue.get(option).fold("")(text => s"\n- $option: $text")
                    )
                  )
                })
              case Some(Right(_))      => unsupported(path, "an empty enum")
              case Some(Left(problem)) => unsupported(path, problem)
              case None =>
                unsupported(path, "an array whose items are not a string enum (multi-select)")
            }

          case Some("object") if isBox(schema) =>
            BoxSlot(add(path, BoxQuestion(instructions)))

          case Some("object") =>
            (schema \ "properties").asOpt[JsObject] match {
              case Some(properties) if properties.fields.nonEmpty =>
                val names = properties.fields.map(_._1).toSeq // declaration order
                val baseOf = confidenceFieldBases(names.toSet)

                properties.fields.foreach {
                  case (name, sub)
                      if baseOf.contains(name) && !typeOf(sub).contains("number") =>
                    unsupported(
                      path :+ name,
                      s"a confidence field (of '${baseOf(name)}') must be a number"
                    )
                  case _ => ()
                }

                ObjectSlot(
                  properties.fields.toSeq.collect {
                    case (name, sub) if !baseOf.contains(name) =>
                      name -> slot(path :+ name, sub)
                  },
                  names.filter(baseOf.contains).groupBy(baseOf).map { case (base, fields) =>
                    base -> names.filter(fields.contains)
                  }
                )
              case _ =>
                unsupported(
                  path,
                  "an object without properties (a map or open object) - the questions need fixed fields"
                )
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

    def value(
      slot: Slot,
      path: Seq[String]
    ): JsValue =
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

        case BoxSlot(q) =>
          answer(q) { case BoxAnswer(BoundingBox(top, bottom, left, right), _) =>
            Json.obj("top" -> top, "bottom" -> bottom, "left" -> left, "right" -> right)
          }

        case ObjectSlot(fields, confidenceFields) =>
          JsObject(fields.flatMap { case (name, s) =>
            val confidences = confidenceFields.getOrElse(name, Nil) match {
              case Nil => Nil
              case confidenceNames =>
                confidence(s, answers, noulThreshold) match {
                  case Right(c) =>
                    confidenceNames.map(
                      _ -> JsNumber(BigDecimal(c.bigDecimal.stripTrailingZeros))
                    )
                  case Left(unusable) =>
                    logger.warn(
                      confidenceLeftOutMessage(
                        confidenceNames.map(c => questionName(path :+ c)),
                        unusable,
                        answers
                      )
                    )
                    Nil
                }
            }
            (name -> value(s, path :+ name)) +: confidences
          })
      }

    value(plan.root, Nil).as[JsObject]
  }

  /**
   * A question's name: its path joined with `.` - `customer.is_angry` is `is_angry` inside
   * `customer`, `topics.[payments]` the option `payments` of the multi-select `topics`. A `.`
   * or `\` inside a property name or an option is escaped with a backslash, so distinct paths
   * never share a name: the property `a.b` is `a\.b`, apart from `b` inside `a` (`a.b`).
   */
  private[impl] def questionName(path: Seq[String]): String =
    path.map(_.replace("\\", "\\\\").replace(".", "\\.")).mkString(".")

  /**
   * Why confidence fields are left out: the questions without a usable answer, and apart from
   * them the boxes answered without a confidence (the API may send none).
   */
  private[impl] def confidenceLeftOutMessage(
    fields: Seq[String],
    unusable: Seq[String],
    answers: Map[String, Answer]
  ): String = {
    val (withoutConfidence, unanswered) = unusable.partition { question =>
      answers.get(question).exists {
        case box: BoxAnswer => box.confidence.isEmpty
        case _              => false
      }
    }
    val reasons = Seq(
      "no usable answer for" -> unanswered,
      "no confidence in the box answer to" -> withoutConfidence
    ).collect {
      case (reason, questions) if questions.nonEmpty =>
        s"$reason ${questions.mkString(", ")}"
    }

    s"Leaving out ${fields.mkString(", ")}: ${reasons.mkString("; ")}; " +
      s"answered: ${answers.keys.toSeq.sorted.mkString(", ")}"
  }

  /**
   * The confidence in a slot's answer (see the object scaladoc) rounded to 4 decimals, or the
   * questions under it without a usable answer or, for a box, without a confidence.
   */
  private[impl] def confidence(
    slot: Slot,
    answers: Map[String, Answer],
    noulThreshold: Double
  ): Either[Seq[String], BigDecimal] = {
    def noul(question: String) = answers.get(question).collect { case a: NoulAnswer =>
      if (a.isYes(noulThreshold)) a.noul else 1 - a.noul
    }

    def perQuestion(slot: Slot): Seq[(String, Option[Double])] =
      slot match {
        case NoulSlot(q) => Seq(q -> noul(q))
        case ChoiceSlot(q) =>
          Seq(q -> answers.get(q).collect { case a: ChoiceAnswer => a.confidence })
        case ScoreSlot(q, _, _) =>
          Seq(q -> answers.get(q).collect { case a: ScoreAnswer => a.confidence })
        case MultiSelectSlot(options) => options.map { case (_, q) => q -> noul(q) }
        case BoxSlot(q) =>
          Seq(q -> answers.get(q).collect { case a: BoxAnswer => a.confidence }.flatten)
        case ObjectSlot(fields, _) => fields.flatMap { case (_, s) => perQuestion(s) }
      }

    val confidences = perQuestion(slot)
    val unanswered = confidences.collect { case (q, None) => q }

    if (unanswered.nonEmpty || confidences.isEmpty) Left(unanswered)
    else
      Right(
        BigDecimal(confidences.flatMap(_._2).min).setScale(4, BigDecimal.RoundingMode.HALF_UP)
      )
  }

  /**
   * The confidence fields among an object's property names -> their base: `<base>_confidence`
   * / `<base>Confidence` whose `<base>` is a sibling that is not a confidence field itself.
   */
  private[impl] def confidenceFieldBases(names: Set[String]): Map[String, String] = {
    def baseOf(name: String): Option[String] =
      ConfidenceSuffixes.collectFirst {
        case suffix if name.endsWith(suffix) && name.length > suffix.length =>
          name.dropRight(suffix.length)
      }.filter(names.contains)

    // a base is shorter than its confidence field, so this recursion terminates
    def isConfidence(name: String): Boolean =
      baseOf(name).exists(base => !isConfidence(base))

    names.collect { case name if isConfidence(name) => name -> baseOf(name).get }.toMap
  }

  private val BoxEdges = Set("top", "bottom", "left", "right")

  // an object of exactly the four edges of a bounding box, each a `number` declared a fraction
  // of the image (`minimum` 0, `maximum` 1 - `BoundingBox` derives them): the range is the
  // opt-in, since four numbers with these names may mean something else (margins, insets) -
  // those stay ordinary numbers (refused when free, score questions with a range or an enum)
  private def isBox(schema: JsValue): Boolean =
    (schema \ "properties").asOpt[JsObject].exists { properties =>
      properties.keys == BoxEdges && properties.values.forall(isFraction)
    }

  private def isFraction(schema: JsValue): Boolean =
    typeOf(schema).contains("number") &&
      (schema \ "minimum").asOpt[Double].contains(0d) &&
      (schema \ "maximum").asOpt[Double].contains(1d) &&
      (schema \ "enum").toOption.isEmpty

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
        if (numbers.size != values.value.size) Left("a numeric enum with non-numeric values")
        else if (numbers.distinct.size > MaxScoreLevels)
          Left(
            s"a numeric enum with ${numbers.distinct.size} values - a score allows at most " +
              s"$MaxScoreLevels levels"
          )
        else Right(numbers.distinct.sorted)

      case None =>
        (
          (schema \ "minimum").asOpt[BigDecimal],
          (schema \ "maximum").asOpt[BigDecimal]
        ) match {
          case (Some(min), Some(max)) if !min.isWhole || !max.isWhole =>
            Left("minimum / maximum must be whole numbers")
          case (Some(min), Some(max)) if max < min =>
            Left("maximum is below minimum")
          case (Some(min), Some(max)) if max - min + 1 > MaxScoreLevels =>
            Left(
              s"a minimum..maximum range wider than $MaxScoreLevels values - a score allows at " +
                s"most $MaxScoreLevels levels"
            )
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
