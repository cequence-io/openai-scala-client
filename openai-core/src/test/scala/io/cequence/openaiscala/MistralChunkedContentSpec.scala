package io.cequence.openaiscala

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{AssistantMessage, FunctionCallSpec}
import io.cequence.openaiscala.service.ChatChunks
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * Mistral's reasoning turns answer `content` as a list of thinking / text chunks rather than a
 * string - read from the responses recorded live on 2026-10-07 (Mistral Large 4).
 */
class MistralChunkedContentSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("mistral-chunked-content")
  private implicit val materializer: Materializer = Materializer(system)
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds))

  override protected def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def resource(name: String): String = {
    val source = scala.io.Source.fromResource(s"mistral/$name")
    try source.mkString
    finally source.close()
  }

  "A chat completion whose content is a list of chunks" should {

    "read the text chunks as the content" in {
      val response = Json.parse(resource("large-4-sync.json")).as[ChatCompletionResponse]

      response.model shouldBe "mistral-large-4"
      response.contentHead shouldBe "391"
    }

    "read a tool call whose content holds only thinking" in {
      val message = Json
        .parse(resource("large-4-tool-call.json"))
        .as[ChatToolCompletionResponse]
        .choices
        .head
        .message

      message.content shouldBe None
      message.tool_calls.map(_._2).collect { case call: FunctionCallSpec =>
        call.name
      } shouldBe
        Seq("get_weather")
    }

    "read a list of chunks of other kinds as no content - never refusing it - and keep them on a delta" in {
      val unknown = Json.parse("""{"content": [{"type": "output_text", "text": "hi"}]}""")

      unknown.as[AssistantMessage].content shouldBe ""
      Json.parse("""{"content": []}""").as[AssistantMessage].content shouldBe ""
      val delta = unknown.as[ChunkMessageSpec]
      delta.content shouldBe None
      delta.content_chunks shouldBe Some(
        Seq(Json.obj("type" -> "output_text", "text" -> "hi"))
      )

      // next to a known chunk: the text is the content, the other chunk rides along raw on a
      // delta and is dropped by a message read (no field for it)
      val mixed = Json.parse(
        """{"content": [{"type": "text", "text": "a"}, {"type": "reference", "reference_ids": [1]}]}"""
      )
      mixed.as[AssistantMessage].content shouldBe "a"
      val mixedDelta = mixed.as[ChunkMessageSpec]
      mixedDelta.content shouldBe Some("a")
      mixedDelta.content_chunks shouldBe Some(
        Seq(Json.obj("type" -> "reference", "reference_ids" -> Json.arr(1)))
      )
    }

    "still write the content as a string" in {
      Json.toJson(AssistantMessage("391")) shouldBe
        Json.obj("content" -> "391")
    }
  }

  "A stream whose deltas are lists of chunks" should {

    "carry the thinking as reasoning and the text as content - to Thinking and Text" in {
      val chunks = resource("large-4-stream.txt").linesIterator.collect {
        case line if line.startsWith("data:") => line.drop(5).trim
      }.filterNot(_ == "[DONE]").map(Json.parse(_).as[ChatCompletionChunkResponse]).toList

      val out =
        Source(chunks).via(ChatChunks.fromOpenAIChunks).runWith(Sink.seq).futureValue

      out.head shouldBe a[Start]
      val thinking = out.collect { case Thinking(text) => text }.mkString
      thinking should include("391")
      out.collect { case Text(text) => text }.mkString shouldBe "391"
      out.collectFirst { case finish: Finish => finish.reason } shouldBe Some(
        FinishReason.stop
      )
    }

    "read Mistral Medium 3.5's citation stream, whose reference chunk is a delta of its own" in {
      // live 2026-10-09: a delta `{"content":[{"type":"reference","reference_ids":[...]}]}`
      // failed the whole stream with the 2026-10-08 reader (error.expected.jsstring)
      val chunks = resource("medium-3.5-citations-stream.txt").linesIterator.collect {
        case line if line.startsWith("data:") => line.drop(5).trim
      }.filterNot(_ == "[DONE]").map(Json.parse(_).as[ChatCompletionChunkResponse]).toList

      val reference = chunks.flatMap(_.choices.flatMap(_.delta.content_chunks)).flatten
      reference.map(r => (r \ "type").as[String]) shouldBe Seq("reference")

      val out =
        Source(chunks).via(ChatChunks.fromOpenAIChunks).runWith(Sink.seq).futureValue
      out.collect { case Text(text) => text }.mkString should include("Nihon Hidankyo")
      out.collect { case Other(kind, _) => kind } shouldBe Seq("content.reference")
      out.collectFirst { case finish: Finish => finish.reason } shouldBe Some(
        FinishReason.stop
      )
    }

    "read a delta with a thinking and a text chunk at once" in {
      val delta = Json
        .parse(
          """{"content": [{"type": "thinking", "thinking": [{"type": "text", "text": " only."}], "closed": true}, {"type": "text", "text": "391"}]}"""
        )
        .as[ChunkMessageSpec]

      delta.content shouldBe Some("391")
      delta.reasoning_content shouldBe Some(" only.")
    }
  }
}
