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
 * framing ws-client's JSON framing does not cover - CRLF and LF framing in one stream, and (up
 * to ws-client 1.1.2, which failed on them) comment-only heartbeat frames (`:`, e.g. OpenAI's
 * Agents API session events every 15 s): splits the byte stream into events on a blank line
 * (`\n\n` or `\r\n\r\n` - both occur in the wild, so neither is assumed), joins an event's
 * `data:` lines, skips comments / `event:` / `id:` lines and the `[DONE]` terminator, and
 * parses each payload as JSON. A pending event larger than `maxEventBytes` fails the stream
 * instead of buffering without bound.
 *
 * Linear in the input, whatever the chunking: the pending event is a rope of the chunks that
 * is never walked per chunk - only the last 3 bytes plus the new chunk are searched for the
 * next boundary - and each incoming chunk is compacted ONCE and walked with a moving offset,
 * one pass, every boundary search resuming where the previous boundary ended and every event a
 * slice of that one copy. So a chunk holding thousands of tiny events (or a flood of blank
 * lines) costs O(chunk size) time and memory, not O(events x chunk size). An event is read on
 * its bytes (one compaction, the `data:` lines sliced from it, the payload parsed from its
 * bytes) rather than through String copies. Built for large events too (a frame may be up to
 * [[StreamingConsts.maxFrameLength]], 32 MiB by default).
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
          // one copy per chunk (with the carry in front), walked once from left to right
          val window = (carry ++ chunk).compact
          var from = 0
          var next = nextBoundary(window, 0)

          while (next.isDefined) {
            val (index, length) = next.get

            // the first boundary closes the pending event (it starts in the chunk, or straddles
            // the chunk edge, i.e. in the carry); later ones close events lying wholly in this
            // chunk - slices of the one copy
            events +=
              (if (from > 0)
                 window.slice(from, index)
               else if (index >= carry.length)
                 buffer ++ window.slice(carry.length, index)
               else
                 buffer.dropRight(carry.length - index))

            if (from == 0)
              buffer = ByteString.empty

            from = index + length
            next = nextBoundary(window, from)
          }

          // what follows the last boundary is the new pending event
          buffer =
            if (from == 0)
              buffer ++ window.drop(carry.length)
            else
              window.drop(from)
          carry = window.drop(from).takeRight(3)

          if (buffer.length > maxEventBytes)
            throw error(StreamingConsts.frameTooLongMessage(maxEventBytes.toLong))

          events.toList.flatMap(dataOf(_, error))
        }
      }

  private val NL = '\n'.toByte
  private val CR = '\r'.toByte

  // the earliest blank-line boundary (`\n\n`, `\r\n\r\n` or `\r\r`) at or after `from`: (index,
  // boundary length) - one linear pass, resumed by the caller where the previous boundary ended
  private def nextBoundary(
    window: ByteString,
    from: Int
  ): Option[(Int, Int)] = {
    val n = window.length
    var i = from
    var found: Option[(Int, Int)] = None

    while (found.isEmpty && i < n) {
      val b = window(i)

      if (b == NL && i + 1 < n && window(i + 1) == NL)
        found = Some((i, 2))
      else if (b == CR) {
        if (i + 3 < n && window(i + 1) == NL && window(i + 2) == CR && window(i + 3) == NL)
          found = Some((i, 4))
        else if (i + 1 < n && window(i + 1) == CR)
          found = Some((i, 2))
      }

      i += 1
    }

    found
  }

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
