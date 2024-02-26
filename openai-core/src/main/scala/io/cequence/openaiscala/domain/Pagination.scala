package io.cequence.openaiscala.domain

final case class Pagination(
  limit: Option[Int] = None,
  after: Option[String] = None,
  before: Option[String] = None
) {
  def withLimit(limit: Int): Pagination = copy(limit = Some(limit))
  def after(after: String): Pagination = copy(after = Some(after))
  def before(before: String): Pagination = copy(before = Some(before))
}

object Pagination {
  val default: Pagination = Pagination()

  def limit(limit: Int): Pagination = Pagination(limit = Some(limit))
}
