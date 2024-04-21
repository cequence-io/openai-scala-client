package io.cequence.openaiscala.v2.domain.response

import java.{util => ju}

case class ImageInfo(
  created: ju.Date,
  data: Seq[Map[String, String]]
)
