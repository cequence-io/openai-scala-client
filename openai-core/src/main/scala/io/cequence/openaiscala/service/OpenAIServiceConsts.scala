package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings._

/**
 * Constants of [[OpenAIService]], mostly defaults
 */
trait OpenAIServiceConsts {

  protected val coreUrl = "https://api.openai.com/v1/"

  protected val defaultRequestTimeout: Int = 120 * 1000 // two minute

  protected val defaultReadoutTimeout: Int = 120 * 1000 // two minute

  protected val configPrefix = "openai-scala-client"

  protected val configFileName = "openai-scala-client.conf"

  object DefaultSettings {

    val CreateCompletion: CreateCompletionSettings = CreateCompletionSettings(
      model = ModelId.text_davinci_003,
      temperature = Some(0.7),
      max_tokens = Some(1000)
    )

    val CreateChatCompletion: CreateChatCompletionSettings = CreateChatCompletionSettings(
      model = ModelId.gpt_3_5_turbo,
      max_tokens = Some(1000)
    )

    val CreateEdit: CreateEditSettings = CreateEditSettings(
      model = ModelId.text_davinci_edit_001,
      temperature = Some(0.7)
    )

    // keep all OpenAI defaults
    val CreateImage: CreateImageSettings = CreateImageSettings()

    // keep all OpenAI defaults
    val CreateImageEdit: CreateImageSettings = CreateImageSettings()

    // keep all OpenAI defaults
    val CreateImageVariation: CreateImageSettings = CreateImageSettings()

    val CreateEmbeddings: CreateEmbeddingsSettings = CreateEmbeddingsSettings(
      model = ModelId.text_embedding_ada_002
    )

    val CreateTranscription: CreateTranscriptionSettings = CreateTranscriptionSettings(
      model = ModelId.whisper_1,
      language = Some("en")
    )

    val CreateTranslation: CreateTranslationSettings = CreateTranslationSettings(
      model = ModelId.whisper_1
    )

    val UploadFile: UploadFileSettings = UploadFileSettings(
      purpose = "fine-tune"
    )

    // keep all OpenAI defaults
    val CreateFineTune: CreateFineTuneSettings = CreateFineTuneSettings()

    // keep all OpenAI defaults
    val CreateModeration: CreateModerationSettings = CreateModerationSettings()
  }
}