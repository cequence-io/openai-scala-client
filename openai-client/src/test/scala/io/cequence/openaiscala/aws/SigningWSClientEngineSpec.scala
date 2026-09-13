package io.cequence.openaiscala.aws

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.{RichResponse, SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.FilePart
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import java.io.File
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * [[SigningWSClientEngine]] over a recording fake engine: what site and headers reach the
 * wrapped engine, and how signing failures surface.
 */
class SigningWSClientEngineSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  /** Records the site and extra headers of every call; never does I/O. */
  private class RecordingEngine extends WSClientEngine {
    val calls: mutable.ListBuffer[(String, SiteBinding, Seq[(String, String)])] =
      mutable.ListBuffer.empty
    var closed = 0

    implicit val ec: ExecutionContext = ExecutionContext.global
    override def transportSettings: TransportSettings = TransportSettings()

    override def copy(
      transportSettings: TransportSettings,
      reuseExecContext: Boolean
    ): WSClientEngine = new RecordingEngine

    override def close(): Unit = closed += 1

    private def record(
      method: String,
      site: SiteBinding,
      headers: Seq[(String, String)]
    ): Future[RichResponse] = {
      calls += ((method, site, headers))
      Future.failed(new RuntimeException("recorded"))
    }

    override def execGETRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("GET", site, extraHeaders)

    override def execDELETERich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("DELETE", site, extraHeaders)

    override def execPOSTRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      bodyParams: Seq[(String, Option[JsValue])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("POST", site, extraHeaders)

    override def execPATCHRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      bodyParams: Seq[(String, Option[JsValue])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("PATCH", site, extraHeaders)

    override def execPUTRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      bodyParams: Seq[(String, Option[JsValue])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("PUT", site, extraHeaders)

    override def execPOSTBodyRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      body: JsValue,
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("POST-body", site, extraHeaders)

    override def execPUTBodyRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      body: JsValue,
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("PUT-body", site, extraHeaders)

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
    ): Future[RichResponse] = record("POST-multipart", site, extraHeaders)

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
    ): Future[RichResponse] = record("PUT-multipart", site, extraHeaders)

    override def execPOSTFileRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      file: File,
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("POST-file", site, extraHeaders)

    override def execPUTFileRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      file: File,
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("PUT-file", site, extraHeaders)

    override def execPOSTURLEncodedRich(
      site: SiteBinding,
      endPoint: String,
      endPointParam: Option[String],
      params: Seq[(String, Option[Any])],
      bodyParams: Seq[(String, Option[Any])],
      extraHeaders: Seq[(String, String)],
      acceptableStatusCodes: Seq[Int]
    ): Future[RichResponse] = record("POST-form", site, extraHeaders)
  }

  private val creds = AwsCredentialsProvider.static("AKIDEXAMPLE", "secret")

  private val awsSite = SiteBinding(
    "https://bedrock-mantle.us-east-1.api.aws/openai/v1/",
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer stale-token"))
  )

  private def engine(
    fake: RecordingEngine,
    credentials: AwsCredentialsProvider = creds,
    owns: Boolean = true
  ): WSClientEngine =
    SigningWSClientEngine(fake, credentials, "us-east-1", ownsUnderlying = owns)

  private def authHeader(headers: Seq[(String, String)]): Seq[String] =
    headers.collect { case ("Authorization", v) => v }

  "SigningWSClientEngine" should {

    "sign a request exactly once and strip the site's static auth headers" in {
      val fake = new RecordingEngine
      engine(fake).execPOSTRich(awsSite, "chat/completions", None, Nil, Nil, Nil, Nil)

      val (_, forwardedSite, headers) = fake.calls.head
      val auth = authHeader(headers)
      auth should have size 1
      auth.head should startWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
      auth.head should include("/us-east-1/bedrock/aws4_request")
      headers.map(_._1) should contain("X-Amz-Date")
      // the stale bearer must not ride alongside the signature
      forwardedSite.requestContext.authHeaders shouldBe Nil
      forwardedSite.requestContextFun shouldBe None
    }

    "send the session token as a signed header when credentials carry one" in {
      val fake = new RecordingEngine
      val temporary = AwsCredentialsProvider.static("AKID", "secret", Some("session-token"))
      engine(fake, temporary).execGETRich(awsSite, "models", None, Nil, Nil, Nil)

      val headers = fake.calls.head._3
      headers should contain("X-Amz-Security-Token" -> "session-token")
      authHeader(headers).head should include("x-amz-security-token")
    }

    "leave a non-AWS site untouched, so a shared engine never leaks AWS credentials" in {
      val fake = new RecordingEngine
      val openAISite = SiteBinding(
        "https://api.openai.com/v1/",
        WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer sk-openai"))
      )
      engine(fake).execPOSTRich(openAISite, "chat/completions", None, Nil, Nil, Nil, Nil)

      val (_, forwardedSite, headers) = fake.calls.head
      forwardedSite shouldBe openAISite
      headers shouldBe Nil
    }

    "return a failed Future rather than throwing when credentials cannot be resolved" in {
      val fake = new RecordingEngine
      val broken =
        AwsCredentialsProvider(() => throw new OpenAIScalaClientException("no credentials"))

      val result = engine(fake, broken).execPOSTRich(awsSite, "x", None, Nil, Nil, Nil, Nil)

      result.failed.futureValue shouldBe an[OpenAIScalaClientException]
      fake.calls shouldBe empty
    }

    "sign an empty sync body as {} - the engine always serializes one" in {
      val fake = new RecordingEngine
      val e = engine(fake)
      e.execPOSTRich(awsSite, "x", None, Nil, Nil, Nil, Nil)
      e.execPOSTBodyRich(awsSite, "x", None, Nil, Json.obj(), Nil, Nil)

      // both hash the same "{}" payload, so their signatures agree (same second, same URL)
      val sigs = fake.calls.map(c => authHeader(c._3).head.split("Signature=").last)
      sigs.distinct should have size 1
    }

    "fail fast on payloads it cannot hash instead of sending a bad signature" in {
      val fake = new RecordingEngine
      implicit val contentType: FilePart => String = _ => "text/plain"

      val result = engine(fake).execPOSTMultipartRich(
        awsSite,
        "files",
        None,
        Nil,
        Nil,
        Nil,
        Nil,
        Nil,
        useInMemoryBody = false
      )

      result.failed.futureValue.getMessage should include("multipart")
      fake.calls shouldBe empty
    }

    "close the wrapped engine only when it owns it" in {
      val owned = new RecordingEngine
      engine(owned, owns = true).close()
      owned.closed shouldBe 1

      val shared = new RecordingEngine
      engine(shared, owns = false).close()
      shared.closed shouldBe 0
    }

    "own the fresh engine a copy wraps, whatever the original owned" in {
      val shared = new RecordingEngine
      val copy =
        engine(shared, owns = false).copy(TransportSettings(), reuseExecContext = true)
      copy.close()
      // the copy closed its own fresh engine, not the shared original
      shared.closed shouldBe 0
    }

    "wrap a plain engine as a plain signing engine, not a streamed one" in {
      engine(new RecordingEngine) should not be a[SigningStreamedWSClientEngine]
    }
  }
}
