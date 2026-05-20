package io.cequence.openaiscala.examples

import java.awt.image.RenderedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO

trait BufferedImageHelper {

  protected def imageBase64Source(
    file: java.io.File
  ): String = {
    val bufferedImage = ImageIO.read(file)
    Base64.getEncoder.encodeToString(imageToBytes(bufferedImage, "jpeg"))
  }

  protected def pdfBase64Source(
    file: java.io.File
  ): String =
    Base64.getEncoder.encodeToString(Files.readAllBytes(file.toPath))

  /**
   * Reads `file` and returns a base64 `data:` URL with the mime type inferred from the file
   * extension (pdf, jpg/jpeg, png, gif, webp). Use this when passing files as `FileContent`.
   */
  protected def fileBase64DataUrl(
    file: java.io.File
  ): String = {
    val mime = file.getName.toLowerCase match {
      case n if n.endsWith(".pdf")                        => "application/pdf"
      case n if n.endsWith(".jpg") || n.endsWith(".jpeg") => "image/jpeg"
      case n if n.endsWith(".png")                        => "image/png"
      case n if n.endsWith(".gif")                        => "image/gif"
      case n if n.endsWith(".webp")                       => "image/webp"
      case _                                              => "application/octet-stream"
    }
    val data = Base64.getEncoder.encodeToString(Files.readAllBytes(file.toPath))
    s"data:$mime;base64,$data"
  }

  protected def imageToBytes(
    image: RenderedImage,
    format: String
  ): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    ImageIO.write(image, format, baos)
    baos.flush()
    val imageInByte = baos.toByteArray
    baos.close()
    imageInByte
  }
}
