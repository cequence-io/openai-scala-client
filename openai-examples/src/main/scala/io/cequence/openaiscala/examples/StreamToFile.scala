package io.cequence.openaiscala.examples

import io.cequence.wsclient.stream.{AsyncStream, Bytes}

import java.nio.file.{Files, Path}
import scala.concurrent.{blocking, ExecutionContext, Future}

/** Writes a streamed download to a file, chunk by chunk. */
object StreamToFile {

  /** The number of bytes written; the file is closed however the stream ends. */
  def apply(
    stream: AsyncStream[Bytes],
    path: Path
  )(
    implicit ec: ExecutionContext
  ): Future[Long] = {
    val out = Files.newOutputStream(path)

    stream.evalMap { chunk =>
      Future {
        blocking {
          val bytes = chunk.toArray
          out.write(bytes)
          bytes.length.toLong
        }
      }
    }.runFold(0L)(_ + _).andThen { case _ => out.close() }
  }
}
