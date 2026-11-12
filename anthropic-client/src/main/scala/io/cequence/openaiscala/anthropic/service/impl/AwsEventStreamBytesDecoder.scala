package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.stream.AsyncPipe

import java.util.Base64
import play.api.libs.json.{JsValue, Json}

object AwsEventStreamBytesDecoder {
  def pipe: AsyncPipe[JsValue, JsValue] = AsyncPipe.of[JsValue].map { eventJson =>
    // eventJson might look like:
    // { ":message-type":"event", ":event-type":"...", "bytes":"base64string" }

    (eventJson \ "bytes")
      .asOpt[String]
      .map { encoded =>
        val decoded = Base64.getDecoder.decode(encoded)
        Json.parse(decoded)
      }
      .getOrElse(
        // If there's no "bytes" field, return the original JSON (or handle differently)
        eventJson
      )
  }
}
