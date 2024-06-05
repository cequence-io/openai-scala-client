package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.response.ResponseFormat

case class CreateRunSettings (
                               model: String,
                               metadata: Map[String, String] = Map.empty,
                               temperature: Option[Double] = None,
                               topP: Option[Double] = None,
                               maxPromptTokens: Option[Int] = None,
                               maxCompletionTokens: Option[Int] = None,
                               responseFormat: Option[ResponseFormat] = None,
                             )
