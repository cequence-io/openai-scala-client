package io.cequence.openaiscala.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.responsesapi.JsonFormats.{
  createModelResponseSettingsFormat,
  inputsWrites,
  responseStreamEventReads
}
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ResponseStreamEvent
}
import akka.util.ByteString
import io.cequence.openaiscala.domain.agents.{
  AgentInput,
  AgentSessionEvent,
  CreateAgentSessionSettings,
  JsonFormats => AgentsJsonFormats
}
import io.cequence.openaiscala.service.{OpenAIStreamedServiceExtra, ServerSentEvents}
import io.cequence.wsclient.JsonUtil.JsonOps
import play.api.libs.json.{JsValue, Json}

/**
 * Private impl. class of [[OpenAIStreamedServiceExtra]] which offers extra functions with
 * streaming support.
 *
 * @since Jan
 *   2023
 */
private[service] trait OpenAICoreServiceStreamedExtraImpl
    extends OpenAIStreamedServiceExtra
    with OpenAIChatCompletionServiceStreamedExtraImpl
    with CompletionBodyMaker {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createCompletionStreamed(
    prompt: String,
    settings: CreateCompletionSettings
  ): Source[TextCompletionResponse, NotUsed] =
    execJsonStream(
      EndPoint.completions,
      "POST",
      bodyParams = createBodyParamsForCompletion(prompt, settings, stream = true)
    ).map { (json: JsValue) =>
      inBandStreamError(json).foreach(throw _)
      json.asSafe[TextCompletionResponse]
    }

  override def createAgentSessionStreamed(
    settings: CreateAgentSessionSettings,
    input: AgentInput
  ): Source[AgentSessionEvent, NotUsed] =
    agentSessionEvents(
      execRawStream(
        EndPoint.agent_sessions,
        "POST",
        bodyParams = AgentsJsonFormats
          .createAgentSessionBody(settings, Some(input), stream = true)
          .fields
          .toList
          .map { case (name, value) => Param.Raw(name) -> Some(value) },
        extraHeaders = OpenAIAgentsServiceImpl.betaHeaders
      )
    )

  override def streamAgentSessionEvents(
    sessionId: String
  ): Source[AgentSessionEvent, NotUsed] =
    agentSessionEvents(
      execRawStream(
        EndPoint.agent_sessions,
        "GET",
        endPointParam = Some(s"$sessionId/events"),
        extraHeaders = OpenAIAgentsServiceImpl.betaHeaders
      )
    )

  // the session streams carry comment-only heartbeat frames - decoded by the SSE decoder, not
  // the JSON framing; an error body answering the request fails the stream classified
  private def agentSessionEvents(
    bytes: Source[ByteString, NotUsed]
  ): Source[AgentSessionEvent, NotUsed] =
    bytes.via(ServerSentEvents.jsonPayloads()).map { json =>
      if ((json \ "type").isEmpty) inBandStreamError(json).foreach(throw _)
      json.asSafe[AgentSessionEvent](AgentsJsonFormats.agentSessionEventReads)
    }

  override def createModelResponseStreamed(
    inputs: Inputs,
    settings: CreateModelResponseSettings
  ): Source[ResponseStreamEvent, NotUsed] = {
    val body =
      Json.toJsObject(settings.copy(stream = Some(true)))(createModelResponseSettingsFormat) ++
        Json.obj("input" -> inputsWrites.writes(inputs))

    execJsonStream(
      EndPoint.responses,
      "POST",
      bodyParams = body.fields.toList.map { case (name, value) =>
        Param.Raw(name) -> Some(value)
      },
      extraHeaders = CreateModelResponseSettings.betaHeaders(settings)
    ).map { (json: JsValue) =>
      // an error body ({"error": {...}}); streamed `error` events carry the message at the
      // top level and are modeled as ResponseStreamEvent.ErrorEvent
      inBandStreamError(json).foreach(throw _)
      json.asSafe[ResponseStreamEvent]
    }
  }
}
