import sbt._
import sbt.Keys._

/**
 * The javadoc jars Maven Central requires for every artifact, kept small (Sonatype's monthly
 * publishing limits count every byte, and the Scaladoc jars were ~75% of a release): Scaladoc
 * copies every web font in several formats (Scala 2: `.eot` + `.ttf` + `.woff`; Scala 3: Font
 * Awesome as `.ttf` + `.woff` + `.woff2` + an SVG font), and only the most modern format of
 * each font is kept, which every current browser reads (the CSS lists the formats as
 * fallbacks, so the pages look the same). A font without a `.woff` / `.woff2` sibling (e.g.
 * Scala 3's Inter) is kept as it is. Every Scala line keeps its full Scaladoc.
 */
object JavadocJars {

  // most modern first
  private val fontFormats = Seq("woff2", "woff", "ttf", "otf", "eot")
  private val webFormats = Set("woff2", "woff")

  private def baseAndExtension(path: String): (String, String) = {
    val dot = path.lastIndexOf('.')
    if (dot < 0)
      (path, "")
    else
      (path.substring(0, dot), path.substring(dot + 1).toLowerCase)
  }

  /** Drops the font files a `.woff2` / `.woff` copy of the same font makes redundant. */
  def withoutRedundantFonts(mappings: Seq[(File, String)]): Seq[(File, String)] = {
    val formatsByBase: Map[String, Set[String]] =
      mappings.map { case (_, path) => baseAndExtension(path) }.groupBy(_._1).map {
        case (base, entries) => base -> entries.map(_._2).toSet
      }

    def redundant(path: String): Boolean = {
      val (base, extension) = baseAndExtension(path)
      val formats = formatsByBase.getOrElse(base, Set.empty)
      val kept = fontFormats.find(formats.contains)
      val isFont = fontFormats.contains(extension)
      // an SVG next to a web font of the same name is an SVG font, not an image
      val isSvgFont = extension == "svg" && formats.exists(webFormats.contains)

      if (isFont)
        kept.exists(webFormats.contains) && !kept.contains(extension)
      else
        isSvgFont
    }

    mappings.filterNot { case (_, path) => redundant(path) }
  }

  /** The `Compile / packageDoc / mappings` of every published project. */
  val settings: Seq[Setting[_]] = Seq(
    Compile / packageDoc / mappings ~= withoutRedundantFonts
  )
}
