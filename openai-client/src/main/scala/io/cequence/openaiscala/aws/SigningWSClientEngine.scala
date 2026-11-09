package io.cequence.openaiscala.aws

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.{RichResponse, SiteBinding}
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.FilePart
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamCore}
import io.cequence.wsclient.stream.{AsyncStream, Bytes, DeferredPublisher}
import play.api.libs.json.JsValue

import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Wraps a [[WSClientEngine]] and AWS SigV4-signs every request it makes, by reproducing the
 * URL and body the underlying engine is about to send and appending the signature to the
 * per-call `extraHeaders`.
 *
 * This exists because a signature depends on the method, URL, body hash and timestamp of each
 * individual request, which ws-client's static `WsRequestContext.authHeaders` (and even its
 * per-request `requestContextFun`, a `Function0`) cannot express. Decorating the engine means
 * no service impl or call site has to change.
 *
 * Build one with [[SigningWSClientEngine.apply]], which returns the streaming-capable
 * [[SigningStreamedWSClientEngine]] whenever the wrapped engine streams, so it works on both
 * the plain and the streamed client classpath.
 *
 * Notes for maintainers:
 *   - The forwarded [[SiteBinding]] has its `authHeaders` cleared, because the engine appends
 *     `extraHeaders` to them; leaving a static `Authorization` there would send two.
 *   - The signed `Host` header is not forwarded - the HTTP client derives it from the URL (the
 *     same value), and the JDK client refuses a caller-set one.
 *   - The URL and body are signed as the underlying engine renders them - its own `wireUrl` /
 *     `renderJsonBody` (ws-client 1.2.0+), so the signature matches the bytes on the wire on
 *     every engine (query encoding and JSON escaping differ between engines).
 *   - Streams are signed PER RUN (inside `AsyncStream.defer`, at the moment the request goes
 *     out - the signature carries a timestamp), and keep the underlying engine's native stream
 *     (an Akka engine's `Source` stays native for an Akka consumer). The `Flow.Publisher`
 *     variants are signed when they are subscribed.
 *   - A signing failure (e.g. missing credentials) is returned as a failed `Future` / stream
 *     rather than thrown, matching what callers expect from the async API.
 *   - Multipart and raw-file uploads cannot be signed here: their bytes (and the multipart
 *     boundary) are generated inside the HTTP engine, so they cannot be hashed beforehand.
 *     They fail fast rather than sending an invalid signature. The Bedrock OpenAI-compatible
 *     endpoints do not serve those routes anyway.
 */
class SigningWSClientEngine private[aws] (
  protected val underlying: WSClientEngine,
  protected val credentials: AwsCredentialsProvider,
  protected val region: String,
  protected val awsService: String,
  protected val signedSites: SiteBinding => Boolean,
  protected val ownsUnderlying: Boolean
)(
  implicit protected val ec: ExecutionContext
) extends WSClientEngine {

  override def transportSettings: TransportSettings = underlying.transportSettings

  // a copy wraps a FRESH underlying engine that only the copy references, so it must own it
  override def copy(
    transportSettings: TransportSettings,
    reuseExecContext: Boolean
  ): WSClientEngine =
    SigningWSClientEngine(
      underlying.copy(transportSettings, reuseExecContext),
      credentials,
      region,
      awsService,
      signedSites,
      ownsUnderlying = true
    )

  override def close(): Unit = if (ownsUnderlying) underlying.close()

  // the wire format is the underlying engine's (a stacked signer signs what it sends)
  override def renderJsonBody(json: JsValue): String = underlying.renderJsonBody(json)

  override def wireUrl(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])]
  ): String =
    underlying.wireUrl(site, endPoint, endPointParam, params)

  // -- signing ---------------------------------------------------------------

  /** The site to forward (auth headers stripped) and the headers to forward. */
  protected def prepare(
    site: SiteBinding,
    method: String,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    body: => String,
    extraHeaders: Seq[(String, String)]
  ): Try[(SiteBinding, Seq[(String, String)])] =
    if (!signedSites(site)) Success((site, extraHeaders))
    else
      Try {
        // evaluated exactly once per request, so a rotating context is read consistently: the
        // forwarded site carries the evaluated context (no context function)
        val ctx = site.requestContextFn()
        val creds = credentials.resolve()
        val stripped = site.copy(
          requestContext = ctx.copy(authHeaders = Nil),
          requestContextFun = None
        )
        val url = underlying.wireUrl(stripped, endPoint, endPointParam, params)

        val signature = AwsSigV4
          .signedHeaders(
            method = method,
            url = url,
            headers = Map.empty,
            body = body,
            accessKey = creds.accessKeyId,
            secretKey = creds.secretAccessKey,
            region = region,
            service = awsService,
            sessionToken = creds.sessionToken
          )
          .toSeq
          // `Host` is signed (derived from the URL) but never forwarded: every HTTP client sets
          // it from the URL itself, and some refuse a caller-set one (the JDK client throws
          // "restricted header name")
          .filterNot { case (name, _) => name.equalsIgnoreCase("host") }

        (stripped, extraHeaders ++ signature)
      }

  protected def signedFuture(
    prepared: Try[(SiteBinding, Seq[(String, String)])]
  )(
    send: (SiteBinding, Seq[(String, String)]) => Future[RichResponse]
  ): Future[RichResponse] =
    prepared match {
      case Success((s, h)) => send(s, h)
      case Failure(e)      => Future.failed(e)
    }

  /** The sync engine always serializes a body, so an empty parameter list signs as `{}`. */
  protected def jsonBody(bodyParams: Seq[(String, Option[JsValue])]): String =
    // the body object as every engine builds it, rendered as the underlying engine sends it
    underlying.renderJsonBody(toJsBodyObject(bodyParams))

  /** The streamed engine attaches a body only when there are params, so empty signs as "". */
  protected def streamBody(bodyParams: Seq[(String, Option[JsValue])]): String =
    if (bodyParams.isEmpty) "" else jsonBody(bodyParams)

  protected def unsupported(what: String): Future[RichResponse] =
    Future.failed(
      new OpenAIScalaClientException(
        s"AWS SigV4 cannot sign a $what payload - its bytes (and multipart boundary) are " +
          "generated inside the HTTP engine and cannot be hashed beforehand. This endpoint is " +
          "not available on a SigV4-signed service; the Bedrock OpenAI-compatible endpoints do " +
          "not serve it."
      )
    )

  // -- delegations -----------------------------------------------------------

  override def execGETRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(prepare(site, "GET", endPoint, endPointParam, params, "", extraHeaders)) {
      (
        s,
        h
      ) => underlying.execGETRich(s, endPoint, endPointParam, params, h, acceptableStatusCodes)
    }

  override def execDELETERich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(prepare(site, "DELETE", endPoint, endPointParam, params, "", extraHeaders)) {
      (
        s,
        h
      ) =>
        underlying.execDELETERich(s, endPoint, endPointParam, params, h, acceptableStatusCodes)
    }

  override def execPOSTRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(
      prepare(
        site,
        "POST",
        endPoint,
        endPointParam,
        params,
        jsonBody(bodyParams),
        extraHeaders
      )
    ) {
      (
        s,
        h
      ) =>
        underlying.execPOSTRich(
          s,
          endPoint,
          endPointParam,
          params,
          bodyParams,
          h,
          acceptableStatusCodes
        )
    }

  override def execPATCHRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(
      prepare(
        site,
        "PATCH",
        endPoint,
        endPointParam,
        params,
        jsonBody(bodyParams),
        extraHeaders
      )
    ) {
      (
        s,
        h
      ) =>
        underlying.execPATCHRich(
          s,
          endPoint,
          endPointParam,
          params,
          bodyParams,
          h,
          acceptableStatusCodes
        )
    }

  override def execPUTRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(
      prepare(site, "PUT", endPoint, endPointParam, params, jsonBody(bodyParams), extraHeaders)
    ) {
      (
        s,
        h
      ) =>
        underlying.execPUTRich(
          s,
          endPoint,
          endPointParam,
          params,
          bodyParams,
          h,
          acceptableStatusCodes
        )
    }

  override def execPOSTBodyRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    body: JsValue,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(
      prepare(
        site,
        "POST",
        endPoint,
        endPointParam,
        params,
        underlying.renderJsonBody(body),
        extraHeaders
      )
    ) {
      (
        s,
        h
      ) =>
        underlying.execPOSTBodyRich(
          s,
          endPoint,
          endPointParam,
          params,
          body,
          h,
          acceptableStatusCodes
        )
    }

  override def execPUTBodyRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    body: JsValue,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] =
    signedFuture(
      prepare(
        site,
        "PUT",
        endPoint,
        endPointParam,
        params,
        underlying.renderJsonBody(body),
        extraHeaders
      )
    ) {
      (
        s,
        h
      ) =>
        underlying.execPUTBodyRich(
          s,
          endPoint,
          endPointParam,
          params,
          body,
          h,
          acceptableStatusCodes
        )
    }

  // -- unsignable payloads ---------------------------------------------------

  override def execPOSTMultipartRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    fileParams: Seq[(String, File, Option[String])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int],
    useInMemoryBody: Boolean
  )(
    implicit filePartToContent: FilePart => String
  ): Future[RichResponse] = unsupported("multipart")

  override def execPUTMultipartRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    fileParams: Seq[(String, File, Option[String])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int],
    useInMemoryBody: Boolean
  )(
    implicit filePartToContent: FilePart => String
  ): Future[RichResponse] = unsupported("multipart")

  override def execPOSTFileRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    file: File,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = unsupported("raw file")

  override def execPUTFileRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    file: File,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = unsupported("raw file")

  override def execPOSTURLEncodedRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = unsupported("url-encoded form")
}

/** The signing decorator over a streaming-capable engine - adds the signed stream methods. */
class SigningStreamedWSClientEngine private[aws] (
  protected val streamedUnderlying: WSClientEngine with WSClientOutputStreamCore,
  credentials: AwsCredentialsProvider,
  region: String,
  awsService: String,
  signedSites: SiteBinding => Boolean,
  ownsUnderlying: Boolean
)(
  implicit ec: ExecutionContext
) extends SigningWSClientEngine(
      streamedUnderlying,
      credentials,
      region,
      awsService,
      signedSites,
      ownsUnderlying
    )
    with WSClientOutputStreamCore {

  override def copy(
    transportSettings: TransportSettings,
    reuseExecContext: Boolean
  ): WSClientEngine with WSClientOutputStreamCore =
    new SigningStreamedWSClientEngine(
      streamedUnderlying.copy(transportSettings, reuseExecContext),
      credentials,
      region,
      awsService,
      signedSites,
      ownsUnderlying = true
    )

  // signed per run, when the request goes out; the underlying engine's (native) stream is kept
  override def execJsonAsyncStream(
    site: SiteBinding,
    endPoint: String,
    method: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    framingDelimiter: String,
    maxFrameLength: Option[Int],
    stripPrefix: Option[String],
    stripSuffix: Option[String]
  ): AsyncStream[JsValue] =
    AsyncStream.defer(
      prepare(
        site,
        method,
        endPoint,
        endPointParam,
        params,
        streamBody(bodyParams),
        extraHeaders
      ) match {
        case Success((s, h)) =>
          streamedUnderlying.execJsonAsyncStream(
            s,
            endPoint,
            method,
            endPointParam,
            params,
            bodyParams,
            h,
            framingDelimiter,
            maxFrameLength,
            stripPrefix,
            stripSuffix
          )
        case Failure(e) => AsyncStream.failed(e)
      }
    )

  override def execRawAsyncStream(
    site: SiteBinding,
    endPoint: String,
    method: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)]
  ): AsyncStream[Bytes] =
    AsyncStream.defer(
      prepare(
        site,
        method,
        endPoint,
        endPointParam,
        params,
        streamBody(bodyParams),
        extraHeaders
      ) match {
        case Success((s, h)) =>
          streamedUnderlying.execRawAsyncStream(
            s,
            endPoint,
            method,
            endPointParam,
            params,
            bodyParams,
            h
          )
        case Failure(e) => AsyncStream.failed(e)
      }
    )

  override def execJsonStreamPublisher(
    site: SiteBinding,
    endPoint: String,
    method: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    framingDelimiter: String,
    maxFrameLength: Option[Int],
    stripPrefix: Option[String],
    stripSuffix: Option[String]
  ): Flow.Publisher[JsValue] =
    signedOnSubscribe(
      prepare(
        site,
        method,
        endPoint,
        endPointParam,
        params,
        streamBody(bodyParams),
        extraHeaders
      ).map { case (s, h) =>
        streamedUnderlying.execJsonStreamPublisher(
          s,
          endPoint,
          method,
          endPointParam,
          params,
          bodyParams,
          h,
          framingDelimiter,
          maxFrameLength,
          stripPrefix,
          stripSuffix
        )
      }
    )

  override def execRawStreamPublisher(
    site: SiteBinding,
    endPoint: String,
    method: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)]
  ): Flow.Publisher[ByteBuffer] =
    signedOnSubscribe(
      prepare(
        site,
        method,
        endPoint,
        endPointParam,
        params,
        streamBody(bodyParams),
        extraHeaders
      ).map { case (s, h) =>
        streamedUnderlying.execRawStreamPublisher(
          s,
          endPoint,
          method,
          endPointParam,
          params,
          bodyParams,
          h
        )
      }
    )

  // a cold, single-shot publisher: signed (and the underlying publisher created) when the
  // first subscriber arrives; a signing failure is delivered as onError, a second subscriber
  // is rejected (the publishers of the engine contract are single-shot)
  private def signedOnSubscribe[T](create: => Try[Flow.Publisher[T]]): Flow.Publisher[T] =
    new DeferredPublisher[T](() => create.get)
}

object SigningWSClientEngine {

  /**
   * Signs only sites on an AWS host, so a shared engine never leaks AWS credentials elsewhere.
   */
  val awsSitesOnly: SiteBinding => Boolean = site =>
    Try(new java.net.URL(site.coreUrl).getHost.toLowerCase).toOption.exists { host =>
      host.endsWith(".amazonaws.com") || host.endsWith(".api.aws")
    }

  /**
   * Wraps `underlying` so that every request it makes to an AWS host is SigV4-signed - over
   * the URL and body exactly as `underlying` sends them (its `wireUrl` / `renderJsonBody`).
   * Returns a [[SigningStreamedWSClientEngine]] when `underlying` streams, a plain
   * [[SigningWSClientEngine]] otherwise.
   *
   * @param signedSites
   *   which sites get signed; defaults to AWS hosts only, which is what makes sharing the
   *   engine with non-AWS providers safe.
   * @param ownsUnderlying
   *   when true, closing this engine closes the wrapped one.
   */
  def apply(
    underlying: WSClientEngine,
    credentials: AwsCredentialsProvider,
    region: String,
    awsService: String = "bedrock",
    signedSites: SiteBinding => Boolean = awsSitesOnly,
    ownsUnderlying: Boolean = true
  )(
    implicit ec: ExecutionContext
  ): WSClientEngine =
    underlying match {
      case streamed: WSClientEngine with WSClientOutputStreamCore =>
        new SigningStreamedWSClientEngine(
          streamed,
          credentials,
          region,
          awsService,
          signedSites,
          ownsUnderlying
        )
      case plain =>
        new SigningWSClientEngine(
          plain,
          credentials,
          region,
          awsService,
          signedSites,
          ownsUnderlying
        )
    }
}
