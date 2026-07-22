package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  GetInputTokensCountSettings
}

/**
 * Constants of [[OpenAIService]], mostly defaults
 */
trait OpenAIServiceConsts {

  protected val defaultCoreUrl = "https://api.openai.com/v1/"

  // Amazon Bedrock `bedrock-mantle` endpoint (OpenAI Responses API)
  protected val bedrockMantleBearerTokenEnvKey = "AWS_BEDROCK_BEARER_TOKEN"
  protected val bedrockMantleRegionEnvKey = "AWS_BEDROCK_REGION"

  // `bedrock-mantle` serves models from the standard `v1` base path. The OpenAI provider models
  // (e.g. `openai.gpt-5.5`) are an exception - they are served from the `openai/v1` base path.
  protected val defaultBedrockMantleBasePath = "v1"
  protected val openAIBedrockMantleBasePath = "openai/v1"

  protected def bedrockMantleCoreUrl(
    region: String,
    basePath: String = defaultBedrockMantleBasePath
  ): String =
    s"https://bedrock-mantle.$region.api.aws/$basePath/"

  object DefaultSettings {

    val CreateJsonCompletion = CreateCompletionSettings(
      model = ModelId.gpt_4_1,
      temperature = Some(0.0),
      max_tokens = Some(4000)
    )

    val CreateCompletion = CreateCompletionSettings(
      model = ModelId.gpt_3_5_turbo_instruct,
      temperature = Some(0.7),
      max_tokens = Some(4000)
    )

    val CreateRun = CreateRunSettings(
      model = Some(ModelId.gpt_4o_mini),
      maxPromptTokens = Some(4000)
    )

    val CreateThreadAndRun = CreateThreadAndRunSettings(
      model = Some(ModelId.gpt_5_4_mini)
    )

    val CreateChatCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_5_4_mini,
      max_tokens = Some(4000)
    )

    val CreateChatWebSearchCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_4o_search_preview,
      max_tokens = Some(4000)
    )

    val CreateChatFunCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_5_4_mini,
      max_tokens = Some(4000)
    )

    val CreateChatToolCompletion = CreateChatCompletionSettings(
      model = ModelId.gpt_5_4_mini,
      max_tokens = Some(4000)
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

    val CreateFineTune = CreateFineTuneSettings(
      model = ModelId.gpt_4o_2024_08_06
    )

    val CreateModeration = CreateModerationSettings()

    val CreateModelResponse = CreateModelResponseSettings(
      model = ModelId.gpt_5_4_mini
    )

    val CreateModelResponseInputTokensCount = GetInputTokensCountSettings(
      model = Some(ModelId.gpt_5_4_mini)
    )
  }
}
