package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  TextCompletionResponse
}
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  CreateCompletionSettings
}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.openaiscala.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper
import io.cequence.openaiscala.service.adapter.{
  OpenAIChatCompletionServiceWrapper,
  OpenAICoreServiceWrapper,
  OpenAIServiceWrapper,
  SimpleServiceWrapper
}
import io.cequence.openaiscala.service.ws.Timeouts

import scala.concurrent.ExecutionContext

/**
 * Handy implicits for streaming support allowing to merge "normal" services with traits
 * offering streaming functions, as well as to extend (monkey patch) the factory methods such
 * that normal services are created automatically with streaming extensions.
 */
object OpenAIStreamedServiceImplicits {

  implicit class ChatCompletionStreamExt(
    service: OpenAIChatCompletionService
  ) {
    def withStreaming(
      streamedExtra: OpenAIChatCompletionStreamedServiceExtra
    ): StreamedServiceTypes.OpenAIChatCompletionStreamedService =
      new OpenAIChatCompletionStreamedServiceWrapper(
        service,
        streamedExtra
      ) with HasOpenAIChatCompletionStreamedExtra
  }

  implicit class ChatCompletionStreamFactoryExt(
    factory: RawWsServiceFactory[OpenAIChatCompletionService]
  ) {
    def withStreaming(
      coreUrl: String,
      authHeaders: Seq[(String, String)] = Nil,
      extraParams: Seq[(String, String)] = Nil,
      timeouts: Option[Timeouts] = None
    )(
      implicit ec: ExecutionContext,
      materializer: Materializer
    ): StreamedServiceTypes.OpenAIChatCompletionStreamedService = {
      val service = factory(
        coreUrl,
        authHeaders,
        extraParams,
        timeouts
      )

      val streamedExtra = OpenAIChatCompletionStreamedServiceFactory(
        coreUrl,
        authHeaders,
        extraParams,
        timeouts
      )

      service.withStreaming(streamedExtra)
    }
  }

  implicit class CoreStreamExt(
    service: OpenAICoreService
  ) {
    def withStreaming(
      streamedExtra: OpenAIStreamedServiceExtra
    ): StreamedServiceTypes.OpenAICoreStreamedService =
      new OpenAICoreStreamedServiceWrapper(
        service,
        streamedExtra
      ) with HasOpenAICoreStreamedExtra
  }

  implicit class ChatCompletionStreamCoreExt(
    service: OpenAICoreService
  ) {
    def withStreaming(
      streamedExtra: OpenAIChatCompletionStreamedServiceExtra
    ): OpenAICoreService with OpenAIChatCompletionStreamedServiceExtra =
      new OpenAICoreStreamedServiceWrapper(
        service,
        streamedExtra
      ) with HasOpenAIChatCompletionStreamedExtra
  }

  implicit class CoreStreamFactoryExt(
    factory: RawWsServiceFactory[OpenAICoreService]
  ) {
    def withStreaming(
      coreUrl: String,
      authHeaders: Seq[(String, String)] = Nil,
      extraParams: Seq[(String, String)] = Nil,
      timeouts: Option[Timeouts] = None
    )(
      implicit ec: ExecutionContext,
      materializer: Materializer
    ): StreamedServiceTypes.OpenAICoreStreamedService = {
      val service = factory(
        coreUrl,
        authHeaders,
        extraParams,
        timeouts
      )

      val streamedExtra = OpenAIStreamedServiceFactory.customInstance(
        coreUrl,
        authHeaders,
        extraParams,
        timeouts
      )

      CoreStreamExt(service).withStreaming(streamedExtra)
    }
  }

  implicit class StreamExt(
    service: OpenAIService
  ) {
    def withStreaming(
      streamedExtra: OpenAIStreamedServiceExtra
    ): StreamedServiceTypes.OpenAIStreamedService =
      new OpenAIStreamedServiceWrapper(
        service,
        streamedExtra
      ) with HasOpenAICoreStreamedExtra
  }

  implicit class ChatCompletionStreamFullExt(
    service: OpenAIService
  ) {
    def withStreaming(
      streamedExtra: OpenAIChatCompletionStreamedServiceExtra
    ): OpenAIService with OpenAIChatCompletionStreamedServiceExtra =
      new OpenAIStreamedServiceWrapper(
        service,
        streamedExtra
      ) with HasOpenAIChatCompletionStreamedExtra
  }

  implicit class StreamFactoryExt(
    factory: OpenAIServiceFactoryHelper[OpenAIService]
  ) {
    def withStreaming: OpenAIServiceFactoryHelper[StreamedServiceTypes.OpenAIStreamedService] =
      new StreamedFactoryAux(factory)

    private final class StreamedFactoryAux(
      factory: OpenAIServiceFactoryHelper[OpenAIService]
    ) extends OpenAIServiceFactoryHelper[StreamedServiceTypes.OpenAIStreamedService] {

      override def customInstance(
        coreUrl: String,
        authHeaders: Seq[(String, String)],
        extraParams: Seq[(String, String)],
        timeouts: Option[Timeouts]
      )(
        implicit ec: ExecutionContext,
        materializer: Materializer
      ): OpenAIStreamedService = {
        val service = factory.customInstance(
          coreUrl,
          authHeaders,
          extraParams,
          timeouts
        )

        val streamedExtra = OpenAIStreamedServiceFactory.customInstance(
          coreUrl,
          authHeaders,
          extraParams,
          timeouts
        )

        StreamExt(service).withStreaming(streamedExtra)
      }
    }
  }

  private class OpenAIChatCompletionStreamedServiceWrapper(
    service: OpenAIChatCompletionService,
    val streamedServiceExtra: OpenAIChatCompletionStreamedServiceExtra
  ) extends OpenAIChatCompletionServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAIChatCompletionService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
    }
  }

  private class OpenAICoreStreamedServiceWrapper[S <: CloseableService](
    service: OpenAICoreService,
    val streamedServiceExtra: S
  ) extends OpenAICoreServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAICoreService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
    }
  }

  private class OpenAIStreamedServiceWrapper[S <: CloseableService](
    service: OpenAIService,
    val streamedServiceExtra: S
  ) extends OpenAIServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAIService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
    }
  }

  private trait HasOpenAICoreStreamedExtra
      extends HasOpenAIChatCompletionStreamedExtraBase[OpenAIStreamedServiceExtra]
      with OpenAIStreamedServiceExtra {

    override def createCompletionStreamed(
      prompt: String,
      settings: CreateCompletionSettings
    ): Source[TextCompletionResponse, NotUsed] =
      streamedServiceExtra.createCompletionStreamed(prompt, settings)
  }

  private type HasOpenAIChatCompletionStreamedExtra =
    HasOpenAIChatCompletionStreamedExtraBase[OpenAIChatCompletionStreamedServiceExtra]

  private trait HasOpenAIChatCompletionStreamedExtraBase[
    E <: OpenAIChatCompletionStreamedServiceExtra
  ] extends OpenAIChatCompletionStreamedServiceExtra {

    val streamedServiceExtra: E
    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      streamedServiceExtra.createChatCompletionStreamed(messages, settings)
  }
}
