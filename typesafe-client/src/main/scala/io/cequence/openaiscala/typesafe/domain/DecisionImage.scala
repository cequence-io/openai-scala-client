package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.annotation.tailrec
import scala.util.Try

/**
 * An image for the `state` of a decision model that reads images - Perplexity's decider,
 * OpenAI's `gpt-6-luna`, Liquid AI's `d1`, a local llama.cpp with a vision model: an
 * OpenAI-style part, `{"type": "image_url", "image_url": {"url":
 * "data:image/png;base64,..."}}`, which may go anywhere in the state - next to the text in an
 * array, as the whole state, nested in an object:
 *
 * {{{
 * decider.systemOne(
 *   Json.arr("Is the device in the photo damaged?", DecisionImage(Files.readAllBytes(photo))),
 *   Map("damaged" -> NoulQuestion("Is the device visibly damaged?"))
 * )
 * }}}
 *
 * The provider says how its host takes them (`DecisionProvider.images`): where they are, or
 * lifted into a top-level `images` array with an `[image n]` marker left in their place.
 *
 * The hosts take base64 PNG, JPEG and WebP data URLs only - they never fetch a URL; Liquid and
 * Perplexity refuse a GIF too (live 2026-10-07). The format is checked here, and again before
 * a service sends a state; a size cap only where a provider sets one (`maxImageTiles` - none
 * of the presets does since 2026-10-06: Perplexity's decider, which used to time out over
 * 2,048 tiles of 32 x 32 pixels, now scales any image, Liquid refuses too large ones with a
 * quick 422, OpenAI scales them). Through the OpenAI adapter pass an `ImageURLContent` with a
 * data URL instead (e.g. `VLMContent.of(bytes, "photo.png")`). TypeSafe's Jev and Liquid's
 * `d1:free` take no images (Jev reads such a part as plain JSON text).
 */
object DecisionImage {

  /**
   * The part for an image's bytes - PNG, JPEG or WebP, the type read from the bytes.
   *
   * @throws IllegalArgumentException
   *   for another format
   */
  def apply(bytes: Array[Byte]): JsObject = {
    val mediaType = mediaTypeOf(bytes).getOrElse(
      throw new IllegalArgumentException(
        "Not a PNG, JPEG or WebP image - the decision-model hosts take only those."
      )
    )
    fromDataUrl(s"data:$mediaType;base64," + Base64.getEncoder.encodeToString(bytes))
  }

  /**
   * The part for a data URL (`data:image/png;base64,...`).
   *
   * @throws IllegalArgumentException
   *   for anything but a base64 PNG, JPEG or WebP data URL
   */
  def fromDataUrl(url: String): JsObject = {
    problem(url).foreach(p =>
      throw new IllegalArgumentException(s"The image cannot go to a decision model: $p.")
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

  /**
   * Why a decision-model host would not take an image URL, if it would not: anything but a
   * base64 PNG / JPEG / WebP data URL and, for a host with a tile cap only, an image over it
   * (the size read from the image header, so only then is the base64 decoded - a malformed one
   * is otherwise left to the host's 400).
   *
   * @param maxTiles
   *   the largest image the host takes, in tiles - no cap by default
   */
  private[typesafe] def problem(
    url: String,
    maxTiles: Option[Int] = None
  ): Option[String] =
    DataUrlPrefix.findPrefixMatchOf(url) match {
      case Some(prefix) =>
        maxTiles.flatMap { max =>
          size(url, prefix.end) match {
            case Left(problem) => Some(problem)
            case Right(Some((width, height))) if tiles(width, height) > max =>
              Some(
                s"a $width x $height image - ${tiles(width, height)} tiles of 32 x 32 pixels, " +
                  s"at most $max fit on this host, so resize it first"
              )
            case Right(_) => None
          }
        }

      case None if url.startsWith("http://") || url.startsWith("https://") =>
        Some(
          s"an image URL (${abbreviate(url)}) - a decision-model host never fetches one; send " +
            "a base64 PNG, JPEG or WebP data URL"
        )

      case None =>
        Some(s"an image that is not a base64 PNG, JPEG or WebP data URL (${abbreviate(url)})")
    }

  /** The problems of the image parts anywhere in a state, in document order. */
  private[typesafe] def problems(
    state: JsValue,
    maxTiles: Option[Int]
  ): Seq[String] =
    imageUrls(state).flatMap(problem(_, maxTiles))

  /**
   * The state of one message of texts (`Left`) and image URLs (`Right`): its text alone (the
   * texts joined with a newline) when it has no images, else an array of its parts in order -
   * consecutive texts joined, each image an `image_url` part.
   *
   * @param text
   *   how a text goes into the state
   */
  private[typesafe] def messageState(
    parts: Seq[Either[String, String]],
    text: String => JsValue = JsString(_)
  ): JsValue =
    if (parts.forall(_.isLeft)) text(parts.collect { case Left(t) => t }.mkString("\n"))
    else
      JsArray(
        parts
          .foldLeft(Vector.empty[Either[String, String]]) {
            case (done :+ Left(previous), Left(more)) => done :+ Left(previous + "\n" + more)
            case (done, part)                         => done :+ part
          }
          .map {
            case Left(t)    => text(t)
            case Right(url) => part(url)
          }
      )

  /** The image URLs of the image parts anywhere in a state, in document order. */
  private[typesafe] def imageUrls(json: JsValue): Seq[String] = {
    // a walk that only collects (the state is not rebuilt, as `lift` does)
    val found = Vector.newBuilder[String]
    def go(json: JsValue): Unit =
      json match {
        case obj: JsObject =>
          url(obj) match {
            case Some(imageUrl) => found += imageUrl
            case None           => obj.fields.foreach { case (_, value) => go(value) }
          }
        case JsArray(values) => values.foreach(go)
        case _               => ()
      }
    go(json)
    found.result()
  }

  /**
   * The state with each image part replaced by an `[image n]` marker (numbered from 1 in
   * document order), and the parts' URLs in that order.
   */
  private[typesafe] def lift(state: JsValue): (JsValue, Seq[String]) = {
    // a value lifted after the URLs found before it
    def go(
      json: JsValue,
      urls: Vector[String]
    ): (JsValue, Vector[String]) =
      json match {
        case obj: JsObject if url(obj).isDefined =>
          (JsString(s"[image ${urls.size + 1}]"), urls ++ url(obj))

        case JsObject(fields) =>
          val (lifted, found) = fields.foldLeft((Vector.empty[(String, JsValue)], urls)) {
            case ((done, found), (key, value)) =>
              val (liftedValue, foundAfter) = go(value, found)
              (done :+ (key -> liftedValue), foundAfter)
          }
          (JsObject(lifted), found)

        case JsArray(values) =>
          val (lifted, found) = values.foldLeft((Vector.empty[JsValue], urls)) {
            case ((done, found), value) =>
              val (liftedValue, foundAfter) = go(value, found)
              (done :+ liftedValue, foundAfter)
          }
          (JsArray(lifted), found)

        case other =>
          (other, urls)
      }

    go(state, Vector.empty)
  }

  // the URL of an image part - `image_url.url`, or `image_url` as a string
  private[typesafe] def url(obj: JsObject): Option[String] =
    if ((obj \ "type").asOpt[String].contains("image_url"))
      (obj \ "image_url" \ "url").asOpt[String].orElse((obj \ "image_url").asOpt[String])
    else None

  /**
   * 32 x 32 tiles of an image, its width and height rounded to the nearest multiple of 32 (the
   * rule Perplexity documented for its former cap: 1600 x 1310 is 50 x 41 = 2,050 tiles) - the
   * unit of a provider's `maxImageTiles`.
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

  private def abbreviate(url: String) =
    if (url.length <= 60) url else url.take(57) + "..."
}
