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
  ) =
    Base64.getEncoder.encodeToString(Files.readAllBytes(file.toPath))

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
