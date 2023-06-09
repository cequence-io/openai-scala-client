package io.cequence.openaiscala.service.ws

case class MultipartFormData(
    dataParts: Map[String, Seq[String]] = Map(),
    files: Seq[FilePart] = Nil
)

case class FilePart(
    key: String,
    path: String,
    headerFileName: Option[String] = None,
    contentType: Option[String] = None
)
