package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait HarmCategory extends EnumValue

object HarmCategory {
  // Category is unspecified.
  case object HARM_CATEGORY_UNSPECIFIED extends HarmCategory
  // PaLM - Negative or harmful comments targeting identity and/or protected attribute.
  case object HARM_CATEGORY_DEROGATORY extends HarmCategory
  // PaLM - Content that is rude, disrespectful, or profane.
  case object HARM_CATEGORY_TOXICITY extends HarmCategory
  // PaLM - Describes scenarios depicting violence against an individual or group, or general descriptions of gore.
  case object HARM_CATEGORY_VIOLENCE extends HarmCategory
  // PaLM - Contains references to sexual acts or other lewd content.
  case object HARM_CATEGORY_SEXUAL extends HarmCategory
  // PaLM - Promotes unchecked medical advice.
  case object HARM_CATEGORY_MEDICAL extends HarmCategory
  // PaLM - Dangerous content that promotes, facilitates, or encourages harmful acts.
  case object HARM_CATEGORY_DANGEROUS extends HarmCategory
  // Gemini - Harassment content.
  case object HARM_CATEGORY_HARASSMENT extends HarmCategory
  // Gemini - Hate speech and content.
  case object HARM_CATEGORY_HATE_SPEECH extends HarmCategory
  // Gemini - Sexually explicit content.
  case object HARM_CATEGORY_SEXUALLY_EXPLICIT extends HarmCategory
  // Gemini - Dangerous content.
  case object HARM_CATEGORY_DANGEROUS_CONTENT extends HarmCategory
  // Gemini - Content that may be used to harm civic integrity.
  case object HARM_CATEGORY_CIVIC_INTEGRITY extends HarmCategory

  def values: Seq[HarmCategory] = Seq(
    HARM_CATEGORY_UNSPECIFIED,
    HARM_CATEGORY_DEROGATORY,
    HARM_CATEGORY_TOXICITY,
    HARM_CATEGORY_VIOLENCE,
    HARM_CATEGORY_SEXUAL,
    HARM_CATEGORY_MEDICAL,
    HARM_CATEGORY_DANGEROUS,
    HARM_CATEGORY_HARASSMENT,
    HARM_CATEGORY_HATE_SPEECH,
    HARM_CATEGORY_SEXUALLY_EXPLICIT,
    HARM_CATEGORY_DANGEROUS_CONTENT,
    HARM_CATEGORY_CIVIC_INTEGRITY
  )
}

sealed trait HarmBlockThreshold extends EnumValue

object HarmBlockThreshold {
  // Threshold is unspecified.
  case object HARM_BLOCK_THRESHOLD_UNSPECIFIED extends HarmBlockThreshold
  // Content with NEGLIGIBLE will be allowed.
  case object BLOCK_LOW_AND_ABOVE extends HarmBlockThreshold
  // Content with NEGLIGIBLE and LOW will be allowed.
  case object BLOCK_MEDIUM_AND_ABOVE extends HarmBlockThreshold
  // Content with NEGLIGIBLE, LOW, and MEDIUM will be allowed.
  case object BLOCK_ONLY_HIGH extends HarmBlockThreshold
  // All content will be allowed.
  case object BLOCK_NONE extends HarmBlockThreshold
  // Turn off the safety filter.
  case object OFF extends HarmBlockThreshold

  def values: Seq[HarmBlockThreshold] = Seq(
    HARM_BLOCK_THRESHOLD_UNSPECIFIED,
    BLOCK_LOW_AND_ABOVE,
    BLOCK_MEDIUM_AND_ABOVE,
    BLOCK_ONLY_HIGH,
    BLOCK_NONE,
    OFF
  )
}

sealed trait HarmProbability extends EnumValue

object HarmProbability {
  // Probability is unspecified.
  case object HARM_PROBABILITY_UNSPECIFIED extends HarmProbability
  // Content has a negligible chance of being unsafe.
  case object NEGLIGIBLE extends HarmProbability
  // Content has a low chance of being unsafe.
  case object LOW extends HarmProbability
  // Content has a medium chance of being unsafe.
  case object MEDIUM extends HarmProbability
  // Content has a high chance of being unsafe.
  case object HIGH extends HarmProbability

  def values: Seq[HarmProbability] = Seq(
    HARM_PROBABILITY_UNSPECIFIED,
    NEGLIGIBLE,
    LOW,
    MEDIUM,
    HIGH
  )
}
