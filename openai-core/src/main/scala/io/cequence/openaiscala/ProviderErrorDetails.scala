package io.cequence.openaiscala

/**
 * What a provider's error response carried, exposed uniformly across providers: the HTTP
 * status, the provider's own error type / code when the body names one, and the provider's
 * request id when a header does. Provider-native exceptions implement it (TypeSafe's
 * `TypeSafeScalaClientException` today); when an adapter repacks such an exception into the
 * shared `OpenAIScala*` hierarchy it keeps the native one as `getCause`, so
 * `ProviderErrorDetails.unapply` finds the details anywhere along the cause chain:
 *
 * {{{
 * service.createChatCompletion(...).recover { case ProviderErrorDetails(d) =>
 *   log(s"status ${d.httpCode} type ${d.errorType} request ${d.requestId}")
 * }
 * }}}
 */
trait ProviderErrorDetails {
  def httpCode: Option[Int]
  def errorType: Option[String]
  def requestId: Option[String]
}

object ProviderErrorDetails {

  /**
   * The first exception along the cause chain (the throwable itself included) carrying
   * details.
   */
  def unapply(t: Throwable): Option[ProviderErrorDetails] =
    Iterator
      .iterate(t)(_.getCause)
      .takeWhile(_ != null)
      .take(16) // a cause cycle would otherwise never end
      .collectFirst { case d: ProviderErrorDetails => d }
}
