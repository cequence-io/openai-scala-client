package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future

/**
 * Calls the OpenAI Responses API for `openai.gpt-oss-120b` hosted on Amazon Bedrock via the
 * `bedrock-mantle` endpoint.
 *
 * Authentication is a Bedrock API key passed as a bearer token, exactly like the OpenAI SDK
 * configured with `OPENAI_BASE_URL` / `OPENAI_API_KEY`. Set the following env vars before
 * running:
 *   - `AWS_BEARER_TOKEN_BEDROCK` - your Bedrock (long-term) API key
 *   - `AWS_BEDROCK_REGION` - e.g. "us-east-2"
 *
 * Unlike `openai.gpt-5.5`, the gpt-oss models are served from the plain "v1" base path,
 * producing the request URL `https://bedrock-mantle.<region>.api.aws/v1/responses`.
 */
object CreateModelResponseViaBedrockMantleGptOss extends ExampleBase[OpenAIService] {

  override protected val service: OpenAIService =
    OpenAIServiceFactory.forBedrockMantle()

  override protected def run: Future[_] =
    service
      .createModelResponse(
        Inputs.Text("Can you explain the features of Amazon Bedrock? Be concise."),
        settings = CreateModelResponseSettings(
          model = NonOpenAIModelId.bedrock_openai_gpt_oss_120b
        )
      )
      .map { response =>
        println(s"Response: ${response.outputText.getOrElse("N/A")}")
        response.usage.foreach { u =>
          println(s"Usage: in=${u.inputTokens} out=${u.outputTokens} total=${u.totalTokens}")
        }
      }
}
