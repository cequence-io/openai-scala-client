package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source

import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Encodes a caller-supplied id (resource id, model name) as exactly ONE URL path segment. RFC
 * 3986 unreserved characters, sub-delims, ':' and '@' stay literal; every other byte - '/',
 * '?', '#', '%', whitespace, controls, non-ASCII (as UTF-8) - is percent-encoded. Null, empty,
 * "." and ".." are refused with an IllegalArgumentException before any I/O, since the HTTP
 * stack would resolve the dot segments and address another resource.
 *
 * A service method that encodes its ids runs inside [[inFuture]] / [[inSource]], so a refusal
 * fails the Future / stream it returns: a synchronous throw would escape `.recover`,
 * `Future.traverse` and the service adapters (e.g. the retry adapter).
 *
 * The safe set equals Python's `urllib.parse.quote(value, safe = "!$&'()*+,;=:@")`.
 */
object PathSegment {

  private val literal: Set[Char] =
    (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "-._~!$&'()*+,;=:@").toSet

  /**
   * Refuses null, empty, "." and ".." (IllegalArgumentException) and returns the id unchanged.
   * For ids that a call site encodes by other means (e.g. SigV4's doubly encoded segments).
   */
  def validate(id: String): String = {
    require(
      id != null && id.nonEmpty && id != "." && id != "..",
      s"Invalid path segment (resource id): '$id'"
    )

    id
  }

  /**
   * A Future-returning call whose path encodes caller-supplied ids, as a Future that fails -
   * rather than throwing on the caller's thread - when an id is refused (or the call throws
   * while being set up).
   */
  def inFuture[T](call: => Future[T]): Future[T] =
    try call
    catch {
      case NonFatal(e) => Future.failed(e)
    }

  /** [[inFuture]] for a call returning a stream: a refused id fails the stream. */
  def inSource[T](call: => Source[T, NotUsed]): Source[T, NotUsed] =
    try call
    catch {
      case NonFatal(e) => Source.failed(e)
    }

  def apply(id: String): String = {
    validate(id)

    val sb = new StringBuilder

    id.getBytes(StandardCharsets.UTF_8).foreach { b =>
      val c = (b & 0xff).toChar

      if (b >= 0 && literal.contains(c))
        sb.append(c)
      else
        sb.append('%').append("%02X".format(b & 0xff))
    }

    sb.toString
  }
}
