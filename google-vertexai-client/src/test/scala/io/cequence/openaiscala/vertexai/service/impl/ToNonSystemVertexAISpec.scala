package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  FileContent,
  FunctionCallSpec,
  ImageURLContent,
  TextContent,
  ToolCallSpec,
  ToolMessage,
  UserMessage,
  UserSeqMessage
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.{util => ju}
import java.nio.charset.StandardCharsets

class ToNonSystemVertexAISpec extends AnyWordSpec with Matchers {

  private def b64(bytes: Array[Byte]): String =
    ju.Base64.getEncoder.encodeToString(bytes)

  private val pngBytes: Array[Byte] = Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10)
  private val pdfBytes: Array[Byte] = "%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII)

  "toNonSystemVertexAI" should {

    "convert a data: image URL to an inline_data Blob (not file_data)" in {
      val dataUrl = s"data:image/png;base64,${b64(pngBytes)}"
      val msg = UserSeqMessage(Seq(ImageURLContent(dataUrl)))

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      vertexContent.getRole shouldBe "USER"
      vertexContent.getPartsCount shouldBe 1

      val part = vertexContent.getParts(0)
      part.hasInlineData shouldBe true
      part.hasFileData shouldBe false

      val blob = part.getInlineData
      blob.getMimeType shouldBe "image/png"
      blob.getData.toByteArray shouldBe pngBytes
    }

    "convert a FileContent with data:application/pdf fileData to inline_data" in {
      val dataUrl = s"data:application/pdf;base64,${b64(pdfBytes)}"
      val msg = UserSeqMessage(
        Seq(
          TextContent("Summarize"),
          FileContent(fileData = Some(dataUrl), filename = Some("doc.pdf"))
        )
      )

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      vertexContent.getPartsCount shouldBe 2

      vertexContent.getParts(0).hasText shouldBe true
      vertexContent.getParts(0).getText shouldBe "Summarize"

      val filePart = vertexContent.getParts(1)
      filePart.hasInlineData shouldBe true
      filePart.getInlineData.getMimeType shouldBe "application/pdf"
      filePart.getInlineData.getData.toByteArray shouldBe pdfBytes
    }

    "reject ImageURLContent with non-base64 data URL" in {
      val dataUrl = "data:image/png;utf8,hello"
      val msg = UserSeqMessage(Seq(ImageURLContent(dataUrl)))
      assertThrows[IllegalArgumentException](toNonSystemVertexAI(Seq(msg)))
    }

    "reject ImageURLContent with non-data URL" in {
      val msg = UserSeqMessage(Seq(ImageURLContent("https://example.com/image.png")))
      assertThrows[IllegalArgumentException](toNonSystemVertexAI(Seq(msg)))
    }

    "reject FileContent with only fileId (OpenAI file_id not portable)" in {
      val msg = UserSeqMessage(Seq(FileContent(fileId = Some("file-abc"))))
      assertThrows[IllegalArgumentException](toNonSystemVertexAI(Seq(msg)))
    }

    "reject FileContent with no fileData and no fileId" in {
      val msg = UserSeqMessage(Seq(FileContent()))
      assertThrows[IllegalArgumentException](toNonSystemVertexAI(Seq(msg)))
    }

    "convert an AssistantToolMessage with text + one function call to a MODEL content" in {
      val msg = AssistantToolMessage(
        content = Some("thinking..."),
        tool_calls = Seq(
          "call_1" -> (FunctionCallSpec(
            "get_weather",
            """{"city":"Oslo"}"""
          ): ToolCallSpec)
        )
      )

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      vertexContent.getRole shouldBe "MODEL"
      vertexContent.getPartsCount shouldBe 2

      vertexContent.getParts(0).hasText shouldBe true
      vertexContent.getParts(0).getText shouldBe "thinking..."

      val fcPart = vertexContent.getParts(1)
      fcPart.hasFunctionCall shouldBe true
      val fc = fcPart.getFunctionCall
      fc.getName shouldBe "get_weather"
      fc.getArgs.getFieldsMap.get("city").getStringValue shouldBe "Oslo"
    }

    "convert an AssistantToolMessage with no content and two function calls to parts-only MODEL content" in {
      val msg = AssistantToolMessage(
        content = None,
        tool_calls = Seq(
          "call_1" -> (FunctionCallSpec("get_weather", """{"city":"Oslo"}"""): ToolCallSpec),
          "call_2" -> (FunctionCallSpec("get_time", """{"tz":"UTC"}"""): ToolCallSpec)
        )
      )

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      vertexContent.getRole shouldBe "MODEL"
      vertexContent.getPartsCount shouldBe 2
      vertexContent.getParts(0).hasFunctionCall shouldBe true
      vertexContent.getParts(1).hasFunctionCall shouldBe true
      vertexContent.getParts(0).getFunctionCall.getName shouldBe "get_weather"
      vertexContent.getParts(1).getFunctionCall.getName shouldBe "get_time"
    }

    "convert a ToolMessage with JSON object content to a USER content with a function_response part" in {
      val msg = ToolMessage(Some("""{"temp":7}"""), "call_1", "get_weather")

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      vertexContent.getRole shouldBe "USER"
      vertexContent.getPartsCount shouldBe 1

      val part = vertexContent.getParts(0)
      part.hasFunctionResponse shouldBe true
      val fr = part.getFunctionResponse
      fr.getName shouldBe "get_weather"
      fr.getResponse.getFieldsMap.get("temp").getNumberValue shouldBe 7.0
    }

    "convert a ToolMessage with plain-text content into a 'result' field response" in {
      val msg = ToolMessage(Some("sunny"), "call_1", "get_weather")

      val Seq(vertexContent) = toNonSystemVertexAI(Seq(msg))
      val fr = vertexContent.getParts(0).getFunctionResponse
      fr.getResponse.getFieldsMap.get("result").getStringValue shouldBe "sunny"
    }

    "merge two consecutive ToolMessages into one USER content, keeping a following UserMessage separate" in {
      val messages = Seq(
        ToolMessage(Some("""{"temp":7}"""), "call_1", "get_weather"),
        ToolMessage(Some("""{"tz":"UTC"}"""), "call_2", "get_time"),
        UserMessage("thanks")
      )

      val contents = toNonSystemVertexAI(messages)
      contents.size shouldBe 2

      val merged = contents.head
      merged.getRole shouldBe "USER"
      merged.getPartsCount shouldBe 2
      merged.getParts(0).getFunctionResponse.getName shouldBe "get_weather"
      merged.getParts(1).getFunctionResponse.getName shouldBe "get_time"

      val userContent = contents(1)
      userContent.getRole shouldBe "USER"
      userContent.getPartsCount shouldBe 1
      userContent.getParts(0).hasText shouldBe true
      userContent.getParts(0).getText shouldBe "thanks"
    }

    "produce a full round-trip ordering of USER, MODEL, USER, MODEL contents" in {
      val messages = Seq(
        UserMessage("What's the weather in Oslo?"),
        AssistantToolMessage(
          content = None,
          tool_calls = Seq(
            "call_1" -> (FunctionCallSpec("get_weather", """{"city":"Oslo"}"""): ToolCallSpec)
          )
        ),
        ToolMessage(Some("""{"temp":7}"""), "call_1", "get_weather"),
        AssistantMessage("It's 7 degrees in Oslo.")
      )

      val contents = toNonSystemVertexAI(messages)
      contents.map(_.getRole) shouldBe Seq("USER", "MODEL", "USER", "MODEL")
    }
  }
}
