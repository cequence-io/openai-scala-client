package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.service.PathSegment
import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case class generateContent(model: String)
      extends EndPoint(s"models/${PathSegment(stripModelsPrefix(model))}:generateContent")
  case class streamGenerateContent(model: String)
      extends EndPoint(
        s"models/${PathSegment(stripModelsPrefix(model))}:streamGenerateContent"
      )
  case class batchGenerateContent(model: String)
      extends EndPoint(
        s"models/${PathSegment(stripModelsPrefix(model))}:batchGenerateContent"
      )
  case object models extends EndPoint
  case object batches extends EndPoint
  case class batches(name: String)
      extends EndPoint(s"batches/${PathSegment(stripBatchesPrefix(name))}")
  case class cancelBatch(name: String)
      extends EndPoint(s"batches/${PathSegment(stripBatchesPrefix(name))}:cancel")
  case object cachedContents extends EndPoint
  case class cachedContents(name: String)
      extends EndPoint(s"cachedContents/${PathSegment(stripCachedContentsPrefix(name))}")
  case class files(name: String)
      extends EndPoint(s"files/${PathSegment(stripFilesPrefix(name))}")

  // every caller-supplied id below goes through PathSegment, so it stays ONE path segment
  // (a '/', '?' or '#' in it is escaped, '.' / '..' / "" refused) - after the known prefix strip
  private def stripCachedContentsPrefix(name: String): String =
    name.stripPrefix("cachedContents/")

  // shared with GeminiThinking, so the endpoint and the thinking decision agree on the bare id
  private[impl] def stripModelsPrefix(name: String): String =
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
  case object alt extends Param
}
