package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings._

/**
 * Constants of [[OpenAIService]], mostly defaults
 */
trait OpenAIServiceConsts {

  protected val defaultCoreUrl = "https://api.openai.com/v1/"

  protected val configPrefix = "openai-scala-client"

  protected val configFileName = "openai-scala-client.conf"

  object DefaultSettings {

    val CreateCompletion = CreateCompletionSettings(
      model = ModelId.gpt_3_5_turbo_instruct,
      temperature = Some(0.7),
      max_tokens = Some(1000)
    )

    val CreateRun = CreateRunSettings(
      model = Some(ModelId.gpt_3_5_turbo),
      maxPromptTokens = Some(1000)
    )

    val CreateChatCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_3_5_turbo_1106,
      max_tokens = Some(1000)
    )

    val CreateChatFunCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_3_5_turbo_1106,
      max_tokens = Some(1000)
    )

    val CreateChatToolCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_3_5_turbo_1106,
      max_tokens = Some(1000)
    )

    val CreateEdit = CreateEditSettings(
      model = ModelId.text_davinci_edit_001,
      temperature = Some(0.7)
    )

    // keep all OpenAI defaults
    val CreateImage = CreateImageSettings()

    // keep all OpenAI defaults
    val CreateImageEdit = CreateImageEditSettings()

    // keep all OpenAI defaults
    val CreateImageVariation = CreateImageEditSettings()

    val CreateEmbeddings = CreateEmbeddingsSettings(
      model = ModelId.text_embedding_ada_002
    )

    val CreateSpeech = CreateSpeechSettings(
      model = ModelId.tts_1_1106,
      voice = VoiceType.shimmer
    )

    val CreateTranscription = CreateTranscriptionSettings(
      model = ModelId.whisper_1,
      language = Some("en")
    )

    val CreateTranslation = CreateTranslationSettings(
      model = ModelId.whisper_1
    )

    val UploadFineTuneFile = UploadFileSettings(purpose = FileUploadPurpose.`fine-tune`)

    val UploadBatchFile = UploadFileSettings(purpose = FileUploadPurpose.batch)

    val CreateFineTune = CreateFineTuneSettings(
      model = ModelId.gpt_3_5_turbo_0613
    )

    // keep all OpenAI defaults
    val CreateModeration = CreateModerationSettings()
  }
}
