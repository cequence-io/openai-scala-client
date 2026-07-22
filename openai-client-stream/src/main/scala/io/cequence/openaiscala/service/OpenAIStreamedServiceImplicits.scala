package io.cequence.openaiscala.service

import akka.NotUsed
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
import io.cequence.openaiscala.service.adapter.{
  OpenAIChatCompletionServiceWrapper,
  OpenAICoreServiceWrapper,
  OpenAIServiceWrapper
}
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper
import io.cequence.wsclient.service.adapter.SimpleServiceWrapper
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.ws.Timeouts
import io.cequence.wsclient.service.{CloseableService, WSClientEngine}

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
    factory: IOpenAIChatCompletionServiceFactory[OpenAIChatCompletionService]
  ) {
    def withStreaming: IOpenAIChatCompletionServiceFactory[
      StreamedServiceTypes.OpenAIChatCompletionStreamedService
    ] =
      new StreamedFactoryAux(factory)

    private final class StreamedFactoryAux(
      factory: IOpenAIChatCompletionServiceFactory[OpenAIChatCompletionService]
    ) extends IOpenAIChatCompletionServiceFactory[
          StreamedServiceTypes.OpenAIChatCompletionStreamedService
        ] {

      override def apply(
        coreUrl: String,
        requestContext: WsRequestContext,
        timeouts: Option[Timeouts]
      )(
        implicit ec: ExecutionContext
      ): StreamedServiceTypes.OpenAIChatCompletionStreamedService = {
        // ONE private stateless engine serves both the base service and the streamed extra;
        // the merged service owns it (closed via alsoClose)
        val engine = StreamedEngineRegistry.outputStreamed(
          TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
        )

        merge(engine, coreUrl, requestContext, alsoClose = Seq(engine))
      }

      override def withEngine(
        engine: WSClientEngine,
        coreUrl: String,
        requestContext: WsRequestContext
      )(
        implicit ec: ExecutionContext
      ): StreamedServiceTypes.OpenAIChatCompletionStreamedService =
        // a shared, caller-supplied engine - closed by its creator
        merge(engine, coreUrl, requestContext, alsoClose = Nil)

      private def merge(
        engine: WSClientEngine,
        coreUrl: String,
        requestContext: WsRequestContext,
        alsoClose: Seq[CloseableService]
      )(
        implicit ec: ExecutionContext
      ): StreamedServiceTypes.OpenAIChatCompletionStreamedService = {
        val service = factory.withEngine(engine, coreUrl, requestContext)
        val streamedExtra =
          OpenAIChatCompletionStreamedServiceFactory.withEngine(
            engine,
            coreUrl,
            requestContext
          )

        new OpenAIChatCompletionStreamedServiceWrapper(service, streamedExtra, alsoClose)
          with HasOpenAIChatCompletionStreamedExtra
      }
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
      requestContext: WsRequestContext = WsRequestContext(),
      timeouts: Option[Timeouts] = None
    )(
      implicit ec: ExecutionContext
    ): StreamedServiceTypes.OpenAICoreStreamedService = {
      // ONE private stateless engine serves both the base service and the streamed extra;
      // the merged service owns it (closed via alsoClose)
      val engine = StreamedEngineRegistry.outputStreamed(
        TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
      )

      val service = factory.withEngine(engine, coreUrl, requestContext)
      val streamedExtra =
        OpenAIStreamedServiceFactory.customEngineInstance(engine, coreUrl, requestContext)

      new OpenAICoreStreamedServiceWrapper(service, streamedExtra, alsoClose = Seq(engine))
        with HasOpenAICoreStreamedExtra
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
        requestContext: WsRequestContext,
        timeouts: Option[Timeouts]
      )(
        implicit ec: ExecutionContext
      ): OpenAIStreamedService = {
        // ONE private stateless engine serves both the base service and the streamed extra;
        // the merged service owns it (closed via alsoClose)
        val engine = StreamedEngineRegistry.outputStreamed(
          TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
        )

        merge(engine, coreUrl, requestContext, alsoClose = Seq(engine))
      }

      override def customEngineInstance(
        engine: WSClientEngine,
        coreUrl: String,
        requestContext: WsRequestContext
      )(
        implicit ec: ExecutionContext
      ): OpenAIStreamedService =
        // a shared, caller-supplied engine - closed by its creator
        merge(engine, coreUrl, requestContext, alsoClose = Nil)

      private def merge(
        engine: WSClientEngine,
        coreUrl: String,
        requestContext: WsRequestContext,
        alsoClose: Seq[CloseableService]
      )(
        implicit ec: ExecutionContext
      ): OpenAIStreamedService = {
        val service = factory.customEngineInstance(engine, coreUrl, requestContext)
        val streamedExtra =
          OpenAIStreamedServiceFactory.customEngineInstance(engine, coreUrl, requestContext)

        new OpenAIStreamedServiceWrapper(service, streamedExtra, alsoClose)
          with HasOpenAICoreStreamedExtra
      }
    }
  }

  private class OpenAIChatCompletionStreamedServiceWrapper(
    service: OpenAIChatCompletionService,
    val streamedServiceExtra: OpenAIChatCompletionStreamedServiceExtra,
    alsoClose: Seq[CloseableService] = Nil // e.g. a privately-created shared engine
  ) extends OpenAIChatCompletionServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAIChatCompletionService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
      alsoClose.foreach(_.close())
    }
  }

  private class OpenAICoreStreamedServiceWrapper[S <: CloseableService](
    service: OpenAICoreService,
    val streamedServiceExtra: S,
    alsoClose: Seq[CloseableService] = Nil // e.g. a privately-created shared engine
  ) extends OpenAICoreServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAICoreService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
      alsoClose.foreach(_.close())
    }
  }

  private class OpenAIStreamedServiceWrapper[S <: CloseableService](
    service: OpenAIService,
    val streamedServiceExtra: S,
    alsoClose: Seq[CloseableService] = Nil // e.g. a privately-created shared engine
  ) extends OpenAIServiceWrapper {

    override protected def delegate: CloseableServiceWrapper[OpenAIService] =
      SimpleServiceWrapper(service)

    // note that because we override the close method it's not delegated through the service wrapper
    override def close(): Unit = {
      service.close()
      streamedServiceExtra.close()
      alsoClose.foreach(_.close())
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
