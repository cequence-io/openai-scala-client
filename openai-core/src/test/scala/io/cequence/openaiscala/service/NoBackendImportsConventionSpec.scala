package io.cequence.openaiscala.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

/**
 * A repo-wide convention check (since 2.0.0): the library is backend-neutral - a caller picks
 * any ws-client engine and consumes streams as `AsyncStream`s (or natively, via ws-client's
 * interop modules) - so no main source may reference a stream / HTTP backend directly: Akka,
 * Pekko, fs2, ZIO, Play WS or the Play-only JSON stringifier.
 */
class NoBackendImportsConventionSpec extends AnyWordSpec with Matchers {

  // the modules already ported to the backend-neutral API (all modules once 2.0 is complete)
  private val portedModules = Seq("openai-core", "openai-client", "openai-client-stream")

  private val backendReference =
    """(?m)^\s*import\s+(?:_root_\.)?(akka|org\.apache\.pekko|fs2|zio|play\.api\.libs\.ws)\b|\bPlayJsonUtil\b|\bakka\.(?:stream|actor|util|pattern|NotUsed)\b""".r

  private def repoRoot: File =
    Iterator
      .iterate(new File(".").getAbsoluteFile)(_.getParentFile)
      .takeWhile(_ != null)
      .find(dir =>
        new File(dir, "build.sbt").isFile && new File(dir, "openai-core").isDirectory
      )
      .getOrElse(fail("the repository root (with build.sbt and openai-core) was not found"))

  private def scalaFiles(dir: File): Seq[File] =
    Option(dir.listFiles()).toSeq.flatten.flatMap { file =>
      if (file.isDirectory)
        scalaFiles(file)
      else if (file.getName.endsWith(".scala"))
        Seq(file)
      else
        Nil
    }

  "the main sources" should {

    "reference no stream / HTTP backend" in {
      val mainSources = portedModules
        .map(module => new File(repoRoot, s"$module/src/main"))
        .filter(_.isDirectory)
        .flatMap(scalaFiles)

      mainSources should not be empty

      val offenders = mainSources.flatMap { file =>
        val source = Source.fromFile(file, "UTF-8")
        val text =
          try source.mkString
          finally source.close()

        backendReference.findAllMatchIn(text).map { reference =>
          val line = text.substring(0, reference.start).count(_ == '\n') + 1
          s"${repoRoot.toPath.relativize(file.toPath)}:$line (${reference.matched.trim})"
        }
      }

      withClue(
        "Backend references in main sources (use AsyncStream / AsyncPipe / AsyncScheduler): "
      ) {
        offenders shouldBe empty
      }
    }
  }
}
