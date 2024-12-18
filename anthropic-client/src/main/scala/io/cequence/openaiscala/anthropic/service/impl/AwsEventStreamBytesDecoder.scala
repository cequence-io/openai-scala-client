package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Flow

import java.util.Base64
import play.api.libs.json.{JsString, JsValue, Json}

object AwsEventStreamBytesDecoder {
  def flow: Flow[JsValue, JsValue, NotUsed] = Flow[JsValue].map { eventJson =>
    // eventJson might look like:
    // { ":message-type":"event", ":event-type":"...", "bytes":"base64string" }

    val base64Str = (eventJson \ "bytes").asOpt[String]
    base64Str match {
      case Some(encoded) =>
        val decoded = Base64.getDecoder.decode(encoded)
        Json.parse(decoded)
      case None =>
        // If there's no "bytes" field, return the original JSON (or handle differently)
        eventJson
    }
  }
}

