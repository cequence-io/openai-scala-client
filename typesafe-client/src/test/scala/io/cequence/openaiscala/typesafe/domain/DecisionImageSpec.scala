package io.cequence.openaiscala.typesafe.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.{ByteBuffer, ByteOrder}
import java.util.Base64
import javax.imageio.ImageIO

/**
 * The image parts for Perplexity's Decisions API: the public builders, the size read from a
 * PNG / JPEG / WebP header, Perplexity's tile rule, and the data URL rules checked before
 * sending.
 */
class DecisionImageSpec extends AnyWordSpec with Matchers {

  import DecisionImage._

  // a real file, as Java writes it (a JPEG starts with a JFIF segment the scan must skip)
  private def encoded(
    width: Int,
    height: Int,
    format: String
  ): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out)
    out.toByteArray
  }

  // headers only, for sizes too big to encode in a test - the size is all that is read
  private def pngHeader(
    width: Int,
    height: Int
  ): Array[Byte] =
    Array(0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte) ++
      ByteBuffer
        .allocate(16)
        .putInt(13)
        .put("IHDR".getBytes(US_ASCII))
        .putInt(width)
        .putInt(height)
        .array()

  // SOI, an EXIF segment, then a progressive SOF2 (precision, height, width)
  private def jpegHeader(
    width: Int,
    height: Int
  ): Array[Byte] =
    Array(0xff, 0xd8, 0xff, 0xe1, 0x00, 0x08).map(_.toByte) ++ "Exif\u0000\u0000".getBytes(
      US_ASCII
    ) ++
      Array(0xff, 0xc2, 0x00, 0x11, 0x08).map(_.toByte) ++
      ByteBuffer.allocate(4).putShort(height.toShort).putShort(width.toShort).array() ++
      new Array[Byte](12)

  // SOI, five 64 KB APP1 segments (~320 KB, past the decoded head), then the SOF0 with the size
  private def jpegWithLargeSegments(
    width: Int,
    height: Int
  ): Array[Byte] =
    Array(0xff, 0xd8).map(_.toByte) ++
      (1 to 5).flatMap(_ =>
        Array(0xff, 0xe1, 0xff, 0xff).map(_.toByte) ++ new Array[Byte](65533)
      ) ++
      Array(0xff, 0xc0, 0x00, 0x11, 0x08).map(_.toByte) ++
      ByteBuffer.allocate(4).putShort(height.toShort).putShort(width.toShort).array() ++
      new Array[Byte](12)

  private def le(
    value: Int,
    bytes: Int
  ): Array[Byte] =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array().take(bytes)

  private def webp(
    chunk: String,
    payload: Array[Byte]
  ): Array[Byte] = {
    val padded = payload ++ new Array[Byte](8)
    "RIFF".getBytes(US_ASCII) ++ le(12 + padded.length, 4) ++ "WEBP".getBytes(US_ASCII) ++
      chunk.getBytes(US_ASCII) ++ le(padded.length, 4) ++ padded
  }

  // lossy: frame tag, start code, then the 14-bit width and height
  private def vp8(
    width: Int,
    height: Int
  ) = webp(
    "VP8 ",
    Array(0, 0, 0, 0x9d, 0x01, 0x2a).map(_.toByte) ++ le(width, 2) ++ le(height, 2)
  )

  // lossless: signature, then width - 1 and height - 1 in 14 bits each
  private def vp8l(
    width: Int,
    height: Int
  ) = webp("VP8L", Array(0x2f.toByte) ++ le((width - 1) | ((height - 1) << 14), 4))

  // extended: flags and reserved bytes, then the canvas width - 1 and height - 1 in 24 bits each
  private def vp8x(
    width: Int,
    height: Int
  ) = webp("VP8X", new Array[Byte](4) ++ le(width - 1, 3) ++ le(height - 1, 3))

  private def dataUrl(
    mediaType: String,
    bytes: Array[Byte]
  ) = s"data:$mediaType;base64," + Base64.getEncoder.encodeToString(bytes)

  "dimensions" should {

    "read a PNG and a JPEG as Java writes them" in {
      dimensions(encoded(64, 48, "png")) shouldBe Some((64L, 48L))
      dimensions(encoded(100, 80, "jpg")) shouldBe Some((100L, 80L))
    }

    "read a PNG, a progressive JPEG past an EXIF segment, and the three WebP kinds" in {
      dimensions(pngHeader(4032, 3024)) shouldBe Some((4032L, 3024L))
      dimensions(jpegHeader(4032, 3024)) shouldBe Some((4032L, 3024L))
      dimensions(vp8(1600, 1310)) shouldBe Some((1600L, 1310L))
      dimensions(vp8l(16383, 2)) shouldBe Some((16383L, 2L))
      dimensions(vp8x(2048, 1024)) shouldBe Some((2048L, 1024L))
    }

    "know nothing about other or truncated data" in {
      dimensions("GIF89a, not one of the three".getBytes(US_ASCII)) shouldBe None
      dimensions(Array(0xff, 0xd8).map(_.toByte)) shouldBe None
      dimensions(pngHeader(10, 10).take(20)) shouldBe None
      dimensions(Array.emptyByteArray) shouldBe None
    }
  }

  "DecisionImage(bytes) / fromDataUrl" should {

    "build the part, the media type read from the bytes" in {
      val png = encoded(16, 16, "png")
      DecisionImage(png) shouldBe Json.obj(
        "type" -> "image_url",
        "image_url" -> Json.obj("url" -> dataUrl("image/png", png))
      )
      (DecisionImage(encoded(16, 16, "jpg")) \ "image_url" \ "url")
        .as[String] should startWith(
        "data:image/jpeg;base64,"
      )
      (DecisionImage(vp8x(64, 64)) \ "image_url" \ "url").as[String] should startWith(
        "data:image/webp;base64,"
      )
      DecisionImage.fromDataUrl(dataUrl("image/png", png)) shouldBe DecisionImage(png)
    }

    "refuse another format, an image over 2,048 tiles and a URL, up front" in {
      the[IllegalArgumentException]
        .thrownBy(DecisionImage("GIF89a, a gif".getBytes(US_ASCII)))
        .getMessage should include("Not a PNG, JPEG or WebP image")
      the[IllegalArgumentException]
        .thrownBy(DecisionImage(pngHeader(4032, 3024)))
        .getMessage should include("4032 x 3024")
      the[IllegalArgumentException]
        .thrownBy(DecisionImage.fromDataUrl("https://example.com/cat.png"))
        .getMessage should include("never fetches")
    }
  }

  "tiles" should {

    "round width and height to the nearest 32, as Perplexity's examples do" in {
      tiles(1440, 1440) shouldBe 2025 // fits
      tiles(2048, 1024) shouldBe 2048 // fits
      tiles(1600, 1310) shouldBe 2050 // does not
      tiles(4032, 3024) shouldBe 11970 // a phone photo: 126 x 95
    }
  }

  "problem" should {

    "pass an image that fits" in {
      problem(dataUrl("image/png", encoded(64, 48, "png"))) shouldBe None
      problem(dataUrl("image/jpeg", jpegHeader(1440, 1440))) shouldBe None
      problem(dataUrl("image/webp", vp8x(2048, 1024))) shouldBe None
    }

    "refuse an image over 2,048 tiles, which the API would let time out after a minute" in {
      val photo = problem(dataUrl("image/jpeg", jpegHeader(4032, 3024)))
      photo.get should (include("4032 x 3024") and include("11970 tiles") and include("504"))
      problem(dataUrl("image/png", pngHeader(1600, 1310))) shouldBe defined
    }

    "refuse a URL the API would have to fetch, and other image types" in {
      problem("https://example.com/cat.png").get should include("never fetches")
      problem(dataUrl("image/gif", "GIF89a".getBytes(US_ASCII))).get should include(
        "not a base64 PNG, JPEG or WebP data URL"
      )
    }

    "refuse a PNG declaring a size past Int.MaxValue rather than wrap it negative" in {
      dimensions(pngHeader(Int.MinValue, 1000)) shouldBe Some((2147483648L, 1000L))
      problem(dataUrl("image/png", pngHeader(Int.MinValue, 1000))) shouldBe defined
    }

    "find a JPEG's size past the decoded head - and decode only the head when it is there" in {
      val photo = jpegWithLargeSegments(4032, 3024)
      photo.length should be > 300000
      problem(dataUrl("image/jpeg", photo)).get should include("4032 x 3024")
      problem(dataUrl("image/jpeg", jpegWithLargeSegments(1440, 1440))) shouldBe None

      // a big PNG: its size is in the first bytes
      val bigPng = pngHeader(4032, 3024) ++ new Array[Byte](2 * 1024 * 1024)
      problem(dataUrl("image/png", bigPng)).get should include("4032 x 3024")
    }

    "leave an image of unreadable size to the API" in {
      problem("data:image/png;base64,AAAA") shouldBe None
    }
  }

  "problems" should {

    "find the image parts anywhere in a state, in document order" in {
      val state = Json.obj(
        "instructions" -> "Judge the photos.",
        "message" -> Json.arr("Look:", part(dataUrl("image/png", pngHeader(4032, 3024)))),
        "attachments" -> Json.obj("first" -> part("https://example.com/a.png"))
      )

      problems(state).map(_.take(12)) shouldBe Seq("a 4032 x 302", "an image URL")
      problems(JsString("text only")) shouldBe empty
      problems(part(dataUrl("image/png", encoded(8, 8, "png")))) shouldBe empty
    }
  }
}
