package io.cequence.openaiscala.gemini.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case class generateContent(model: String)
      extends EndPoint(s"models/${stripModelsPrefix(model)}:generateContent")
  case class streamGenerateContent(model: String)
      extends EndPoint(s"models/${stripModelsPrefix(model)}:streamGenerateContent")
  case class batchGenerateContent(model: String)
      extends EndPoint(s"models/${stripModelsPrefix(model)}:batchGenerateContent")
  case object models extends EndPoint
  case object batches extends EndPoint
  case class batches(name: String) extends EndPoint(s"batches/${stripBatchesPrefix(name)}")
  case class cancelBatch(name: String)
      extends EndPoint(s"batches/${stripBatchesPrefix(name)}:cancel")
  case object cachedContents extends EndPoint
  case class cachedContents(name: String)
      extends EndPoint(s"cachedContents/${stripCachedContentsPrefix(name)}")
  case class files(name: String) extends EndPoint(s"files/${stripFilesPrefix(name)}")

  private def stripCachedContentsPrefix(name: String): String =
    name.stripPrefix("cachedContents/")

  private def stripModelsPrefix(name: String): String =
    name.stripPrefix("models/")

  private def stripBatchesPrefix(name: String): String =
    name.stripPrefix("batches/")

  private def stripFilesPrefix(name: String): String =
    name.stripPrefix("files/")
}

sealed trait Param extends EnumValue

object Param {

  case object key extends Param
  case object contents extends Param
  case object model extends Param
  case object tools extends Param
  case object tool_config extends Param
  case object safety_settings extends Param
  case object system_instruction extends Param
  case object generation_config extends Param
  case object cached_content extends Param
  case object page_size extends Param
  case object page_token extends Param
  case object name extends Param
  case object ttl extends Param
  case object expireTime extends Param
  case object updateMask extends Param
  case object cachedContent extends Param
}
