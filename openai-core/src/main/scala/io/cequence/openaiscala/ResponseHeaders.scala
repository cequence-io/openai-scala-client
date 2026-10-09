package io.cequence.openaiscala

/** Response header lookups shared by the services of every module. */
object ResponseHeaders {

  /**
   * The first value of the first of `names` found among `headers` - the names compared
   * case-insensitively, a blank value skipped - e.g. a host's request id by priority
   * (`x-typesafe-request-id` before `x-request-id`), or None when no name is present.
   */
  def first(
    headers: Map[String, Seq[String]],
    names: Seq[String]
  ): Option[String] =
    names.foldLeft(Option.empty[String]) {
      (
        found,
        name
      ) =>
        found.orElse(
          headers.collectFirst {
            case (header, values) if header.equalsIgnoreCase(name) =>
              values.headOption.map(_.trim).filter(_.nonEmpty)
          }.flatten
        )
    }
}
