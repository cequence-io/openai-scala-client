package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.cequence.openaiscala.perplexity.service.PerplexityScalaClientException
import play.api.libs.json.{JsValue, Json}

import java.nio.charset.StandardCharsets

/**
 * A server-sent-events decoder for the Agent API streams: splits the byte stream into events
 * on a blank line (`\n\n` or `\r\n\r\n` - both occur in the wild, so neither is assumed),
 * joins an event's `data:` lines, skips comments / `event:` / `id:` lines and the `[DONE]`
 * terminator, and parses each payload as JSON. A pending event larger than `maxEventBytes`
 * fails the stream instead of buffering without bound.
 */
private[service] object ServerSentEvents {

  val DefaultMaxEventBytes: Int = 16 * 1024 * 1024

  def jsonPayloads(maxEventBytes: Int = DefaultMaxEventBytes)
    : Flow[ByteString, JsValue, NotUsed] =
    Flow[ByteString]
      .concat(akka.stream.scaladsl.Source.single(ByteString("\n\n"))) // flush a trailing event
      .statefulMapConcat { () =>
        var buffer = ByteString.empty

        (chunk: ByteString) => {
          buffer = buffer ++ chunk
          val events = scala.collection.mutable.ArrayBuffer.empty[String]

          var boundary = nextBoundary(buffer)
          while (boundary.isDefined) {
            val (end, length) = boundary.get
            events += buffer.take(end).decodeString(StandardCharsets.UTF_8)
            buffer = buffer.drop(end + length)
            boundary = nextBoundary(buffer)
          }

          if (buffer.length > maxEventBytes)
            throw new PerplexityScalaClientException(
              s"Server-sent event exceeds $maxEventBytes bytes without an event boundary."
            )

          events.toList.flatMap(dataOf).map(Json.parse)
        }
      }

  // the earliest blank-line boundary: (index, boundary length)
  private def nextBoundary(buffer: ByteString): Option[(Int, Int)] =
    Seq("\r\n\r\n", "\n\n", "\r\r")
      .map(delimiter => (buffer.indexOfSlice(ByteString(delimiter)), delimiter.length))
      .filter(_._1 >= 0)
      .sortBy(_._1)
      .headOption

  private val fieldLine = "^(data|event|id|retry)(:.*)?$".r

  // the joined `data:` payload of one event, if any (and not the [DONE] terminator); a block
  // that is not SSE at all - a JSON error body answering the request, an HTML gateway page -
  // is returned whole (JSON) or fails the stream, so an error is never silently dropped
  private def dataOf(event: String): Option[String] = {
    val lines = event.split("\r\n|\n|\r").toList.filter(_.nonEmpty)
    val nonSse =
      lines.filterNot(line => line.startsWith(":") || fieldLine.findFirstIn(line).isDefined)

    if (nonSse.nonEmpty) {
      val text = event.trim
      if (text.startsWith("{") || text.startsWith("["))
        Some(text)
      else
        throw new PerplexityScalaClientException(
          s"Expected a server-sent event stream but got: ${text.take(500)}"
        )
    } else {
      val data = lines.collect {
        case line if line.startsWith("data:") =>
          val value = line.stripPrefix("data:")
          if (value.startsWith(" ")) value.drop(1) else value
      }.mkString("\n").trim

      if (data.isEmpty || data == "[DONE]") None else Some(data)
    }
  }
}
