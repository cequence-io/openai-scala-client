package io.cequence.openaiscala.service.ws

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import play.api.libs.ws.{BodyWritable, SourceBody}

import java.nio.file.Paths

/**
 * Adapted from `play.api.http.writeableOf_MultipartFormData` but more efficient due to the
 * fact that, rather then fully materializing, form data and files are concatenated as
 * sources/streams before sending out.
 */
object MultipartWritable {

  object HttpHeaderNames {
    val CONTENT_DISPOSITION = "content-disposition"
    val CONTENT_TYPE = "content-type"
  }

  private val fileExtensionContentTypeMap = Map(
    "txt" -> "text/plain",
    "csv" -> "text/csv",
    "json" -> "application/json",
    "xml" -> "application/xml",
    "pdf" -> "application/pdf",
    "zip" -> "application/zip",
    "tar" -> "application/x-tar",
    "gz" -> "application/x-gzip",
    "ogg" -> "application/ogg",
    "mp3" -> "audio/mpeg",
    "wav" -> "audio/x-wav",
    "mp4" -> "video/mp4",
    "webm" -> "video/webm",
    "png" -> "image/png",
    "jpg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "gif" -> "image/gif",
    "svg" -> "image/svg+xml"
  )

  /**
   * `Writeable` for `MultipartFormData`.
   */
  def writeableOf_MultipartFormData(
    charset: String
  ): BodyWritable[MultipartFormData] = {

    val boundary: String =
      "--------" + scala.util.Random.alphanumeric.take(20).mkString("")

    def encode(str: String) = ByteString.apply(str, charset)

    def formatDataParts(data: Map[String, Seq[String]]) = {
      val dataParts = data.flatMap { case (name, values) =>
        values.map { value =>
          s"--$boundary\r\n${HttpHeaderNames.CONTENT_DISPOSITION}: form-data; name=$name\r\n\r\n$value\r\n"
        }
      }.mkString("")

      encode(dataParts)
    }

    def filePartHeader(file: FilePart) = {
      val name = s""""${file.key}""""
      val filenameAux = file.headerFileName.getOrElse(file.path)
      val filenamePart = s""""${filenameAux}""""

      val contentTypeAux = file.contentType.orElse {
        val extension = filenameAux.split('.').last
        // Azure expects an explicit content type for files
        fileExtensionContentTypeMap.get(extension)
      }

      val contentTypePart = contentTypeAux.map { ct =>
        s"${HttpHeaderNames.CONTENT_TYPE}: $ct\r\n"
      }.getOrElse("")

      encode(
        s"--$boundary\r\n${HttpHeaderNames.CONTENT_DISPOSITION}: form-data; name=$name; filename=$filenamePart\r\n$contentTypePart\r\n"
      )
    }

    BodyWritable[MultipartFormData](
      transform = { (form: MultipartFormData) =>
        // combined data source
        val dataSource: Source[ByteString, _] =
          Source.single(formatDataParts(form.dataParts))

        // files as sources
        val fileSources: Seq[Source[ByteString, _]] = form.files.map { file =>
          val fileSource = FileIO.fromPath(Paths.get(file.path))
          Source
            .single(filePartHeader(file))
            .concat(fileSource)
            .concat(Source.single(encode("\r\n")))
        }

        // file sources combined
        val combinedFileSource =
          fileSources.foldLeft(Source.empty[ByteString])(_.concat(_))

        // all sources concatenated into one
        val finalSource =
          dataSource.concat(combinedFileSource).concat(Source.single(encode(s"--$boundary--")))

        SourceBody(finalSource)
      },
      contentType = s"multipart/form-data; boundary=$boundary"
    )
  }
}
