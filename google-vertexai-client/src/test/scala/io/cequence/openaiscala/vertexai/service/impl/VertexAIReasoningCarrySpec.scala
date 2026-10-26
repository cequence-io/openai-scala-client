package io.cequence.openaiscala.vertexai.service.impl

import com.google.api.core.{ApiFuture, ApiFutures}
import com.google.api.gax.rpc.{ApiCallContext, UnaryCallable}
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.stub.PredictionServiceStub
import com.google.cloud.vertexai.api.{
  Candidate,
  Content,
  FunctionCall,
  GenerateContentRequest,
  GenerateContentResponse,
  Part,
  PredictionServiceClient
}
import com.google.protobuf.{ByteString, Struct, Value}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  JsonSchema,
  ReasoningBlock,
  ReasoningMessage,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.vertexai.domain.settings.CreateChatCompletionSettingsOps._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Reasoning carried between the rounds of a Vertex AI tool loop, end to end through the real
 * SDK (`VertexAI` -> `GenerativeModel` -> `PredictionServiceClient`) with the transport
 * replaced by a stub that records each request and answers with a thought part and a function
 * call: a sync call asks for thoughts only when told to (`setVertexAIIncludeThoughts`), reads
 * them as the reply's reasoning, and sends its own back as thought parts opening the model
 * turn of the next round.
 */
class VertexAIReasoningCarrySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val signature = "sig-1".getBytes("UTF-8")

  // a reply that thought (signed) and called a tool
  private val thoughtfulReply = GenerateContentResponse
    .newBuilder()
    .addCandidates(
      Candidate
        .newBuilder()
        .setIndex(0)
        .setContent(
          Content
            .newBuilder()
            .setRole("model")
            .addParts(
              Part
                .newBuilder()
                .setText("Oslo is closer.")
                .setThought(true)
                .setThoughtSignature(ByteString.copyFrom(signature))
            )
            .addParts(
              Part
                .newBuilder()
                .setFunctionCall(
                  FunctionCall
                    .newBuilder()
                    .setName("get_weather")
                    .setArgs(
                      Struct
                        .newBuilder()
                        .putFields("city", Value.newBuilder().setStringValue("Oslo").build())
                    )
                )
            )
        )
    )
    .build()

  // records every request of a VertexAI instance and answers with the reply above
  private class Recording {
    val requests = mutable.ArrayBuffer.empty[GenerateContentRequest]

    private object Unary
        extends UnaryCallable[GenerateContentRequest, GenerateContentResponse] {
      override def futureCall(
        request: GenerateContentRequest,
        context: ApiCallContext
      ): ApiFuture[GenerateContentResponse] = {
        requests.synchronized {
          requests += request
        }
        ApiFutures.immediateFuture(thoughtfulReply)
      }
    }

    private object Stub extends PredictionServiceStub {
      override def generateContentCallable() = Unary
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
      .setPredictionClientSupplier(() => PredictionServiceClient.create(Stub))
      .build()

    val service = new OpenAIVertexAIChatCompletionService(vertexAI)

    def last: GenerateContentRequest = requests.synchronized(requests.last)
  }

  private val recordings = mutable.ArrayBuffer.empty[Recording]

  private def recording(): Recording = {
    val recording = new Recording
    recordings += recording
    recording
  }

  override protected def afterAll(): Unit =
    recordings.foreach(_.service.close())

  private def await[T](future: Future[T]): T = Await.result(future, 20.seconds)

  private val weather = FunctionTool(
    "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val question = UserMessage("What's the weather where the trains meet?")
  private val settings = CreateChatCompletionSettings("gemini-2.5-flash")

  private def includeThoughts(request: GenerateContentRequest): Boolean =
    request.getGenerationConfig.hasThinkingConfig &&
      request.getGenerationConfig.getThinkingConfig.getIncludeThoughts

  "The Vertex AI adapter" should {

    "ask a sync call for thoughts only when told to" in {
      val vertex = recording()

      await(vertex.service.createChatCompletion(Seq(question), settings))
      includeThoughts(vertex.last) shouldBe false

      await(
        vertex.service.createChatToolCompletion(Seq(question), Seq(weather), None, settings)
      )
      includeThoughts(vertex.last) shouldBe false

      await(
        vertex.service
          .createChatCompletion(Seq(question), settings.setVertexAIIncludeThoughts())
      )
      includeThoughts(vertex.last) shouldBe true

      await(
        vertex.service.createChatToolCompletion(
          Seq(question),
          Seq(weather),
          None,
          settings.setVertexAIIncludeThoughts()
        )
      )
      includeThoughts(vertex.last) shouldBe true
    }

    "read a reply's thoughts as its reasoning and send them back as thought parts" in {
      val vertex = recording()
      val thinking = settings.setVertexAIIncludeThoughts()

      val first =
        await(
          vertex.service.createChatToolCompletion(Seq(question), Seq(weather), None, thinking)
        )

      first.reasoningMessage shouldBe Some(
        ReasoningMessage(
          Seq(
            ReasoningBlock(
              text = Some("Oslo is closer."),
              signature = Some(Base64.getEncoder.encodeToString(signature))
            )
          )
        )
      )
      // the thought is the reasoning, not the answer
      first.assistantToolMessage.content shouldBe None
      first.historyMessages shouldBe Seq(
        first.reasoningMessage.get,
        first.assistantToolMessage
      )

      val toolResult =
        ToolMessage(Some("4C"), first.assistantToolMessage.tool_calls.head._1, "get_weather")
      await(
        vertex.service.createChatToolCompletion(
          Seq(question) ++ first.historyMessages :+ toolResult,
          Seq(weather),
          None,
          thinking
        )
      )

      val contents = vertex.last.getContentsList.toSeq
      contents.map(_.getRole) shouldBe Seq("USER", "MODEL", "USER")
      val modelParts = contents(1).getPartsList.toSeq
      modelParts.head.getThought shouldBe true
      modelParts.head.getText shouldBe "Oslo is closer."
      modelParts.head.getThoughtSignature.toByteArray shouldBe signature
      modelParts(1).getFunctionCall.getName shouldBe "get_weather"
    }

    "send back only the thoughts its own service returned" in {
      val vertex = recording()
      val other = recording()
      val thinking = settings.setVertexAIIncludeThoughts()

      val first =
        await(
          vertex.service.createChatToolCompletion(Seq(question), Seq(weather), None, thinking)
        )
      val toolResult =
        ToolMessage(Some("4C"), first.assistantToolMessage.tool_calls.head._1, "get_weather")
      val history = Seq(question) ++ first.historyMessages :+ toolResult

      // another Vertex AI service (project, credentials) leaves them out
      await(other.service.createChatToolCompletion(history, Seq(weather), None, thinking))
      other.last.getContentsList.toSeq(1).getPartsList.toSeq.map(_.getThought) shouldBe
        Seq(false)

      // and so does this one for a thought it never returned
      await(
        vertex.service.createChatToolCompletion(
          Seq(
            question,
            ReasoningMessage(Seq(ReasoningBlock(text = Some("Made up.")))),
            first.assistantToolMessage,
            toolResult
          ),
          Seq(weather),
          None,
          thinking
        )
      )
      vertex.last.getContentsList.toSeq(1).getPartsList.toSeq.map(_.getThought) shouldBe
        Seq(false)
    }
  }
}
