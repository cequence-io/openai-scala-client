package io.cequence.openaiscala

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ReasoningBlock,
  ReasoningMessage
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * A chat completion's reasoning read into its choice as a provider-neutral
 * [[ReasoningMessage]]
 *   - from the message's `reasoning_content` / `reasoning` field (and Mistral's thinking
 *     chunks)
 *   - and a ReasoningMessage's own JSON for persisting a history.
 */
class ReasoningMessageJsonSpec extends AnyWordSpec with Matchers {

  private def chatResponse(message: String) =
    s"""{"id": "c1", "object": "chat.completion", "created": 1700000000, "model": "m",
       |"choices": [{"index": 0, "message": $message, "finish_reason": "stop"}]}""".stripMargin

  private def resource(name: String): String = {
    val source = scala.io.Source.fromResource(s"mistral/$name")
    try source.mkString
    finally source.close()
  }

  "A chat completion choice" should {

    "read the message's reasoning_content as reasoning, in that field" in {
      val response = Json
        .parse(
          chatResponse(
            """{"role": "assistant", "content": "Oslo", "reasoning_content": "Closer to Oslo."}"""
          )
        )
        .as[ChatCompletionResponse]

      response.reasoningMessage shouldBe Some(
        ReasoningMessage(
          Seq(
            ReasoningBlock(text = Some("Closer to Oslo."), field = Some("reasoning_content"))
          )
        )
      )
      response.historyMessages shouldBe Seq(
        ReasoningMessage(
          Seq(
            ReasoningBlock(text = Some("Closer to Oslo."), field = Some("reasoning_content"))
          )
        ),
        AssistantMessage("Oslo")
      )
    }

    "read the message's reasoning (Cerebras, Together, Groq) - and a tool reply's" in {
      val response = Json
        .parse(
          chatResponse(
            """{"role": "assistant", "content": null, "reasoning": "Need the weather.",
              |"tool_calls": [{"id": "call_1", "type": "function",
              |"function": {"name": "get_weather", "arguments": "{}"}}]}""".stripMargin
          )
        )
        .as[ChatToolCompletionResponse]

      response.reasoningMessage.map(_.blocks) shouldBe Some(
        Seq(ReasoningBlock(text = Some("Need the weather."), field = Some("reasoning")))
      )
      response.historyMessages shouldBe Seq(
        response.reasoningMessage.get,
        response.assistantToolMessage
      )
    }

    "read no reasoning from a message without any (or a blank one)" in {
      Json
        .parse(chatResponse("""{"role": "assistant", "content": "hi"}"""))
        .as[ChatCompletionResponse]
        .reasoningMessage shouldBe None
      Json
        .parse(
          chatResponse("""{"role": "assistant", "content": "hi", "reasoning_content": " "}""")
        )
        .as[ChatCompletionResponse]
        .reasoningMessage shouldBe None
    }

    "read Mistral's thinking chunks as reasoning - to go back as a thinking chunk" in {
      val response = Json.parse(resource("large-4-sync.json")).as[ChatCompletionResponse]

      response.contentHead shouldBe "391"
      response.reasoningMessage shouldBe Some(
        ReasoningMessage(
          Seq(
            ReasoningBlock(
              text = Some("We need answer number only. 17*23 = 391. Need final only."),
              field = Some(ReasoningBlock.ThinkingChunk)
            )
          )
        )
      )
    }

    "write its reasoning and read it back" in {
      val response = Json
        .parse(
          chatResponse(
            """{"role": "assistant", "content": "Oslo", "reasoning": "Closer to Oslo."}"""
          )
        )
        .as[ChatCompletionResponse]

      Json.toJson(response).as[ChatCompletionResponse] shouldBe response
    }
  }

  "A ReasoningMessage" should {

    val reasoning = ReasoningMessage(
      Seq(
        ReasoningBlock(
          summary = Seq("Checking."),
          encrypted = Some("enc-1"),
          id = Some("rs_1")
        ),
        ReasoningBlock(text = Some("Hmm."), signature = Some("sig-1")),
        ReasoningBlock(encrypted = Some("opaque")),
        ReasoningBlock(text = Some("Plain."), field = Some("reasoning"))
      )
    )

    "survive a JSON round trip, every block kind" in {
      Json
        .toJson(reasoning: BaseMessage)(messageWrites)
        .as[BaseMessage](messageReads) shouldBe reasoning
    }

    "give its readable text - opaque blocks left out" in {
      reasoning.text shouldBe Some("Checking.\n\nHmm.\n\nPlain.")
      ReasoningMessage(Seq(ReasoningBlock(encrypted = Some("x")))).text shouldBe None
    }
  }
}
