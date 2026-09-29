package io.cequence.openaiscala.vertexai.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.google.api.core.{ApiFuture, ApiFutures}
import com.google.api.gax.grpc.GrpcStatusCode
import com.google.api.gax.rpc._
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.stub.PredictionServiceStub
import com.google.cloud.vertexai.api.{
  Candidate,
  Content,
  GenerateContentRequest,
  GenerateContentResponse,
  Part,
  PredictionServiceClient
}
import io.cequence.openaiscala._
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import io.grpc.Status
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag

/**
 * Failures of the Vertex AI adapter end to end through the real SDK (`VertexAI` ->
 * `GenerativeModel` -> `PredictionServiceClient` -> gax server stream / unary future), with
 * the transport replaced by a scripted [[PredictionServiceStub]]: an error when the stream
 * opens, one in the middle of a stream (after a chunk was delivered), wrapped ones, and failed
 * unary calls - each must surface as the right, `Retryable`-classified `OpenAIScala*`
 * exception.
 */
class VertexAIStreamedErrorsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("vertexai-streamed-errors")
  private implicit val materializer: Materializer = Materializer(system)

  // what the next streamed call emits: chunks (Right) and a terminal error (Left)
  @volatile private var script: Seq[Either[Throwable, GenerateContentResponse]] = Nil
  // how the next unary call fails
  @volatile private var unaryFailure: Throwable = new RuntimeException("unset")

  private object ScriptedStream
      extends ServerStreamingCallable[GenerateContentRequest, GenerateContentResponse] {

    override def call(
      request: GenerateContentRequest,
      observer: ResponseObserver[GenerateContentResponse],
      context: ApiCallContext
    ): Unit = {
      val remaining = mutable.Queue(script: _*)
      var autoFlow = true
      var done = false

      def emitNext(): Unit =
        if (!done) {
          if (remaining.isEmpty) { done = true; observer.onComplete() }
          else
            remaining.dequeue() match {
              case Right(response) => observer.onResponse(response)
              case Left(error)     => done = true; observer.onError(error)
            }
        }

      observer.onStart(new StreamController {
        override def cancel(): Unit = done = true
        override def disableAutoInboundFlowControl(): Unit = autoFlow = false
        override def request(count: Int): Unit = (1 to count).foreach(_ => emitNext())
      })
      if (autoFlow) while (!done) emitNext()
    }
  }

  private object FailingUnary
      extends UnaryCallable[GenerateContentRequest, GenerateContentResponse] {
    override def futureCall(
      request: GenerateContentRequest,
      context: ApiCallContext
    ): ApiFuture[GenerateContentResponse] =
      ApiFutures.immediateFailedFuture(unaryFailure)
  }

  private object ScriptedStub extends PredictionServiceStub {
    override def streamGenerateContentCallable() = ScriptedStream
    override def generateContentCallable() = FailingUnary
    override def close(): Unit = ()
    override def shutdown(): Unit = ()
    override def isShutdown: Boolean = false
    override def isTerminated: Boolean = false
    override def shutdownNow(): Unit = ()
    override def awaitTermination(
      duration: Long,
      unit: TimeUnit
    ): Boolean = true
  }

  private val vertexAI = new VertexAI.Builder()
    .setProjectId("test-project")
    .setLocation("us-central1")
    .setCredentials(GoogleCredentials.create(new AccessToken("token", null)))
    .setPredictionClientSupplier(() => PredictionServiceClient.create(ScriptedStub))
    .build()

  private val service = new OpenAIVertexAIChatCompletionService(vertexAI)

  override protected def afterAll(): Unit = {
    service.close()
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def apiError(
    code: Status.Code,
    message: String
  ): ApiException =
    ApiExceptionFactory.createException(
      new RuntimeException(message),
      GrpcStatusCode.of(code),
      false
    )

  private val chunk = GenerateContentResponse
    .newBuilder()
    .addCandidates(
      Candidate
        .newBuilder()
        .setIndex(0)
        .setContent(
          Content.newBuilder().setRole("model").addParts(Part.newBuilder().setText("Hel"))
        )
    )
    .build()

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gemini-3.5-flash")

  // every element until the failure, then the failure
  private def run[T](stream: Source[T, _]): (Seq[T], Throwable) = {
    val events = Await.result(
      stream
        .map(Right(_): Either[Throwable, T])
        .recover { case e => Left(e) }
        .runWith(Sink.seq),
      20.seconds
    )
    val failure =
      events.collectFirst { case Left(e) => e }.getOrElse(fail("the stream completed"))
    (events.collect { case Right(t) => t }, failure)
  }

  private def failureOf[E <: Throwable: ClassTag](stream: Source[_, _]): E = {
    val (_, failure) = run(stream)
    failure shouldBe a[E]
    failure.asInstanceOf[E]
  }

  "a streamed chat completion" should {

    "fail with the classified exception when the stream opens" in {
      script = Seq(Left(apiError(Status.Code.RESOURCE_EXHAUSTED, "Quota exceeded")))
      val rateLimit = failureOf[OpenAIScalaRateLimitException](
        service.createChatCompletionStreamed(messages, settings)
      )
      Retryable(rateLimit) shouldBe true
      rateLimit.getCause shouldBe a[ResourceExhaustedException]
    }

    "deliver the chunks before a failure in the middle of the stream, then fail classified" in {
      script = Seq(Right(chunk), Left(apiError(Status.Code.UNAVAILABLE, "Service is down")))
      val (chunks, failure) = run(service.createChatCompletionStreamed(messages, settings))

      chunks.flatMap(_.choices.flatMap(_.delta.content)) shouldBe Seq("Hel")
      failure shouldBe an[OpenAIScalaEngineOverloadedException]
      Retryable(failure.asInstanceOf[OpenAIScalaClientException]) shouldBe true
    }

    "classify a token-count INVALID_ARGUMENT as not retryable" in {
      script = Seq(
        Left(
          apiError(
            Status.Code.INVALID_ARGUMENT,
            "The input token count (1200000) exceeds the maximum number of tokens allowed (1048576)."
          )
        )
      )
      Retryable(
        failureOf[OpenAIScalaTokenCountExceededException](
          service.createChatCompletionStreamedTyped(messages, settings)
        )
      ) shouldBe false
    }
  }

  "a typed tool stream" should {

    "classify a wrapped raw gRPC status failure, mid-stream" in {
      script = Seq(
        Right(chunk),
        Left(new RuntimeException("wrapped", Status.PERMISSION_DENIED.asRuntimeException()))
      )
      val tool = FunctionTool("get_weather", parameters = JsonSchema.Object(properties = Nil))
      val (chunks, failure) =
        run(service.createChatToolCompletionStreamed(messages, Seq(tool), None, settings))

      chunks should not be empty
      failure shouldBe an[OpenAIScalaUnauthorizedException]
    }
  }

  "a unary chat completion" should {

    "fail with the classified exception through the Java future wrappers" in {
      unaryFailure = apiError(Status.Code.INTERNAL, "Internal error encountered.")
      val serverError = intercept[OpenAIScalaServerErrorException](
        Await.result(service.createChatCompletion(messages, settings), 20.seconds)
      )
      Retryable(serverError) shouldBe true

      unaryFailure = apiError(Status.Code.DEADLINE_EXCEEDED, "Deadline exceeded")
      intercept[OpenAIScalaClientTimeoutException](
        Await.result(
          service.createChatToolCompletion(messages, Nil, None, settings),
          20.seconds
        )
      )
    }
  }
}
