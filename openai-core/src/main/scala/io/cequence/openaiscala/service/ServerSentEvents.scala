package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.stream.AsyncStream.Step
import io.cequence.wsclient.stream.{AsyncPipe, Bytes}
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
 * Linear in the input, whatever the chunking: each run keeps ONE growable buffer (grown
 * geometrically, its framed prefix compacted away only when it would otherwise grow), every
 * byte is copied into it once and scanned once - the search for the next boundary resumes
 * where the previous one stopped, minus the 3 bytes a boundary (the longest, `\r\n\r\n`, is 4
 * bytes) may straddle. A chunk holding thousands of tiny events (or a flood of blank lines)
 * costs O(chunk size), and a multi-MB event arriving in small chunks O(event size) - a
 * whole-buffer search per chunk made a 1 MiB event in 8 KiB chunks take 21 s (measured
 * 2026-10-08), a tail search on a rope of the chunks 11 s for 64 MiB in 1460-byte chunks
 * (2026-10-09). An event is read on its bytes in the buffer (its `data:` lines located there,
 * the payload copied once and parsed from its bytes) rather than through String copies. Built
 * for large events too (a frame may be up to [[StreamingConsts.maxFrameLength]], 32 MiB by
 * default).
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
  ): AsyncPipe[Bytes, JsValue] =
    AsyncPipe
      .of[Bytes]
      .stateful(() => new EventSplitter(maxEventBytes, error))(
        (
          splitter,
          chunk
        ) => Step(splitter, splitter.push(chunk)),
        splitter => splitter.finish()
      )

  // the per-run state: the pending bytes in one growable buffer
  private final class EventSplitter(
    maxEventBytes: Int,
    error: String => Throwable
  ) {
    private var buffer = new Array[Byte](InitialCapacity)
    private var start = 0 // the first byte of the pending event
    private var end = 0 // the end of the buffered bytes
    private var scanFrom = 0 // no boundary starts before this index

    def push(chunk: Bytes): List[JsValue] = {
      append(chunk)

      val payloads = ListBuffer.empty[JsValue]
      var next = nextBoundary(buffer, math.max(start, scanFrom), end)

      while (next >= 0) {
        val index = next & 0xffffffffL
        val boundary = (next >>> 32).toInt
        dataOf(buffer, start, index.toInt, error).foreach(payloads += _)
        start = index.toInt + boundary
        next = nextBoundary(buffer, start, end)
      }

      // a boundary may straddle the chunk edge: its first (at most) 3 bytes are already here
      scanFrom = math.max(start, end - 3)

      if (end - start > maxEventBytes)
        throw error(StreamingConsts.frameTooLongMessage(maxEventBytes.toLong))

      if (start == end)
        reset()

      payloads.toList
    }

    // the trailing event without a blank line after it
    def finish(): List[JsValue] =
      if (start == end)
        Nil
      else
        dataOf(buffer, start, end, error).toList

    // appends the chunk - compacting the framed prefix away first, if that makes room, and
    // growing geometrically otherwise
    private def append(chunk: Bytes): Unit = {
      val length = chunk.length
      if (length > 0) {
        val pending = end - start

        if (start > 0 && end + length > buffer.length) {
          System.arraycopy(buffer, start, buffer, 0, pending)
          scanFrom -= start
          start = 0
          end = pending
        }

        if (end + length > buffer.length) {
          val needed = end.toLong + length
          if (needed > Int.MaxValue - 8)
            throw error(StreamingConsts.frameTooLongMessage(maxEventBytes.toLong))
          val grown = math.max(needed, math.min(buffer.length.toLong * 2, Int.MaxValue - 8L))
          buffer = java.util.Arrays.copyOf(buffer, grown.toInt)
        }

        chunk.asByteBuffer.get(buffer, end, length)
        end += length
      }
    }

    // nothing pending: start over at the front, and let a buffer a large event grew go
    private def reset(): Unit = {
      start = 0
      end = 0
      scanFrom = 0
      if (buffer.length > RetainedCapacity)
        buffer = new Array[Byte](InitialCapacity)
    }
  }

  private val InitialCapacity = 4096
  private val RetainedCapacity = 1024 * 1024

  private val NL = '\n'.toByte
  private val CR = '\r'.toByte
  private val Colon = ':'.toByte

  // the earliest blank-line boundary (`\n\n`, `\r\n\r\n` or `\r\r`) at or after `from`, below
  // `until`: its index in the low 32 bits and its length in the high ones, or -1 - one linear
  // pass, resumed by the caller where the previous boundary ended
  private def nextBoundary(
    bytes: Array[Byte],
    from: Int,
    until: Int
  ): Long = {
    var i = from
    var found = -1L

    while (found < 0 && i < until) {
      val b = bytes(i)

      if (b == NL && i + 1 < until && bytes(i + 1) == NL)
        found = (2L << 32) | i
      else if (b == CR) {
        if (i + 3 < until && bytes(i + 1) == NL && bytes(i + 2) == CR && bytes(i + 3) == NL)
          found = (4L << 32) | i
        else if (i + 1 < until && bytes(i + 1) == CR)
          found = (2L << 32) | i
      }

      i += 1
    }

    found
  }

  private val Data = ascii("data:")
  private val Done = ascii("[DONE]")
  private val fieldNames = Seq("data", "event", "id", "retry")
  private val bareFields = fieldNames.map(ascii)
  private val fieldPrefixes = fieldNames.map(name => ascii(name + ":"))

  private def ascii(s: String): Array[Byte] = s.getBytes(StandardCharsets.US_ASCII)

  // the joined `data:` payload of the event in bytes(from, until), parsed, if any (and not the
  // [DONE] terminator); a block that is not SSE at all - a JSON error body answering the
  // request, an HTML gateway page - is returned whole (JSON) or fails the stream, so an error
  // is never silently dropped
  private def dataOf(
    bytes: Array[Byte],
    from: Int,
    until: Int,
    error: String => Throwable
  ): Option[JsValue] = {
    val lines = linesOf(bytes, from, until).filter { case (s, e) => e > s }
    val nonSse = lines.exists { case (s, e) =>
      bytes(s) != Colon && !isField(bytes, s, e)
    }

    if (nonSse) {
      val text = new String(bytes, from, until - from, StandardCharsets.UTF_8).trim
      if (text.startsWith("{") || text.startsWith("["))
        Some(Json.parse(text))
      else
        throw error(s"Expected a server-sent event stream but got: ${text.take(500)}")
    } else {
      val data = lines.collect {
        case (s, e) if startsWith(bytes, s, e, Data) =>
          val valueStart = s + Data.length
          if (valueStart < e && bytes(valueStart) == ' '.toByte)
            (valueStart + 1, e)
          else
            (valueStart, e)
      }
      val payload = trimmed(joined(bytes, data))

      if (payload.isEmpty || java.util.Arrays.equals(payload, Done))
        None
      else
        Some(Json.parse(payload))
    }
  }

  // the lines of bytes(from, until), split on `\r\n`, `\n` or `\r`, as (start, end) ranges
  private def linesOf(
    bytes: Array[Byte],
    from: Int,
    until: Int
  ): List[(Int, Int)] = {
    val lines = ListBuffer.empty[(Int, Int)]
    var start = from
    var i = from
    while (i < until) {
      val b = bytes(i)
      if (b == NL || b == CR) {
        lines += ((start, i))
        if (b == CR && i + 1 < until && bytes(i + 1) == NL)
          i += 1
        i += 1
        start = i
      } else
        i += 1
    }
    if (start < until)
      lines += ((start, until))
    lines.toList
  }

  private def startsWith(
    bytes: Array[Byte],
    from: Int,
    until: Int,
    prefix: Array[Byte]
  ): Boolean =
    until - from >= prefix.length && {
      var j = 0
      while (j < prefix.length && bytes(from + j) == prefix(j)) j += 1
      j == prefix.length
    }

  private def isField(
    bytes: Array[Byte],
    from: Int,
    until: Int
  ): Boolean =
    bareFields.exists(field =>
      until - from == field.length && startsWith(bytes, from, until, field)
    ) ||
      fieldPrefixes.exists(startsWith(bytes, from, until, _))

  // the data lines joined with a newline - one copy
  private def joined(
    bytes: Array[Byte],
    data: List[(Int, Int)]
  ): Array[Byte] =
    data match {
      case Nil =>
        Array.emptyByteArray

      case _ =>
        val size = data.map { case (s, e) => e - s }.sum + data.size - 1
        val out = new Array[Byte](size)
        var at = 0
        data.zipWithIndex.foreach { case ((s, e), index) =>
          if (index > 0) {
            out(at) = NL
            at += 1
          }
          System.arraycopy(bytes, s, out, at, e - s)
          at += e - s
        }
        out
    }

  // ASCII whitespace (and control characters, as String.trim) cut from both ends
  private def trimmed(bytes: Array[Byte]): Array[Byte] = {
    var start = 0
    var end = bytes.length
    while (start < end && (bytes(start) & 0xff) <= ' ') start += 1
    while (end > start && (bytes(end - 1) & 0xff) <= ' ') end -= 1
    if (start == 0 && end == bytes.length)
      bytes
    else
      java.util.Arrays.copyOfRange(bytes, start, end)
  }
}
