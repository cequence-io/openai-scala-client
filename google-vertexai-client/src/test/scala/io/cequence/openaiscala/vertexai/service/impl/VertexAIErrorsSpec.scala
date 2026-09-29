package io.cequence.openaiscala.vertexai.service.impl

import com.google.api.gax.grpc.GrpcStatusCode
import com.google.api.gax.rpc.ApiExceptionFactory
import com.google.common.util.concurrent.UncheckedExecutionException
import io.cequence.openaiscala._
import io.cequence.wsclient.domain.{CequenceWSTimeoutException, CequenceWSUnknownHostException}
import io.grpc.Status
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.{CompletionException, ExecutionException}

/**
 * [[VertexAIErrors]]: every canonical status, on every transport shape (gax, raw gRPC, REST
 * body), wrapped or not, maps to the right `OpenAIScala*` exception and `Retryable` verdict.
 */
class VertexAIErrorsSpec extends AnyWordSpec with Matchers {

  // status -> (expected exception class, retryable)
  private val table: Seq[(Status.Code, Class[_], Boolean)] = Seq(
    (Status.Code.RESOURCE_EXHAUSTED, classOf[OpenAIScalaRateLimitException], true),
    (Status.Code.UNAVAILABLE, classOf[OpenAIScalaEngineOverloadedException], true),
    (Status.Code.DEADLINE_EXCEEDED, classOf[OpenAIScalaClientTimeoutException], true),
    (Status.Code.INTERNAL, classOf[OpenAIScalaServerErrorException], true),
    (Status.Code.UNKNOWN, classOf[OpenAIScalaServerErrorException], true),
    (Status.Code.DATA_LOSS, classOf[OpenAIScalaServerErrorException], true),
    (Status.Code.UNAUTHENTICATED, classOf[OpenAIScalaUnauthorizedException], false),
    (Status.Code.PERMISSION_DENIED, classOf[OpenAIScalaUnauthorizedException], false),
    (Status.Code.INVALID_ARGUMENT, classOf[OpenAIScalaClientException], false),
    (Status.Code.NOT_FOUND, classOf[OpenAIScalaClientException], false),
    (Status.Code.FAILED_PRECONDITION, classOf[OpenAIScalaClientException], false),
    (Status.Code.OUT_OF_RANGE, classOf[OpenAIScalaClientException], false),
    (Status.Code.ALREADY_EXISTS, classOf[OpenAIScalaClientException], false),
    (Status.Code.ABORTED, classOf[OpenAIScalaClientException], false),
    (Status.Code.CANCELLED, classOf[OpenAIScalaClientException], false),
    (Status.Code.UNIMPLEMENTED, classOf[OpenAIScalaClientException], false)
  )

  private def gax(code: Status.Code) =
    ApiExceptionFactory.createException(
      new RuntimeException(s"$code happened"),
      GrpcStatusCode.of(code),
      false
    )

  private def check(
    mapped: Throwable,
    expected: Class[_],
    retryable: Boolean
  ) = {
    mapped.getClass shouldBe expected
    Retryable(mapped.asInstanceOf[OpenAIScalaClientException]) shouldBe retryable
  }

  "VertexAIErrors.toOpenAIException" should {

    "map every canonical status of a gax ApiException, raw or wrapped" in {
      table.foreach { case (code, expected, retryable) =>
        withClue(code) {
          check(VertexAIErrors.toOpenAIException(gax(code)), expected, retryable)
          check(
            VertexAIErrors.toOpenAIException(
              new CompletionException(new ExecutionException(gax(code)))
            ),
            expected,
            retryable
          )
          check(
            VertexAIErrors.toOpenAIException(new UncheckedExecutionException(gax(code))),
            expected,
            retryable
          )
        }
      }
    }

    "map raw gRPC status exceptions anywhere in the cause chain" in {
      table.foreach { case (code, expected, retryable) =>
        withClue(code) {
          check(
            VertexAIErrors.toOpenAIException(Status.fromCode(code).asRuntimeException()),
            expected,
            retryable
          )
          check(
            VertexAIErrors.toOpenAIException(
              new RuntimeException("outer", Status.fromCode(code).asException())
            ),
            expected,
            retryable
          )
        }
      }
    }

    "keep the original as the cause and classify token-count errors" in {
      val original = gax(Status.Code.RESOURCE_EXHAUSTED)
      VertexAIErrors.toOpenAIException(original).getCause shouldBe theSameInstanceAs(original)

      val tooLong = ApiExceptionFactory.createException(
        new RuntimeException(
          "The input token count (2000000) exceeds the maximum number of tokens allowed (1048576)."
        ),
        GrpcStatusCode.of(Status.Code.INVALID_ARGUMENT),
        false
      )
      VertexAIErrors.toOpenAIException(tooLong) shouldBe an[
        OpenAIScalaTokenCountExceededException
      ]
    }

    "map ws-client transport failures, pass classified ones through, unwrap the rest" in {
      VertexAIErrors.toOpenAIException(
        new CequenceWSTimeoutException("timed out")
      ) shouldBe an[OpenAIScalaClientTimeoutException]
      VertexAIErrors.toOpenAIException(
        new CequenceWSUnknownHostException("no host")
      ) shouldBe an[OpenAIScalaClientUnknownHostException]

      val already = new OpenAIScalaRateLimitException("mapped")
      VertexAIErrors.toOpenAIException(already) shouldBe theSameInstanceAs(already)

      val plain = new IllegalArgumentException("bad input")
      VertexAIErrors.toOpenAIException(plain) shouldBe theSameInstanceAs(plain)
      VertexAIErrors.toOpenAIException(
        new CompletionException(new ExecutionException(plain))
      ) shouldBe theSameInstanceAs(plain)
    }

    "search a bounded depth of the cause chain" in {
      def wrapped(layers: Int): Throwable =
        (1 to layers).foldLeft(gax(Status.Code.UNAVAILABLE): Throwable)(
          (
            cause,
            _
          ) => new RuntimeException("layer", cause)
        )

      VertexAIErrors.toOpenAIException(wrapped(20)) shouldBe an[
        OpenAIScalaEngineOverloadedException
      ]
      val tooDeep = wrapped(100)
      VertexAIErrors.toOpenAIException(tooDeep) shouldBe theSameInstanceAs(tooDeep)
    }
  }

  "VertexAIErrors.fromHttp" should {

    def body(
      code: Int,
      status: String,
      message: String
    ) = s"""{"error":{"code":$code,"message":"$message","status":"$status"}}"""

    "classify a Google REST error body by its status, with its message" in {
      val rateLimit =
        VertexAIErrors.fromHttp(429, body(429, "RESOURCE_EXHAUSTED", "Quota exceeded."))
      rateLimit shouldBe an[OpenAIScalaRateLimitException]
      rateLimit.getMessage shouldBe "Code 429 : Quota exceeded."

      VertexAIErrors
        .fromHttp(
          400,
          body(400, "FAILED_PRECONDITION", "Model is not ready")
        )
        .getClass shouldBe classOf[OpenAIScalaClientException]
      VertexAIErrors
        .fromHttp(
          404,
          body(404, "NOT_FOUND", "BatchPredictionJob does not exist.")
        )
        .getClass shouldBe classOf[OpenAIScalaClientException]
      VertexAIErrors.fromHttp(
        403,
        body(403, "PERMISSION_DENIED", "Permission denied")
      ) shouldBe an[OpenAIScalaUnauthorizedException]
    }

    "fall back to the HTTP code for a non-Google body" in {
      VertexAIErrors.fromHttp(503, "Service Unavailable") shouldBe an[
        OpenAIScalaEngineOverloadedException
      ]
      VertexAIErrors.fromHttp(502, "<html>Bad Gateway</html>") shouldBe an[
        OpenAIScalaServerErrorException
      ]
      VertexAIErrors.fromHttp(429, "") shouldBe an[OpenAIScalaRateLimitException]
      VertexAIErrors.fromHttp(401, """{"error":{"message":"no"}}""") shouldBe an[
        OpenAIScalaUnauthorizedException
      ]
      VertexAIErrors
        .fromHttp(400, "nope")
        .getClass shouldBe classOf[OpenAIScalaClientException]
    }
  }
}
