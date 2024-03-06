package io.cequence.openaiscala.service

object StreamedServiceTypes {
  type OpenAIChatCompletionStreamedService = OpenAIChatCompletionService
    with OpenAIChatCompletionServiceStreamedExtra

  type OpenAICoreStreamedService = OpenAICoreService with OpenAIServiceStreamedExtra

  type OpenAIStreamedService = OpenAIService with OpenAIServiceStreamedExtra
}
