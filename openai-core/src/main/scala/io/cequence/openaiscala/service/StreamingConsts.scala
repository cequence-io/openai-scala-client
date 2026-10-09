package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.ByteString
import com.typesafe.config.Config
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.CequenceWSException

import scala.util.Try

object StreamingConsts extends HasOpenAIConfig {

  /**
   * The default maximum size of one streamed frame (a server-sent event / JSON line): 32 MiB.
   * A frame carries a whole event, so it can be large: a generated image in base64 (OpenAI's
   * image generation in the Responses API - a partial image, the finished item and again the
   * closing `response.completed`; Gemini's image models up to 4K), a fetched document in an
   * Anthropic server-tool result, a long response echoed in a closing event. ws-client's own
   * default is 20 000 bytes; 1.4.0 capped every stream at 1 MiB, which the 2-3 MiB image
   * frames seen live exceed.
   *
   * Why 32 MiB and not more: the cap is a ceiling, not an allocation - a normal frame costs
   * the same under any value - but it bounds the worst cases: a stream buffers up to the cap
   * before a frame is parsed (about 2-3x the frame in memory, per concurrent stream), a broken
   * stream that never sends its delimiter buffers the cap before failing, and a frame that
   * large stalls its dispatcher thread while it is parsed. The JSON parser refuses a single
   * string over 20 million characters anyway, so a frame above ~20 MiB is usable only when it
   * holds several large values - 32 MiB covers that with margin; set the config for more.
   */
  val DefaultMaxFrameLength: Int = 32 * 1024 * 1024

  val MaxFrameLengthConfigKey = "openai-scala-client.streaming.maxFrameLength"

  val MaxFrameLengthEnvVariable = "OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH"

  private val MinFrameLength = 1024L

  /**
   * The maximum size of one streamed frame, for every stream of every provider here - a larger
   * frame fails the stream (with an exception naming this setting). Read once from the client
   * config's `openai-scala-client.streaming.maxFrameLength` - a size such as `128 MiB` or a
   * number of bytes, set in the config file, as the env variable
   * `OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH` or as a system property - and
   * [[DefaultMaxFrameLength]] when not set.
   */
  lazy val maxFrameLength: Int = maxFrameLengthFrom(clientConfig)

  /**
   * The frame cap set in `config` ([[MaxFrameLengthConfigKey]]), [[DefaultMaxFrameLength]]
   * when not set.
   *
   * @throws OpenAIScalaClientException
   *   for a value that is not a size, below 1 KiB or not below 2 GiB
   */
  def maxFrameLengthFrom(config: Config): Int =
    configuredMaxFrameLength(config).fold(
      problem => throw new OpenAIScalaClientException(problem),
      identity
    )

  private def configuredMaxFrameLength(config: Config): Either[String, Int] =
    if (!config.hasPath(MaxFrameLengthConfigKey)) Right(DefaultMaxFrameLength)
    else
      for {
        bytes <- Try(config.getBytes(MaxFrameLengthConfigKey).longValue).toOption.toRight(
          s"$MaxFrameLengthConfigKey must be a size such as '128 MiB' or a number of bytes, got '${config.getValue(MaxFrameLengthConfigKey).unwrapped}'."
        )
        valid <- Either.cond(
          bytes >= MinFrameLength && bytes <= Int.MaxValue,
          bytes.toInt,
          s"$MaxFrameLengthConfigKey must be at least 1 KiB and less than 2 GiB, got $bytes bytes" +
            (if (bytes < MinFrameLength) s" - a size needs a unit, e.g. '$bytes MiB'."
             else ".")
        )
      } yield valid

  /**
   * A raw byte stream split into frames at `delimiter`, each at most [[maxFrameLength]] bytes
   * \- a larger one fails the stream naming the setting ([[frameTooLong]]); a last frame
   * without the delimiter is kept.
   */
  def framing(delimiter: String): Flow[ByteString, ByteString, NotUsed] =
    Framing
      .delimiter(ByteString(delimiter), maxFrameLength, allowTruncation = true)
      .mapError(frameTooLong)

  /**
   * The message of a stream failed by a frame over `limit` bytes.
   */
  def frameTooLongMessage(limit: Long): String =
    s"A streamed frame (one event or line of the stream) exceeds the limit of ${describe(limit)}. " +
      s"Raise it with the config key $MaxFrameLengthConfigKey (e.g. '128 MiB') or the env variable $MaxFrameLengthEnvVariable."

  /**
   * Turns the failure of a frame over the cap into an [[OpenAIScalaClientException]] naming
   * the setting (the original failure as its cause); `mapError` leaves any other failure as it
   * is.
   */
  val frameTooLong: PartialFunction[Throwable, Throwable] = { case e @ FrameOverflow(limit) =>
    new OpenAIScalaClientException(frameTooLongMessage(limit), e)
  }

  // Akka's framing failure ("Read N bytes which is more than M without seeing a line
  // terminator"), as is or inside ws-client's "Stream framing problem occurred." exception -
  // with M, the limit
  private object FrameOverflow {
    private val Message =
      """Read \d+ bytes which is more than (\d+) without seeing a line terminator""".r.unanchored

    def unapply(e: Throwable): Option[Long] = e match {
      case _: OpenAIScalaClientException => None // already ours
      case _: FramingException | _: CequenceWSException =>
        Option(e.getMessage).collect { case Message(limit) => limit.toLong }
      case _ => None
    }
  }

  private def describe(bytes: Long): String =
    if (bytes % (1024 * 1024) == 0) s"${bytes / (1024 * 1024)} MiB"
    else if (bytes % 1024 == 0) s"${bytes / 1024} KiB"
    else s"$bytes bytes"
}
