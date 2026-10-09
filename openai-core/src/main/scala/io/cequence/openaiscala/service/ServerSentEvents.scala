package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import io.cequence.openaiscala.OpenAIScalaClientException
import play.api.libs.json.{JsValue, Json}

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

/**
 * A server-sent-events decoder over a raw byte stream (`execRawStream`), for the streams whose
 * framing ws-client's JSON framing does not cover - comment-only heartbeat frames (`:`, e.g.
 * OpenAI's Agents API session events every 15 s), CRLF framing: splits the byte stream into
 * events on a blank line (`\n\n` or `\r\n\r\n` - both occur in the wild, so neither is
 * assumed), joins an event's `data:` lines, skips comments / `event:` / `id:` lines and the
 * `[DONE]` terminator, and parses each payload as JSON. A pending event larger than
 * `maxEventBytes` fails the stream instead of buffering without bound.
 *
 * Built for large events (a frame may be up to [[StreamingConsts.maxFrameLength]], 32 MiB by
 * default): the pending event is a rope of the chunks that is never walked per chunk - only
 * the last 3 bytes plus the new chunk are searched for the next boundary - and an event is
 * read on its bytes (one compaction, the `data:` lines sliced from it, the payload parsed from
 * its bytes) rather than through String copies.
 */
object ServerSentEvents {

  /**
   * The shared stream frame cap, [[StreamingConsts.maxFrameLength]] (32 MiB unless
   * configured).
   */
  def DefaultMaxEventBytes: Int = StreamingConsts.maxFrameLength

  /**
   * @param error
   *   the exception a malformed stream fails with (a provider module passes its own)
   */
  def jsonPayloads(
    maxEventBytes: Int = DefaultMaxEventBytes,
    error: String => Throwable = new OpenAIScalaClientException(_)
  ): Flow[ByteString, JsValue, NotUsed] =
    Flow[ByteString]
      .concat(Source.single(ByteString("\n\n"))) // flush a trailing event
      .statefulMapConcat { () =>
        // the pending event: the chunks so far as a rope, searched as they arrived, so it holds
        // no boundary - and a copy of its last (at most) 3 bytes, where alone a chunk can
        // complete a boundary (the longest, `\r\n\r\n`, is 4 bytes). Only `carry ++ chunk` is
        // searched: an `indexOfSlice` or `drop` on the rope walks its fragments, which made a
        // multi-MB event cubic in its chunks with a whole-buffer search (1 MiB in 8 KiB chunks:
        // 21 s) and still quadratic with a tail search that dropped into the rope (64 MiB in
        // 1460-byte chunks: 11 s, against 0.9 s this way - measured 2026-10-09)
        var buffer = ByteString.empty
        var carry = ByteString.empty

        (chunk: ByteString) => {
          val events = ListBuffer.empty[ByteString]
          var rest = chunk
          var searching = true

          while (searching) {
            val window = (carry ++ rest).compact
            nextBoundary(window) match {
              case None =>
                buffer = buffer ++ rest
                carry = window.takeRight(3)
                searching = false

              case Some((index, length)) =>
                // the boundary starts in the chunk, or straddles the chunk edge (in the carry)
                events +=
                  (if (index >= carry.length) buffer ++ rest.take(index - carry.length)
                   else buffer.dropRight(carry.length - index))
                buffer = ByteString.empty
                carry = ByteString.empty
                rest = window.drop(index + length)
            }
          }

          if (buffer.length > maxEventBytes)
            throw error(StreamingConsts.frameTooLongMessage(maxEventBytes.toLong))

          events.toList.flatMap(dataOf(_, error))
        }
      }

  private val delimiters = Seq("\r\n\r\n", "\n\n", "\r\r").map(ByteString(_))

  // the earliest blank-line boundary in the window: (index, boundary length)
  private def nextBoundary(window: ByteString): Option[(Int, Int)] =
    delimiters
      .map(delimiter => (window.indexOfSlice(delimiter), delimiter.length))
      .filter(_._1 >= 0)
      .sortBy(_._1)
      .headOption

  private val Data = ByteString("data:")
  private val Space = ByteString(" ")
  private val Newline = ByteString("\n")
  private val Done = ByteString("[DONE]")
  private val fieldNames = Seq("data", "event", "id", "retry")
  private val bareFields = fieldNames.map(ByteString(_))
  private val fieldPrefixes = fieldNames.map(name => ByteString(name + ":"))

  // the joined `data:` payload of one event, parsed, if any (and not the [DONE] terminator); a
  // block that is not SSE at all - a JSON error body answering the request, an HTML gateway
  // page - is returned whole (JSON) or fails the stream, so an error is never silently dropped
  private def dataOf(
    event: ByteString,
    error: String => Throwable
  ): Option[JsValue] = {
    val bytes = event.compact
    val lines = linesOf(bytes).filter(_.nonEmpty)
    val nonSse = lines.filterNot(line => line.head == ':'.toByte || isField(line))

    if (nonSse.nonEmpty) {
      val text = bytes.decodeString(StandardCharsets.UTF_8).trim
      if (text.startsWith("{") || text.startsWith("["))
        Some(Json.parse(text))
      else
        throw error(s"Expected a server-sent event stream but got: ${text.take(500)}")
    } else {
      val data = lines.collect {
        case line if line.startsWith(Data) =>
          val value = line.drop(Data.length)
          if (value.startsWith(Space)) value.drop(1) else value
      }
      val payload = trimmed(joined(data))

      if (payload.isEmpty || payload == Done) None
      else Some(Json.parse(payload.toArray))
    }
  }

  // the event's lines, split on `\r\n`, `\n` or `\r` (slices, no copies)
  private def linesOf(bytes: ByteString): List[ByteString] = {
    val lines = ListBuffer.empty[ByteString]
    val n = bytes.length
    var start = 0
    var i = 0
    while (i < n) {
      val b = bytes(i)
      if (b == '\n'.toByte || b == '\r'.toByte) {
        lines += bytes.slice(start, i)
        if (b == '\r'.toByte && i + 1 < n && bytes(i + 1) == '\n'.toByte) i += 1
        i += 1
        start = i
      } else i += 1
    }
    if (start < n) lines += bytes.slice(start, n)
    lines.toList
  }

  private def isField(line: ByteString): Boolean =
    bareFields.exists(_ == line) || fieldPrefixes.exists(line.startsWith(_))

  // the data lines joined with a newline (a rope of the slices; one copy at parse time)
  private def joined(data: List[ByteString]): ByteString =
    data match {
      case Nil => ByteString.empty
      case head :: tail =>
        tail.foldLeft(head)(
          (
            acc,
            line
          ) => acc ++ Newline ++ line
        )
    }

  // ASCII whitespace (and control characters, as String.trim) cut from both ends
  private def trimmed(bytes: ByteString): ByteString = {
    var start = 0
    var end = bytes.length
    while (start < end && (bytes(start) & 0xff) <= ' ') start += 1
    while (end > start && (bytes(end - 1) & 0xff) <= ' ') end -= 1
    bytes.slice(start, end)
  }
}
