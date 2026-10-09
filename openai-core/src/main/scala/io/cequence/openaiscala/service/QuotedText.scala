package io.cequence.openaiscala.service

import java.util.regex.Pattern
import scala.util.matching.Regex

/**
 * Untrusted text quoted for a model to judge - a message for a guardrail, a passage for a
 * re-ranker - inside a pair of the prompt's `tags`, with the instruction never to follow it.
 * Any of the tags inside the text (any case, stray spaces, attributes, self-closing: `<
 * /Document x="1">`) is defused to `[/document]`, so the text can neither close its own quote
 * nor open another and append "instructions".
 */
private[openaiscala] final class QuotedText(tags: Seq[String]) {
  require(tags.nonEmpty, "No tags to quote with.")

  private val pattern: Regex =
    s"(?i)<\\s*(/?)\\s*(${tags.map(Pattern.quote).mkString("|")})(?:[\\s/][^<>]*)?>".r

  /** `text` between `<tag>` and `</tag>`, on lines of its own. */
  def apply(
    tag: String,
    text: String
  ): String = {
    require(tags.contains(tag), s"'$tag' is not one of the tags ${tags.mkString(", ")}.")
    s"<$tag>\n${defuse(text)}\n</$tag>"
  }

  /** Every opening or closing tag in `text` turned into `[tag]` / `[/tag]`. */
  def defuse(text: String): String =
    pattern.replaceAllIn(
      text,
      m => Regex.quoteReplacement(s"[${m.group(1)}${declared(m.group(2))}]")
    )

  // the tag as declared, whatever its case in the text
  private def declared(tag: String): String = tags.find(_.equalsIgnoreCase(tag)).getOrElse(tag)
}

private[openaiscala] object QuotedText {

  def apply(tags: String*): QuotedText = new QuotedText(tags)
}
