package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.domain.{
  FileContent,
  ImageURLContent,
  TextContent,
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

      val Seq(content) = toNonSystemVertexAI(Seq(msg))
      content.getRole shouldBe "USER"
      content.getPartsCount shouldBe 1

      val part = content.getParts(0)
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

      val Seq(content) = toNonSystemVertexAI(Seq(msg))
      content.getPartsCount shouldBe 2

      content.getParts(0).hasText shouldBe true
      content.getParts(0).getText shouldBe "Summarize"

      val filePart = content.getParts(1)
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
  }
}
