package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future

/**
 * Calls the (standard) Chat Completions API for `openai.gpt-oss-120b` hosted on Amazon Bedrock
 * via the `bedrock-mantle` endpoint.
 *
 * Unlike `openai.gpt-5.5` (which is Responses-API-only on Bedrock), the gpt-oss models also
 * expose the `/v1/chat/completions` endpoint, so the regular `createChatCompletion` works.
 *
 * Authentication is a Bedrock API key passed as a bearer token, exactly like the OpenAI SDK
 * configured with `OPENAI_BASE_URL` / `OPENAI_API_KEY`. Set the following env vars before
 * running:
 *   - `AWS_BEARER_TOKEN_BEDROCK` - your Bedrock (long-term) API key
 *   - `AWS_BEDROCK_REGION` - e.g. "us-east-2"
 *
 * The gpt-oss models are served from the plain "v1" base path, producing the request URL
 * `https://bedrock-mantle.<region>.api.aws/v1/chat/completions`.
 */
object CreateChatCompletionViaBedrockMantleGptOss extends ExampleBase[OpenAIService] {

  override protected val service: OpenAIService =
    OpenAIServiceFactory.forBedrockMantle()

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage("Can you explain the features of Amazon Bedrock? Be concise.")
        ),
        settings = CreateChatCompletionSettings(
          model = NonOpenAIModelId.bedrock_openai_gpt_oss_120b
        )
      )
      .map { response =>
        println(s"Response: ${response.contentHead}")
        response.usage.foreach { usage =>
          println(
            s"Usage: ${usage.prompt_tokens} prompt + " +
              s"${usage.completion_tokens.getOrElse(0)} completion = ${usage.total_tokens} total"
          )
        }
      }
}
