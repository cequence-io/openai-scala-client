package io.cequence.openaiscala.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class VLMContentSpec extends AnyWordSpecLike with Matchers {

  private val base64 = "QUJD" // "ABC"

  private def imageOf(fileName: String): ImageURLContent =
    VLMContent.of(base64, fileName) match {
      case Seq(TextContent(_), img: ImageURLContent) => img
      case other => fail(s"Expected a text label + ImageURLContent, got: $other")
    }

  "VLMContent" should {

    "emit a labeled text block plus the content block" in {
      VLMContent.of(base64, "pic.png") match {
        case Seq(TextContent(label), _: ImageURLContent) =>
          label shouldBe "[file: pic.png]"
        case other => fail(s"Unexpected shape: $other")
      }
    }

    "include the index in the label when fileIndex is given" in {
      VLMContent.of(base64, "pic.png", Some(2)) match {
        case Seq(TextContent(label), _) => label shouldBe "[file #2: pic.png]"
        case other                      => fail(s"Unexpected shape: $other")
      }
    }

    "map each supported image extension to the right data-url MIME type" in {
      val expected = Map(
        "img.jpg" -> "image/jpeg",
        "img.jpeg" -> "image/jpeg",
        "IMG.JPG" -> "image/jpeg", // case-insensitive
        "img.png" -> "image/png",
        "img.webp" -> "image/webp",
        "img.gif" -> "image/gif",
        "img.bmp" -> "image/bmp",
        "img.tif" -> "image/tiff",
        "img.tiff" -> "image/tiff"
      )

      expected.foreach { case (fileName, mime) =>
        imageOf(fileName).url shouldBe s"data:$mime;base64,$base64"
      }
    }

    "wrap PDFs as FileContent (not ImageURLContent) with the filename preserved" in {
      VLMContent.of(base64, "doc.pdf") match {
        case Seq(TextContent(_), FileContent(_, Some(fileData), Some(filename))) =>
          fileData shouldBe s"data:application/pdf;base64,$base64"
          filename shouldBe "doc.pdf"
        case other => fail(s"Unexpected shape: $other")
      }
    }

    "accept raw bytes and base64-encode them" in {
      VLMContent.of("ABC".getBytes("UTF-8"), "pic.bmp") match {
        case Seq(TextContent(_), img: ImageURLContent) =>
          img.url shouldBe s"data:image/bmp;base64,$base64"
        case other => fail(s"Unexpected shape: $other")
      }
    }

    "reject an unsupported extension" in {
      an[IllegalArgumentException] should be thrownBy VLMContent.of(base64, "clip.mp4")
    }
  }
}
