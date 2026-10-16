package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.domain.decisions.JsonFormats._
import io.cequence.openaiscala.domain.decisions.{CreateDecisionSettings, Decision}
import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  BoxQuestion,
  ChoiceQuestion,
  DecisionImage,
  DecisionImages,
  DecisionProtocol,
  DecisionProvider,
  NoulQuestion,
  Question,
  ScoreQuestion,
  SystemOneRequest,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.DecisionProviderSettings
import io.cequence.wsclient.JsonUtil.JsonOps
import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.collection.mutable

/** How a request goes out and its answers come back in a host's wire format. */
private[impl] trait DecisionCodec {

  /** The request body, the questions in the given order. */
  def body(
    request: SystemOneRequest,
    questions: Seq[(String, Question)]
  ): JsValue

  /** The answers of a response to the questions asked. */
  def response(
    json: JsValue,
    questions: Seq[(String, Question)]
  ): SystemOneResponse

  /** The headers a request with this body needs - e.g. an opt-in for what it carries. */
  def headers(body: JsValue): Seq[(String, String)] = Nil
}

private[impl] object DecisionCodec {

  /** The codec of a request for this model. */
  def apply(
    provider: DecisionProvider,
    model: String
  ): DecisionCodec =
    (provider.protocolFor(model), provider.images) match {
      case (DecisionProtocol.OpenAI, _)    => OpenAIDecisionsCodec
      case (DecisionProtocol.ScaleDown, _) => ScaleDownCodec
      case (DecisionProtocol.SystemOne, DecisionImages.ImagesField) =>
        SystemOneCodec.withImagesField
      case (DecisionProtocol.SystemOne, DecisionImages.ImageObjects) =>
        SystemOneCodec.withImageObjects
      case (DecisionProtocol.SystemOne, DecisionImages.Assets) =>
        SystemOneCodec.withAssets
      case (DecisionProtocol.SystemOne, _) => SystemOneCodec
    }

  /** TypeSafe's own format, which the domain types mirror. */
  object SystemOneCodec extends DecisionCodec {

    override def body(
      request: SystemOneRequest,
      questions: Seq[(String, Question)]
    ): JsValue = Json.toJson(request)

    override def response(
      json: JsValue,
      questions: Seq[(String, Question)]
    ): SystemOneResponse = json.asSafe[SystemOneResponse]

    /**
     * The same, the image parts of the state lifted into a top-level `images` array
     * (`DecisionImages.ImagesField`) - a state without any goes out as it is.
     */
    val withImagesField: DecisionCodec = liftingImages(Json.toJson(_))

    /**
     * The same, each lifted image an `{"mediaType", "base64"}` object
     * (`DecisionImages.ImageObjects`, SubQ).
     */
    val withImageObjects: DecisionCodec =
      liftingImages(images => JsArray(images.map(DecisionImage.mediaObject)))

    /**
     * The same, every image part - in the state and in the questions - replaced by a
     * placeholder and listed under it in a top-level `assets` map, the request opted in to
     * images with TypeSafe's header when it carries one or a box question
     * (`DecisionImages.Assets`).
     */
    val withAssets: DecisionCodec = new DecisionCodec {

      override def body(
        request: SystemOneRequest,
        questions: Seq[(String, Question)]
      ): JsValue = {
        val json = SystemOneCodec.body(request, questions).as[JsObject]
        // one placeholder per image, the same for the same image - a name-based UUID of its
        // data URL, which no text holds by accident
        val placeholders = mutable.LinkedHashMap.empty[String, String]
        def placeholder(url: String) =
          placeholders.getOrElseUpdate(
            url,
            UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString
          )

        val state = DecisionImage.replaceImages((json \ "state").get, placeholder)
        val asked = DecisionImage.replaceImages((json \ "questions").get, placeholder)

        if (placeholders.isEmpty) json
        else
          json ++ Json.obj(
            "state" -> state,
            "questions" -> asked,
            "assets" -> JsObject(placeholders.toSeq.map { case (url, key) =>
              key -> DecisionImage.asset(url)
            })
          )
      }

      override def response(
        json: JsValue,
        questions: Seq[(String, Question)]
      ): SystemOneResponse = SystemOneCodec.response(json, questions)

      override def headers(body: JsValue): Seq[(String, String)] = {
        val boxes = (body \ "questions")
          .asOpt[JsObject]
          .exists(_.values.exists(q => (q \ "type").asOpt[String].contains("box")))
        if ((body \ "assets").isDefined || boxes)
          Seq(DecisionProviderSettings.TypeSafeImagesHeader -> "true")
        else Nil
      }
    }

    // the state's image parts lifted into a top-level `images` array, written by `images`
    private def liftingImages(images: Seq[String] => JsValue): DecisionCodec =
      new DecisionCodec {

        override def body(
          request: SystemOneRequest,
          questions: Seq[(String, Question)]
        ): JsValue =
          DecisionImage.lift(request.state) match {
            case (_, Nil) =>
              SystemOneCodec.body(request, questions)
            case (state, urls) =>
              Json.toJson(request.copy(state = state)).as[JsObject] + ("images" -> images(
                urls
              ))
          }

        override def response(
          json: JsValue,
          questions: Seq[(String, Question)]
        ): SystemOneResponse = SystemOneCodec.response(json, questions)
      }
  }
}

/** System One's questions and answers on OpenAI's Decisions API ([[SystemOneToOpenAI]]). */
private[impl] object OpenAIDecisionsCodec extends DecisionCodec {

  override def body(
    request: SystemOneRequest,
    questions: Seq[(String, Question)]
  ): JsValue =
    createDecisionBody(
      SystemOneToOpenAI.input(request.state),
      questions.map { case (name, question) => SystemOneToOpenAI.question(name, question) },
      CreateDecisionSettings(Some(request.model))
    )

  override def response(
    json: JsValue,
    questions: Seq[(String, Question)]
  ): SystemOneResponse =
    SystemOneToOpenAI.response(json.asSafe[Decision], questions)
}

/**
 * System One in text only, as ScaleDown reads it ([[DecisionProtocol.ScaleDown]], whose
 * scaladoc lists the rules).
 */
private[impl] object ScaleDownCodec extends DecisionCodec {

  // ScaleDown's own state - text, a base64 file or both - which goes as it is
  private val StateFields = Set("text", "document", "document_mime_type")

  override def body(
    request: SystemOneRequest,
    questions: Seq[(String, Question)]
  ): JsValue =
    Json.obj(
      "state" -> state(request.state),
      "model" -> request.model,
      "questions" -> JsObject(questions.map { case (name, question) =>
        name -> this.question(question)
      })
    )

  override def response(
    json: JsValue,
    questions: Seq[(String, Question)]
  ): SystemOneResponse = DecisionCodec.SystemOneCodec.response(json, questions)

  /**
   * @throws IllegalArgumentException
   *   for a state with more than one image - ScaleDown reads one document per request
   */
  def state(json: JsValue): JsValue =
    json match {
      case text: JsString => text

      case own: JsObject
          if own.keys.nonEmpty && own.keys.subsetOf(StateFields) &&
            own.values.forall(_.isInstanceOf[JsString]) =>
        own

      case other =>
        DecisionImage.lift(other) match {
          case (marked, Nil) =>
            JsString(text(marked))

          case (marked, Seq(url)) =>
            val image = DecisionImage.mediaObject(url)
            Json.obj(
              "text" -> text(marked),
              "document" -> image.value("base64"),
              "document_mime_type" -> image.value("mediaType")
            )

          case (_, urls) =>
            throw new IllegalArgumentException(
              s"ScaleDown reads one image per request (its state's document), got ${urls.size} - " +
                "ask about each image in a request of its own."
            )
        }
    }

  private def question(question: Question): JsObject =
    question match {
      case noul: NoulQuestion =>
        Json.obj(
          "type" -> "noul",
          "instructions" -> SystemOneToOpenAI.noulInstructions(noul, text)
        )

      case ChoiceQuestion(criteria, instructions) =>
        Json.obj(
          "type" -> "choice",
          "instructions" -> instructions.map(text).getOrElse[String](""),
          "criteria" -> JsObject(criteria.toSeq.map { case (option, description) =>
            option -> JsString(description.map(text).getOrElse(""))
          })
        )

      case ScoreQuestion(levels, instructions) =>
        Json.obj(
          "type" -> "score",
          "instructions" -> instructions.map(text).getOrElse[String](""),
          "criteria" -> levels.map(level)
        )

      case _: BoxQuestion =>
        throw new IllegalArgumentException(
          "ScaleDown's classify takes no box question - ask TypeSafe's Jev 1.14 " +
            "(DecisionProviderSettings.typeSafe, jev-1.14.0)."
        )
    }

  // a score level: `{label, description}` as `label: description`, other JSON as its text
  private def level(json: JsValue): String =
    ((json \ "label").asOpt[String], (json \ "description").asOpt[String]) match {
      case (Some(label), Some(description)) => s"$label: $description"
      case (Some(label), None)              => label
      case _                                => text(json)
    }

  // text as it is, an array of texts (a chat message's parts) one per line, any other JSON
  // pretty-printed
  private def text(json: JsValue): String =
    json match {
      case JsString(text) => text
      case JsArray(values) if values.forall(_.isInstanceOf[JsString]) =>
        values.collect { case JsString(text) => text }.mkString("\n")
      case other => Json.prettyPrint(other)
    }
}
