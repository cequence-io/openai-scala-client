package io.cequence.openaiscala.anthropic.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.{
  AssistantTool,
  FunctionSpec,
  ModelId,
  Pagination,
  SortOrder,
  Thread,
  ThreadFullMessage,
  ThreadMessage,
  ThreadMessageFile,
  ToolSpec
}
import io.cequence.openaiscala.service.OpenAIService

import java.io.File
import scala.concurrent.Future

/**
 * Constants of [[AnthropicService]], mostly defaults
 */
trait AnthropicServiceConsts {

  protected val defaultCoreUrl = "https://api.anthropic.com/v1/"

  protected val defaultRequestTimeout = 120 * 1000 // two minutes

  protected val defaultReadoutTimeout = 120 * 1000 // two minutes

  protected val configPrefix = "anthropic-scala-client"

  protected val configFileName = "anthropic-scala-client.conf"

//  object DefaultSettings {
//
//    val CreateCompletion = CreateCompletionSettings(
//      model = ModelId.text_davinci_003,
//      temperature = Some(0.7),
//      max_tokens = Some(1000)
//    )
//
//    val CreateChatCompletion = CreateChatCompletionSettings(
//      model = ModelId.gpt_3_5_turbo_1106,
//      max_tokens = Some(1000)
//    )
//
//    val CreateChatFunCompletion = CreateChatCompletionSettings(
//      model = ModelId.gpt_3_5_turbo_1106,
//      max_tokens = Some(1000)
//    )
//
//    val CreateChatToolCompletion = CreateChatCompletionSettings(
//      model = ModelId.gpt_3_5_turbo_1106,
//      max_tokens = Some(1000)
//    )
//
//    val CreateEdit = CreateEditSettings(
//      model = ModelId.text_davinci_edit_001,
//      temperature = Some(0.7)
//    )
//
//    // keep all OpenAI defaults
//    val CreateImage = CreateImageSettings()
//
//    // keep all OpenAI defaults
//    val CreateImageEdit = CreateImageEditSettings()
//
//    // keep all OpenAI defaults
//    val CreateImageVariation = CreateImageEditSettings()
//
//    val CreateEmbeddings = CreateEmbeddingsSettings(
//      model = ModelId.text_embedding_ada_002
//    )
//
//    val CreateSpeech = CreateSpeechSettings(
//      model = ModelId.tts_1_1106,
//      voice = VoiceType.shimmer
//    )
//
//    val CreateTranscription = CreateTranscriptionSettings(
//      model = ModelId.whisper_1,
//      language = Some("en")
//    )
//
//    val CreateTranslation = CreateTranslationSettings(
//      model = ModelId.whisper_1
//    )
//
//    val UploadFile = UploadFileSettings(
//      purpose = "fine-tune"
//    )
//
//    val CreateFineTune = CreateFineTuneSettings(
//      model = ModelId.gpt_3_5_turbo_0613
//    )
//
//    // keep all OpenAI defaults
//    val CreateModeration = CreateModerationSettings()
//  }
}
