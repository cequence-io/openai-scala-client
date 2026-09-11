package io.cequence.openaiscala.examples.responsesapi

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.responsesapi.tools.{
  CodeInterpreterContainer,
  CodeInterpreterTool,
  FunctionTool,
  WebSearchTool
}
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ReasoningConfig
}
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.examples.{ChatChunkPrinter, ExampleBase}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * Streamed Responses API call rendered as typed [[ChatChunk]]s: reasoning summaries, the
 * server-side web search and code interpreter (tool layer + `WebSearch` / `CodeExecution` /
 * `CodeExecutionResult`), a client-side function call, citations, finish and usage.
 * `createModelResponseStreamed` gives the raw
 * [[io.cequence.openaiscala.domain.responsesapi.ResponseStreamEvent]]s instead.
 *
 * Requires `openai-scala-client-stream` as a dependency.
 */
object CreateModelResponseStreamed extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  override protected def run: Future[_] =
    service
      .createModelResponseStreamedTyped(
        Inputs.Text(
          "First search the web for the most recent news about missions to Jupiter (one sentence). " +
            "Then compute the 30th Fibonacci number with python. Then call get_weather for Oslo."
        ),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_4,
          reasoning =
            Some(ReasoningConfig(effort = Some(ReasoningEffort.low), summary = Some("auto"))),
          tools = Seq(
            WebSearchTool(),
            CodeInterpreterTool(container = CodeInterpreterContainer.Auto()),
            FunctionTool(
              name = "get_weather",
              parameters = JsonSchema.Object(
                properties = Seq("location" -> JsonSchema.String()),
                required = Seq("location")
              ),
              strict = false,
              description = Some("Get the current weather in a city")
            )
          )
        )
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
