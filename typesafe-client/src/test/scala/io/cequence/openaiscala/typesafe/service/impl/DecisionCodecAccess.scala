package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.domain.{Question, SystemOneRequest}
import play.api.libs.json.JsObject

/** The codecs' request bodies for specs outside this package. */
object DecisionCodecAccess {

  /** The body TypeSafe's image assets codec writes (`DecisionImages.Assets`). */
  def assetsBody(
    request: SystemOneRequest,
    questions: Seq[(String, Question)]
  ): JsObject =
    DecisionCodec.SystemOneCodec.withAssets.body(request, questions).as[JsObject]
}
