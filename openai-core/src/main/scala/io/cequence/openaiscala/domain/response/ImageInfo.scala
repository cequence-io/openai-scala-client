package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class ImageInfo(
  created: ju.Date,
  data: Seq[Map[String, String]]
)
