package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.graders.Grader
import io.cequence.openaiscala.domain.graders.JsonFormats._
import io.cequence.openaiscala.service.OpenAIGraderService
import io.cequence.wsclient.JsonUtil.StringAnyMapFormat
import io.cequence.wsclient.ResponseImplicits._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

trait OpenAIGraderServiceImpl extends OpenAIGraderService with OpenAIServiceWSBase {

  override def runGrader(
    grader: Grader,
    modelSample: String,
    item: Map[String, Any]
  ): Future[String] = {
    val body = Json.obj(
      "grader" -> Json.toJson(grader),
      "model_sample" -> modelSample,
      "item" -> Json.toJson(item)(StringAnyMapFormat)
    )

    execPOSTBody(
      EndPoint.graders,
      endPointParam = Some("run"),
      body = body
    ).map(_.string)
  }

  override def validateGrader(
    grader: Grader
  ): Future[Grader] = {
    val body = Json.obj(
      "grader" -> Json.toJson(grader)
    )

    execPOSTBody(
      EndPoint.graders,
      endPointParam = Some("validate"),
      body = body
    ).map { response =>
      val json = response.asSafeJson[JsObject]
      (json \ "grader").asOpt[Grader] match {
        case Some(validatedGrader) => validatedGrader
        case None =>
          throw new OpenAIScalaClientException(
            s"Validated grader not found in response. The response JSON: ${json}"
          )
      }
    }
  }
}
