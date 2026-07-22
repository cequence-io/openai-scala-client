package io.cequence.openaiscala.claudeagent.service.impl

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Framing, Keep, Source, StreamConverters}
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentProcessExit, ClaudeAgentSettings}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{blocking, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/**
 * Spawns `claude` as a subprocess (eagerly, at construction time) and exchanges
 * newline-delimited JSON over its stdin/stdout. Dumb transport layer - parses bytes into
 * [[JsValue]]s and back; interpreting/dispatching the JSON shapes is the service layer's job.
 */
private[claudeagent] class ClaudeAgentProcessTransport(
  settings: ClaudeAgentSettings
)(
  implicit ec: ExecutionContext,
  materializer: Materializer
) {

  private val logger: Logger = Logger(LoggerFactory.getLogger(getClass))

  private val resolvedExecutable = settings.executablePath.getOrElse("claude")
  private val closing = new AtomicBoolean(false)

  private val process = {
    val args = Seq.newBuilder[String]

    args += resolvedExecutable
    args += "--output-format"
    args += "stream-json"
    args += "--verbose"
    args += "--input-format"
    args += "stream-json"

    settings.model.foreach { model =>
      args += "--model"
      args += model
    }

    settings.systemPrompt.foreach { prompt =>
      args += "--system-prompt"
      args += prompt
    }

    settings.appendSystemPrompt.foreach { prompt =>
      args += "--append-system-prompt"
      args += prompt
    }

    if (settings.allowedTools.nonEmpty) {
      args += "--allowedTools"
      args += settings.allowedTools.mkString(",")
    }

    if (settings.disallowedTools.nonEmpty) {
      args += "--disallowedTools"
      args += settings.disallowedTools.mkString(",")
    }

    settings.permissionMode.foreach { mode =>
      args += "--permission-mode"
      args += mode
    }

    settings.permissionPromptToolName.foreach { toolName =>
      args += "--permission-prompt-tool"
      args += toolName
    }

    settings.maxTurns.foreach { maxTurns =>
      args += "--max-turns"
      args += maxTurns.toString
    }

    if (settings.includePartialMessages) {
      args += "--include-partial-messages"
    }

    settings.resume.foreach { id =>
      args += s"--resume=$id"
    }

    if (settings.continueSession) {
      args += "--continue"
    }

    if (settings.forkSession) {
      args += "--fork-session"
    }

    settings.extraArgs.foreach { case (key, valueOption) =>
      valueOption match {
        case None        => args += s"--$key"
        case Some(value) => args ++= Seq(s"--$key", value)
      }
    }

    val processBuilder = new ProcessBuilder(args.result(): _*)

    settings.cwd.foreach(cwd => processBuilder.directory(new File(cwd)))

    settings.env.foreach { case (key, value) =>
      processBuilder.environment().put(key, value)
    }

    if (!processBuilder.environment().containsKey("CLAUDE_CODE_ENTRYPOINT")) {
      processBuilder.environment().put("CLAUDE_CODE_ENTRYPOINT", "sdk-scala")
    }

    // stderr is captured separately (for stderrTail) rather than merged into stdout, which
    // only carries the NDJSON protocol stream.
    processBuilder.redirectErrorStream(false)

    Try(processBuilder.start()) match {
      case Success(startedProcess) => startedProcess
      case Failure(e: IOException) =>
        throw new ClaudeAgentSpawnException(
          s"Failed to spawn the '$resolvedExecutable' process: ${e.getMessage}"
        )
      case Failure(e) => throw e
    }
  }

  // -- stdin: plain OutputStream writes, serialized with a lock so concurrent writeLine calls
  // never interleave partial lines.
  private val stdinLock = new Object
  private val stdin = process.getOutputStream

  // -- stderr: drained continuously in the background (an undrained pipe can also block),
  // capped at ~64KB retained; completion reports only the last ~2KB of it.
  private val stderrMaxRetainedBytes = 64 * 1024
  private val stderrTailBytes = 2 * 1024

  private val stderrBuffer: Future[String] =
    Future {
      blocking {
        val out = new java.io.ByteArrayOutputStream()
        val buf = new Array[Byte](4096)
        val in = process.getErrorStream

        var read = in.read(buf)
        while (read != -1) {
          if (out.size() < stderrMaxRetainedBytes) {
            out.write(buf, 0, read)
          }
          read = in.read(buf)
        }

        new String(out.toByteArray, StandardCharsets.UTF_8)
      }
    }

  private lazy val processCompletion: Future[ClaudeAgentProcessExit] =
    Future {
      blocking {
        process.waitFor()
      }
    }.recoverWith { case NonFatal(e) =>
      Future.failed(
        new ClaudeAgentProcessException(
          "Failed while waiting for the claude process to exit",
          Some(e)
        )
      )
    }.flatMap { _ =>
      stderrBuffer.recover { case NonFatal(_) => "" }.map { stderr =>
        val tail =
          if (stderr.length > stderrTailBytes)
            stderr.substring(stderr.length - stderrTailBytes)
          else
            stderr

        ClaudeAgentProcessExit(
          exitCode = Some(process.exitValue()),
          signal = None,
          stderrTail = tail
        )
      }
    }

  // -- stdout: parsed NDJSON, broadcast so it can be subscribed to more than once without
  // re-spawning the (already-running, singleton) process. Runs eagerly so the process's
  // stdout pipe is drained promptly (an undrained OS pipe buffer can block the child).
  private val rawEventsSource: Source[JsValue, NotUsed] =
    StreamConverters
      .fromInputStream(() => process.getInputStream)
      .mapMaterializedValue(_ => NotUsed)
      .via(
        Framing.delimiter(
          ByteString("\n"),
          maximumFrameLength = 10 * 1024 * 1024,
          allowTruncation = true
        )
      )
      .map { bytes =>
        val line = bytes.utf8String
        val parsed = Try(Json.parse(line))

        parsed.failed.foreach { e =>
          logger.debug(s"Skipping non-JSON line from claude process stdout: $line", e)
        }

        parsed.toOption
      }
      .collect { case Some(jsValue) => jsValue }
      .concat(
        Source.futureSource(
          completion.map { exit =>
            if (closing.get() || exit.exitCode.contains(0))
              Source.empty
            else
              Source.failed(unexpectedExitException(exit))
          }
        )
      )
      .toMat(BroadcastHub.sink[JsValue](bufferSize = 256))(Keep.right)
      .run()

  /**
   * Parsed NDJSON lines from the CLI's stdout, one [[JsValue]] per line. Backed by a
   * `BroadcastHub` so it can be materialized more than once without re-spawning the process
   * (the process itself is a stateful singleton spawned once in the constructor, unlike a
   * typical cold per-subscription HTTP `Source`). Malformed/non-JSON lines are logged and
   * skipped, never fail the stream (mirrors the official SDKs' own leniency here).
   */
  def rawEvents: Source[JsValue, NotUsed] = rawEventsSource

  /**
   * Writes one JSON value as a single NDJSON line (`Json.stringify(json) + "\n"`) to the
   * process's stdin.
   */
  def writeLine(json: JsValue): Future[Unit] =
    Future {
      blocking {
        if (closing.get())
          throw new ClaudeAgentProcessException("Cannot write to a closing claude process")

        val bytes = Json.stringify(json).getBytes(StandardCharsets.UTF_8) ++
          "\n".getBytes(StandardCharsets.UTF_8)

        stdinLock.synchronized {
          stdin.write(bytes)
          stdin.flush()
        }
      }
    }.recoverWith { case e: IOException =>
      Future.failed(
        new ClaudeAgentProcessException("Failed to write to claude process stdin", Some(e))
      )
    }

  /**
   * Starts graceful shutdown by closing stdin, then terminates the process if it does not exit
   * promptly. Idempotent; the returned future completes with the process status.
   */
  def shutdown(): Future[ClaudeAgentProcessExit] = {
    if (closing.compareAndSet(false, true)) {
      Future {
        blocking {
          stdinLock.synchronized {
            try stdin.close()
            catch { case _: IOException => () }
          }

          if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS))
              process.destroyForcibly()
          }
        }
      }.flatMap(_ => completion)
    } else
      completion
  }

  /**
   * Completes with the exit status when the process exits, whether normally or through
   * [[shutdown]].
   */
  def completion: Future[ClaudeAgentProcessExit] = processCompletion

  private def unexpectedExitException(exit: ClaudeAgentProcessExit)
    : ClaudeAgentProcessException = {
    val stderr = exit.stderrTail.take(512).trim
    val message = s"claude process exited with code ${exit.exitCode.getOrElse("unknown")}" +
      (if (stderr.nonEmpty) s": $stderr" else "")

    new ClaudeAgentProcessException(message)
  }
}

class ClaudeAgentProcessException(
  message: String,
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull)

/** The `claude` executable could not be located/started (e.g. not on PATH). */
class ClaudeAgentSpawnException(message: String) extends ClaudeAgentProcessException(message)
