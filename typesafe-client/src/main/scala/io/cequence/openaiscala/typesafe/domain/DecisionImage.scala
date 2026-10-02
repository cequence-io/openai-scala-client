package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.annotation.tailrec
import scala.util.Try

/**
 * An image for the `state` of a decision model that reads images - Perplexity's Decisions API
 * (`TypeSafeServiceFactory.perplexity`): an OpenAI-style part, `{"type": "image_url",
 * "image_url": {"url": "data:image/png;base64,..."}}`, which may go anywhere in the state -
 * next to the text in an array, as the whole state, nested in an object (live 2026-10-02):
 *
 * {{{
 * decider.systemOne(
 *   Json.arr("Is the device in the photo damaged?", DecisionImage(Files.readAllBytes(photo))),
 *   Map("damaged" -> NoulQuestion("Is the device visibly damaged?"))
 * )
 * }}}
 *
 * The API takes base64 PNG, JPEG and WebP data URLs only - it never fetches a URL (400) - of
 * at most [[MaxTiles]] tiles of 32 x 32 pixels: a larger image is NOT refused, the request
 * times out (504) after about a minute. Both are checked here, and again before a Perplexity
 * service sends a state. Through the OpenAI adapter (`perplexityAsOpenAI`) pass an
 * `ImageURLContent` with a data URL instead (e.g. `VLMContent.of(bytes, "photo.png")`).
 * TypeSafe's Jev and Liquid's d1 read such a part as plain JSON text, so it is no use there.
 */
object DecisionImage {

  /** The most 32 x 32 tiles an image may have - e.g. 1440 x 1440 or 2048 x 1024. */
  val MaxTiles = 2048

  /**
   * The part for an image's bytes - PNG, JPEG or WebP, the type read from the bytes.
   *
   * @throws IllegalArgumentException
   *   for another format, or an image of more than [[MaxTiles]] tiles
   */
  def apply(bytes: Array[Byte]): JsObject = {
    val mediaType = mediaTypeOf(bytes).getOrElse(
      throw new IllegalArgumentException(
        "Not a PNG, JPEG or WebP image - the Decisions API takes only those."
      )
    )
    fromDataUrl(s"data:$mediaType;base64," + Base64.getEncoder.encodeToString(bytes))
  }

  /**
   * The part for a data URL (`data:image/png;base64,...`).
   *
   * @throws IllegalArgumentException
   *   for anything but a base64 PNG, JPEG or WebP data URL, or an image of more than
   *   [[MaxTiles]] tiles
   */
  def fromDataUrl(url: String): JsObject = {
    problem(url).foreach(p =>
      throw new IllegalArgumentException(s"The image cannot go to the Decisions API: $p.")
    )
    part(url)
  }

  private[typesafe] def part(url: String): JsObject =
    Json.obj("type" -> "image_url", "image_url" -> Json.obj("url" -> url))

  private val DataUrlPrefix = "^data:image/(png|jpeg|webp);base64,".r

  // the size sits in the first bytes - PNG and WebP within 30, a JPEG's SOFn usually within the
  // first kilobytes (after EXIF / ICC segments of up to 64 KB each) - so a prefix is decoded
  // first and the whole image only when its size is not in it
  private val HeaderChars = 256 * 1024 // base64 characters, a multiple of 4

  /** Why the Decisions API would not take an image URL, if it would not. */
  private[typesafe] def problem(url: String): Option[String] =
    DataUrlPrefix.findPrefixMatchOf(url) match {
      case Some(prefix) =>
        size(url, prefix.end) match {
          case Left(problem) => Some(problem)
          case Right(Some((width, height))) if tiles(width, height) > MaxTiles =>
            Some(
              s"a $width x $height image - ${tiles(width, height)} tiles of 32 x 32 pixels, at " +
                s"most $MaxTiles fit (e.g. 1440 x 1440 or 2048 x 1024); a larger one times out " +
                "(504) after about a minute, so resize it first"
            )
          case Right(_) => None
        }

      case None if url.startsWith("http://") || url.startsWith("https://") =>
        Some(
          s"an image URL (${abbreviate(url)}) - the Decisions API never fetches one; send a " +
            "base64 PNG, JPEG or WebP data URL"
        )

      case None =>
        Some(s"an image that is not a base64 PNG, JPEG or WebP data URL (${abbreviate(url)})")
    }

  /** The problems of the image parts anywhere in a state, in document order. */
  private[typesafe] def problems(state: JsValue): Seq[String] =
    imageUrls(state).flatMap(problem)

  /**
   * 32 x 32 tiles of an image, its width and height rounded to the nearest multiple of 32 (the
   * rule Perplexity documents: 1600 x 1310 is 50 x 41 = 2,050 tiles).
   */
  private[typesafe] def tiles(
    width: Long,
    height: Long
  ): Long =
    math.round(width / 32.0) * math.round(height / 32.0)

  // the base64 after `start` decoded - its head first - into the size the header gives (if it
  // gives one), or why it does not decode
  private def size(
    url: String,
    start: Int
  ): Either[String, Option[(Long, Long)]] = {
    def decoded(end: Int) = Try(
      Base64.getMimeDecoder.decode(url.substring(start, end))
    ).toOption

    val fromHead =
      if (url.length - start > HeaderChars) decoded(start + HeaderChars).flatMap(dimensions)
      else None

    if (fromHead.isDefined) Right(fromHead)
    else
      decoded(url.length)
        .map(dimensions)
        .toRight("an image data URL whose base64 does not decode")
  }

  private[typesafe] def mediaTypeOf(bytes: Array[Byte]): Option[String] = {
    def u8(i: Int) = bytes(i) & 0xff
    def ascii(
      from: Int,
      length: Int
    ) = new String(bytes, from, length, StandardCharsets.US_ASCII)

    if (bytes.length >= 8 && u8(0) == 0x89 && ascii(1, 3) == "PNG") Some("image/png")
    else if (bytes.length >= 3 && u8(0) == 0xff && u8(1) == 0xd8 && u8(2) == 0xff)
      Some("image/jpeg")
    else if (bytes.length >= 12 && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP")
      Some("image/webp")
    else None
  }

  /**
   * (width, height) from a PNG, JPEG or WebP header, if it is one of those - as `Long`, since
   * a (malformed) PNG may declare a dimension past `Int.MaxValue`.
   */
  private[typesafe] def dimensions(bytes: Array[Byte]): Option[(Long, Long)] = {
    def u8(i: Int) = bytes(i) & 0xff
    def be16(i: Int) = (u8(i) << 8) | u8(i + 1)
    def be32(i: Int) = (be16(i).toLong << 16) | be16(i + 2)
    def le16(i: Int) = u8(i) | (u8(i + 1) << 8)
    def le24(i: Int) = le16(i) | (u8(i + 2) << 16)
    def ascii(
      from: Int,
      length: Int
    ) = new String(bytes, from, length, StandardCharsets.US_ASCII)

    // the SOFn segment of a JPEG holds the size; scan the marker segments up to it
    @tailrec
    def jpeg(at: Int): Option[(Long, Long)] =
      if (at + 9 > bytes.length || u8(at) != 0xff) None
      else
        u8(at + 1) match {
          case 0xff                                              => jpeg(at + 1) // fill byte
          case m if m == 0xd8 || m == 0x01 || (m & 0xf8) == 0xd0 => jpeg(at + 2) // no length
          case m if (m & 0xf0) == 0xc0 && m != 0xc4 && m != 0xc8 && m != 0xcc =>
            Some((be16(at + 7).toLong, be16(at + 5).toLong))
          case _ => jpeg(at + 2 + be16(at + 2))
        }

    Try {
      if (bytes.length >= 24 && u8(0) == 0x89 && ascii(1, 3) == "PNG")
        Some((be32(16), be32(20))) // IHDR, always the first chunk
      else if (bytes.length >= 30 && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP")
        ascii(12, 4) match {
          case "VP8 " => Some(((le16(26) & 0x3fff).toLong, (le16(28) & 0x3fff).toLong))
          case "VP8L" =>
            Some(
              (
                1L + (u8(21) | ((u8(22) & 0x3f) << 8)),
                1L + ((u8(22) >> 6) | (u8(23) << 2) | ((u8(24) & 0x0f) << 10))
              )
            )
          case "VP8X" => Some((1L + le24(24), 1L + le24(27)))
          case _      => None
        }
      else if (bytes.length >= 4 && u8(0) == 0xff && u8(1) == 0xd8) jpeg(2)
      else None
    }.toOption.flatten
  }

  private def imageUrls(json: JsValue): Seq[String] =
    json match {
      case obj: JsObject if (obj \ "type").asOpt[String].contains("image_url") =>
        (obj \ "image_url" \ "url")
          .asOpt[String]
          .orElse((obj \ "image_url").asOpt[String])
          .toSeq
      case obj: JsObject => obj.values.toSeq.flatMap(imageUrls)
      case arr: JsArray  => arr.value.toSeq.flatMap(imageUrls)
      case _             => Nil
    }

  private def abbreviate(url: String) =
    if (url.length <= 60) url else url.take(57) + "..."
}
