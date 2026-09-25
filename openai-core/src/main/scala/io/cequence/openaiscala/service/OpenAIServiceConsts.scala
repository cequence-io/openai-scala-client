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

  // Amazon Bedrock. The base URLs live on `BedrockEndpoint` itself so that callers building a
  // service by hand can reuse them; the auth env var lives on `BedrockAuth`.
  protected val bedrockRegionEnvKey = "AWS_BEDROCK_REGION"

  object DefaultSettings {

    val CreateJsonCompletion = CreateCompletionSettings(
      model = ModelId.gpt_4_1,
      temperature = Some(0.0),
      max_tokens = Some(4000)
    )

    // OpenAI's last completions models shut down on 2026-09-28 - there is no OpenAI successor, pass
    // the model of your OpenAI-compatible server (vLLM, Ollama, ...) explicitly
    val CreateCompletion = CreateCompletionSettings(
      model = "gpt-3.5-turbo-instruct",
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
      model = ModelId.gpt_5_search_api,
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

    @deprecated(
      "The /v1/edits endpoint and its models are gone - use createChatCompletion",
      "1.3.1"
    )
    val CreateEdit = CreateEditSettings(
      model = "text-davinci-edit-001",
      temperature = Some(0.7)
    )

    // the API no longer defaults the model (dall-e-2/3 are shut down) - otherwise keep OpenAI's defaults
    val CreateImage = CreateImageSettings(model = Some(ModelId.gpt_image_2))

    val CreateImageEdit = CreateImageEditSettings(model = Some(ModelId.gpt_image_2))

    @deprecated(
      "The /v1/images/variations endpoint is gone - use createImageEdit with a gpt-image model",
      "1.3.1"
    )
    val CreateImageVariation = CreateImageEditSettings()

    val CreateEmbeddings = CreateEmbeddingsSettings(
      model = ModelId.text_embedding_ada_002
    )

    val CreateSpeech = CreateSpeechSettings(
      model = ModelId.gpt_4o_mini_tts,
      voice = VoiceType.shimmer
    )

    val CreateTranscription = CreateTranscriptionSettings(
      model = ModelId.gpt_transcribe,
      language = Some("en")
    )

    @deprecated(
      "whisper-1, the only translations model, is scheduled for shutdown on 2027-02-26",
      "1.3.1"
    )
    val CreateTranslation = CreateTranslationSettings(
      model = "whisper-1"
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
