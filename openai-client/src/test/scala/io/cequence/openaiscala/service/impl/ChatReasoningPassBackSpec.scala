package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.{
  AssistantMessage,
  ReasoningBlock,
  ReasoningMessage,
  UserMessage
}
import io.cequence.openaiscala.service.ProducedReasoning
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * Carried reasoning on the chat completions API: the reasoning texts a service's site returned
 * go back on the assistant message after them, each in the form it came in (live 2026-10-26:
 * DeepSeek / Fireworks read `reasoning_content`, Mistral its thinking chunks, Cerebras takes
 * `reasoning` but 400s `reasoning_content`).
 */
class ChatReasoningPassBackSpec extends AnyWordSpec with Matchers {

  private val text = ReasoningBlock(text = Some("Closer to Oslo."))
  private val chunk =
    ReasoningBlock(text = Some("Closer to Oslo."), field = Some(ReasoningBlock.ThinkingChunk))

  private def produced(blocks: ReasoningBlock*) = {
    val registry = new ProducedReasoning()
    blocks.foreach(registry.remember)
    registry
  }

  "withCarriedReasoning" should {

    "pair a carried text with the assistant message after it, if this site returned it" in {
      val messages = Seq(UserMessage("Q"), ReasoningMessage(Seq(text)), AssistantMessage("A"))

      ChatCompletionBodyMaker.withCarriedReasoning(
        messages,
        Some(produced(text))
      ) shouldBe Seq(
        UserMessage("Q") -> Nil,
        AssistantMessage("A") -> Seq(text)
      )
      ChatCompletionBodyMaker.withCarriedReasoning(messages, Some(produced())) shouldBe Seq(
        UserMessage("Q") -> Nil,
        AssistantMessage("A") -> Nil
      )
      ChatCompletionBodyMaker.withCarriedReasoning(messages, None).map(_._2) shouldBe Seq(
        Nil,
        Nil
      )
    }

    "leave out reasoning not followed by an assistant message, and other block kinds" in {
      val item = ReasoningBlock(id = Some("rs_1"), encrypted = Some("e"))
      val registry = produced(text, item)

      ChatCompletionBodyMaker.withCarriedReasoning(
        Seq(ReasoningMessage(Seq(text)), UserMessage("Q")),
        Some(registry)
      ) shouldBe Seq(UserMessage("Q") -> Nil)

      ChatCompletionBodyMaker.withCarriedReasoning(
        Seq(ReasoningMessage(Seq(item)), AssistantMessage("A")),
        Some(registry)
      ) shouldBe Seq(AssistantMessage("A") -> Nil)
    }
  }

  "withReasoning" should {

    "put a text back in its field" in {
      ChatCompletionBodyMaker.withReasoning(
        Json.obj("role" -> "assistant", "content" -> "A"),
        Seq(text, ReasoningBlock(text = Some("Or Madrid."), field = Some("reasoning")))
      ) shouldBe Json.obj(
        "role" -> "assistant",
        "content" -> "A",
        "reasoning_content" -> "Closer to Oslo.",
        "reasoning" -> "Or Madrid."
      )
    }

    "put Mistral's back as a thinking chunk opening the content" in {
      ChatCompletionBodyMaker.withReasoning(
        Json.obj("role" -> "assistant", "content" -> "A"),
        Seq(chunk)
      ) shouldBe Json.obj(
        "role" -> "assistant",
        "content" -> Json.arr(
          Json.obj(
            "type" -> "thinking",
            "thinking" -> Json.arr(Json.obj("type" -> "text", "text" -> "Closer to Oslo."))
          ),
          Json.obj("type" -> "text", "text" -> "A")
        )
      )

      // a tool call turn without text
      (ChatCompletionBodyMaker.withReasoning(
        Json.obj("role" -> "assistant", "tool_calls" -> Json.arr()),
        Seq(chunk)
      ) \ "content")
        .as[Seq[play.api.libs.json.JsObject]]
        .map(c => (c \ "type").as[String]) shouldBe Seq(
        "thinking"
      )
    }
  }
}
