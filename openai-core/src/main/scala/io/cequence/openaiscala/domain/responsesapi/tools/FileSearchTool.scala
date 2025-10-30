package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * A tool that searches for relevant content from uploaded files.
 *
 * @param vectorStoreIds
 *   The IDs of the vector stores to search.
 * @param filters
 *   Optional filter to apply based on file attributes.
 * @param maxNumResults
 *   Optional maximum number of results to return (between 1 and 50).
 * @param rankingOptions
 *   Optional ranking options for search.
 */
case class FileSearchTool(
  vectorStoreIds: Seq[String] = Nil,
  filters: Option[FileFilter] = None,
  maxNumResults: Option[Int] = None,
  rankingOptions: Option[FileSearchRankingOptions] = None
) extends Tool {
  val `type`: String = "file_search"

  override def typeString: String = `type`
}

sealed trait FileFilter

object FileFilter {

  /**
   * A filter used to compare a specified attribute key to a given value using a defined
   * comparison operation.
   *
   * @param key
   *   The key to compare against the value.
   * @param operator
   *   Specifies the comparison operator: eq (equals), ne (not equal), gt (greater than), gte
   *   (greater than or equal), lt (less than), lte (less than or equal), in, nin (not in)
   * @param value
   *   The value to compare against the attribute key; supports string, number, boolean, or
   *   array types.
   */
  case class ComparisonFilter(
    key: String,
    `type`: ComparisonOperator,
    value: Any
  ) extends FileFilter

  /**
   * Comparison operators for file filters.
   */
  sealed trait ComparisonOperator extends EnumValue {
    override def toString: String = super.toString.toLowerCase
  }

  object ComparisonOperator {
    case object Eq extends ComparisonOperator
    case object Ne extends ComparisonOperator
    case object Gt extends ComparisonOperator
    case object Gte extends ComparisonOperator
    case object Lt extends ComparisonOperator
    case object Lte extends ComparisonOperator
    case object In extends ComparisonOperator
    case object Nin extends ComparisonOperator

    def values = Seq(
      Eq,
      Ne,
      Gt,
      Gte,
      Lt,
      Lte,
      In,
      Nin
    )
  }

  /**
   * Combine multiple filters using and or or.
   *
   * @param filters
   *   Array of filters to combine. Items can be ComparisonFilter or CompoundFilter.
   * @param `type`
   *   Type of operation: and or or.
   */
  case class CompoundFilter(
    filters: Seq[FileFilter],
    `type`: CompoundOperator
  ) extends FileFilter

  /**
   * Compound operators for combining multiple filters.
   */
  sealed trait CompoundOperator extends EnumValue {
    override def toString: String = super.toString.toLowerCase
  }

  object CompoundOperator {
    case object And extends CompoundOperator
    case object Or extends CompoundOperator

    def values = Seq(And, Or)
  }
}

/**
 * Ranking options for file search.
 *
 * @param ranker
 *   Optional ranker to use for the file search. Defaults to "auto".
 * @param scoreThreshold
 *   Optional score threshold (between 0 and 1). Numbers closer to 1 return more relevant but
 *   fewer results.
 */
case class FileSearchRankingOptions(
  ranker: Option[String] = None,
  scoreThreshold: Option[Double] = None
)
