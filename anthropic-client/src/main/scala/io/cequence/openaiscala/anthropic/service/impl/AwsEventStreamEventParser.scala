package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.stream.{AsyncPipe, Bytes}
import play.api.libs.json.{JsValue, Json}

object AwsEventStreamEventParser {

  /** Reads the JSON payload of an AWS event-stream frame; a frame without one gives None. */
  def pipe: AsyncPipe[Bytes, Option[JsValue]] = AsyncPipe.of[Bytes].map { frame =>
    // UTF-8 whatever the platform charset: the binary prelude turns into replacement
    // characters, the JSON payload is read intact
    val rawString = frame.utf8String

    if (rawString.contains("message-type")) {
      val jsonString = rawString.dropWhile(_ != '{').takeWhile(_ != '}') + "}"
      Some(Json.parse(jsonString))
    } else
      None
  }
}
