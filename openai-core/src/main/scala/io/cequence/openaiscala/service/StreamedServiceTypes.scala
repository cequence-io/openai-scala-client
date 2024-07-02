package io.cequence.openaiscala.service

object StreamedServiceTypes {
  type OpenAIChatCompletionStreamedService = OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra

  type OpenAICoreStreamedService = OpenAICoreService with OpenAIStreamedServiceExtra

  type OpenAIStreamedService = OpenAIService with OpenAIStreamedServiceExtra
}
