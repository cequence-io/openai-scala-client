package io.cequence.openaiscala.gemini.domain.response

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.domain.Content
import io.cequence.wsclient.domain.EnumValue

/**
 * Attribution for a source that contributed to an answer.
 *
 * @param sourceId
 *   Output only. Identifier for the source contributing to this attribution.
 * @param content
 *   Grounding source content that makes up this attribution.
 */
case class GroundingAttribution(
  sourceId: AttributionSourceId,
  content: Content
)

sealed trait AttributionSourceIdPrefix extends EnumValue

object AttributionSourceIdPrefix {
  case object groundingPassage extends AttributionSourceIdPrefix
  case object semanticRetrieverChunk extends AttributionSourceIdPrefix

  def values: Seq[AttributionSourceIdPrefix] = Seq(
    groundingPassage,
    semanticRetrieverChunk
  )

  def of(value: String): AttributionSourceIdPrefix =
    values.find(_.toString() == value).getOrElse {
      throw new OpenAIScalaClientException(s"Unknown attributionSourceIdPrefix: $value")
    }
}

sealed trait AttributionSourceId {
  val prefix: AttributionSourceIdPrefix
}

object AttributionSourceId {

  /**
   * Identifier for a part within a GroundingPassage.
   *
   * @param passageId
   *   Output only. ID of the passage matching the GenerateAnswerRequest's GroundingPassage.id.
   * @param partIndex
   *   Output only. Index of the part within the GenerateAnswerRequest's
   *   GroundingPassage.content.
   */
  case class GroundingPassageId(
    passageId: String,
    partIndex: Int
  ) extends AttributionSourceId {
    val prefix: AttributionSourceIdPrefix = AttributionSourceIdPrefix.groundingPassage
  }

  /**
   * Identifier for a Chunk retrieved via Semantic Retriever specified in the
   * GenerateAnswerRequest using SemanticRetrieverConfig.
   *
   * @param source
   *   Output only. Name of the source matching the request's SemanticRetrieverConfig.source.
   *   Example: corpora/123 or corpora/123/documents/abc
   * @param chunk
   *   Output only. Name of the Chunk containing the attributed text. Example:
   *   corpora/123/documents/abc/chunks/xyz
   */
  case class SemanticRetrieverChunk(
    source: String,
    chunk: String
  ) extends AttributionSourceId {
    val prefix: AttributionSourceIdPrefix = AttributionSourceIdPrefix.semanticRetrieverChunk
  }
}

/**
 * Metadata returned to client when grounding is enabled.
 *
 * @param groundingChunks
 *   List of supporting references retrieved from specified grounding source.
 * @param groundingSupports
 *   List of grounding support.
 * @param webSearchQueries
 *   Web search queries for the following-up web search.
 * @param searchEntryPoint
 *   Optional. Google search entry for the following-up web searches.
 * @param retrievalMetadata
 *   Metadata related to retrieval in the grounding flow.
 */
case class GroundingMetadata(
  groundingChunks: Seq[GroundingChunk] = Nil,
  groundingSupports: Seq[GroundingSupport] = Nil,
  webSearchQueries: Seq[String] = Nil,
  searchEntryPoint: Option[SearchEntryPoint] = None,
  retrievalMetadata: Option[RetrievalMetadata] = None
)

case class GroundingChunk(
  web: Web
)

/**
 * Chunk from the web.
 *
 * @param uri
 *   URI reference of the chunk.
 * @param title
 *   Title of the chunk.
 */
case class Web(
  uri: String,
  title: String
)

/**
 * Grounding support.
 *
 * @param groundingChunkIndices
 *   A list of indices (into 'grounding_chunk') specifying the citations associated with the
 *   claim. For instance [1,3,4] means that grounding_chunk[1], grounding_chunk[3],
 *   grounding_chunk[4] are the retrieved content attributed to the claim.
 * @param confidenceScores
 *   Confidence score of the support references. Ranges from 0 to 1. 1 is the most confident.
 *   This list must have the same size as the groundingChunkIndices.
 * @param segment
 *   Segment of the content this support belongs to.
 */
case class GroundingSupport(
  groundingChunkIndices: Seq[Int] = Nil,
  confidenceScores: Seq[Double] = Nil,
  segment: Segment
)

/**
 * Segment of the content.
 *
 * @param partIndex
 *   Output only. The index of a Part object within its parent Content object.
 * @param startIndex
 *   Output only. Start index in the given Part, measured in bytes. Offset from the start of
 *   the Part, inclusive, starting at zero.
 * @param endIndex
 *   Output only. End index in the given Part, measured in bytes. Offset from the start of the
 *   Part, exclusive, starting at zero.
 * @param text
 *   Output only. The text corresponding to the segment from the response.
 */
case class Segment(
  partIndex: Option[Int] = None,
  startIndex: Int,
  endIndex: Int,
  text: String
)

/**
 * Google search entry point.
 *
 * @param renderedContent
 *   Optional. Web content snippet that can be embedded in a web page or an app webview.
 * @param sdkBlob
 *   Optional. Base64 encoded JSON representing array of <search term, search url> tuple. A
 *   base64-encoded string.
 */
case class SearchEntryPoint(
  renderedContent: Option[String],
  sdkBlob: Option[String]
)

/**
 * Metadata related to retrieval in the grounding flow.
 *
 * @param googleSearchDynamicRetrievalScore
 *   Optional. Score indicating how likely information from google search could help answer the
 *   prompt. The score is in the range [0, 1], where 0 is the least likely and 1 is the most
 *   likely. This score is only populated when google search grounding and dynamic retrieval is
 *   enabled. It will be compared to the threshold to determine whether to trigger google
 *   search.
 */
case class RetrievalMetadata(
  googleSearchDynamicRetrievalScore: Option[Double]
)
