package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.gemini.domain.ThinkingLevel

/**
 * Which thinking configuration a Gemini API model takes, and which thinking levels it accepts
 * \- measured against the live `generateContent` API on 2026-09-26 (every thinkingLevel and a
 * range of thinkingBudget values per model):
 *
 *   - Gemini 3.x, the rolling aliases (`gemini-flash-latest`, `gemini-flash-lite-latest`,
 *     `gemini-pro-latest`) and `nano-banana-pro*` take `thinkingLevel`
 *   - Gemini 2.5 takes `thinkingBudget`, except `gemini-2.5-flash-image`, which rejects any
 *     thinking config
 *   - MINIMAL is rejected by the Pro models (the 3-pro-image ones excepted), by 3.7 / 3.8
 *     Flash and by `gemini-flash-latest` (currently 3.7 Flash)
 *   - the 3.1 Flash image models (`gemini-3.1-flash-image`, `-lite-image`) accept only MINIMAL
 *     and HIGH - LOW / MEDIUM are 400s
 *
 * A `models/` resource prefix is ignored.
 */
object GeminiThinking {

  sealed trait Mode
  case object Levels extends Mode
  case object Budget extends Mode
  case object Unsupported extends Mode

  private val rollingAliases =
    Set("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-pro-latest")

  def bare(model: String): String = EndPoint.stripModelsPrefix(model)

  def mode(model: String): Mode = {
    val m = bare(model)
    if (m.startsWith("gemini-2.5-flash-image")) Unsupported
    else if (isGemini3(m) || rollingAliases.contains(m) || m.startsWith("nano-banana-pro"))
      Levels
    else if (m.startsWith("gemini-2.5")) Budget
    else Unsupported
  }

  def isGemini3(model: String): Boolean = {
    val m = bare(model)
    m.startsWith("gemini-3-") || m.startsWith("gemini-3.")
  }

  private def isPro(m: String): Boolean =
    (isGemini3(m) && m.contains("-pro") || m == "gemini-pro-latest") && !m.contains("image")

  // only MINIMAL and HIGH
  private def isFlashImage(m: String): Boolean =
    m.startsWith("gemini-3.1-flash-image") || m.startsWith("gemini-3.1-flash-lite-image")

  // Flash releases that dropped MINIMAL; `gemini-flash-latest` resolves to 3.7 Flash today
  private val minimalUnsupportedPrefixes =
    Seq("gemini-3.7", "gemini-3.8", "gemini-flash-latest")

  def supportsMinimal(model: String): Boolean = {
    val m = bare(model)
    !isPro(m) && !minimalUnsupportedPrefixes.exists(m.startsWith)
  }

  /**
   * The level for an effort (`none` / `minimal` -> MINIMAL where supported, else LOW; `low`,
   * `medium`, `high`+ -> LOW / MEDIUM / HIGH), moved onto the levels the model accepts.
   */
  def level(
    model: String,
    requested: ThinkingLevel
  ): ThinkingLevel = {
    val m = bare(model)
    if (isFlashImage(m))
      requested match {
        case ThinkingLevel.MINIMAL | ThinkingLevel.LOW => ThinkingLevel.MINIMAL
        case _                                         => ThinkingLevel.HIGH
      }
    else if (requested == ThinkingLevel.MINIMAL && !supportsMinimal(m)) ThinkingLevel.LOW
    else requested
  }
}
