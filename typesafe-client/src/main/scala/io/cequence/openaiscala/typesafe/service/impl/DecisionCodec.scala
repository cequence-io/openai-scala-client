package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.domain.decisions.JsonFormats._
import io.cequence.openaiscala.domain.decisions.{CreateDecisionSettings, Decision}
import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain.{
  DecisionImage,
  DecisionImages,
  DecisionProtocol,
  DecisionProvider,
  Question,
  SystemOneRequest,
  SystemOneResponse
}
import io.cequence.wsclient.JsonUtil.JsonOps
import play.api.libs.json._

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
}

private[impl] object DecisionCodec {

  def apply(provider: DecisionProvider): DecisionCodec =
    (provider.protocol, provider.images) match {
      case (DecisionProtocol.OpenAI, _) => OpenAIDecisionsCodec
      case (DecisionProtocol.SystemOne, DecisionImages.ImagesField) =>
        SystemOneCodec.withImagesField
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
    val withImagesField: DecisionCodec = new DecisionCodec {

      override def body(
        request: SystemOneRequest,
        questions: Seq[(String, Question)]
      ): JsValue =
        DecisionImage.lift(request.state) match {
          case (_, Nil) =>
            SystemOneCodec.body(request, questions)
          case (state, images) =>
            Json.toJson(request.copy(state = state)).as[JsObject] +
              ("images" -> Json.toJson(images))
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
