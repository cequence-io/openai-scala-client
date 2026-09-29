package io.cequence.openaiscala.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

/**
 * A repo-wide convention check: an ENGINE-level stream call (`engine.execJsonStream(site,
 * ...)` / `execRawStream(site, ...)`) fails a non-2xx answer with an UNCLASSIFIED
 * `CequenceWSHttpStatusException`, so services stream through the service-level methods of
 * [[ClassifiedStreamingWSClient]] instead - or, where an engine-level call is unavoidable,
 * follow it with `.mapError(mapHttpStatusErrors)`. This spec scans every module's main sources
 * and fails on an engine-level stream call without that mapping (engine implementations, which
 * merely delegate, are exempt).
 */
class StreamErrorMappingConventionSpec extends AnyWordSpec with Matchers {

  // engine wrappers delegating to another engine - not a service's call site
  private val exemptFiles = Set("SigningWSClientEngine.scala")

  // an engine-level stream call: its first argument is the site binding
  private val engineLevelStreamCall =
    """\.exec(?:Json|Raw)Stream(?:Publisher)?\(\s*site\b""".r

  // how far after the call the mapping must appear (the call's arguments + the chain start)
  private val MappingWindow = 1500

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
      if (file.isDirectory) scalaFiles(file)
      else if (file.getName.endsWith(".scala")) Seq(file)
      else Nil
    }

  "every engine-level stream call in the main sources" should {

    "be followed by .mapError(mapHttpStatusErrors)" in {
      val mainSources = Option(repoRoot.listFiles()).toSeq.flatten
        .map(module => new File(module, "src/main/scala"))
        .filter(_.isDirectory)
        .flatMap(scalaFiles)
        .filterNot(file => exemptFiles.contains(file.getName))

      mainSources should not be empty

      val offenders = mainSources.flatMap { file =>
        val source = Source.fromFile(file, "UTF-8")
        val text =
          try source.mkString
          finally source.close()

        engineLevelStreamCall.findAllMatchIn(text).collect {
          case call
              if !text
                .slice(call.start, call.start + MappingWindow)
                .contains("mapError(mapHttpStatusErrors)") =>
            val line = text.substring(0, call.start).count(_ == '\n') + 1
            s"${repoRoot.toPath.relativize(file.toPath)}:$line"
        }
      }

      withClue(
        "Engine-level stream calls without .mapError(mapHttpStatusErrors) - use the " +
          "service-level execJsonStream / execRawStream(endPoint, ...) instead: "
      ) {
        offenders shouldBe empty
      }
    }
  }
}
