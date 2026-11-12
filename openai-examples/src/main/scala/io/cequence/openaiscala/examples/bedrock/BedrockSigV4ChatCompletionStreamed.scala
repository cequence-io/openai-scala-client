package io.cequence.openaiscala.examples.bedrock

import io.cequence.openaiscala.aws.AwsCredentialsProvider
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{BedrockAuth, OpenAIServiceFactory}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * Streamed Bedrock call authenticated with AWS SigV4. Streaming goes through a different
 * engine method than the sync path; the request is signed when the stream runs - once per run,
 * so a re-run is signed afresh.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`.
 */
object BedrockSigV4ChatCompletionStreamed extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService =
    OpenAIServiceFactory.withStreaming.forBedrock(
      auth = BedrockAuth.SigV4(
        AwsCredentialsProvider.static(
          accessKeyId = sys.env("AWS_BEDROCK_ACCESS_KEY"),
          secretAccessKey = sys.env("AWS_BEDROCK_SECRET_KEY")
        )
      ),
      region = sys.env.getOrElse("AWS_BEDROCK_REGION", "us-east-1"),
      isOpenAIModel = true
    )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = Seq(UserMessage("Count from 1 to 5, separated by spaces.")),
        settings = CreateChatCompletionSettings(
          ModelId.bedrock_openai_gpt_5_6_luna
        )
      )
      .runForeach { chunk =>
        chunk.choices.headOption.flatMap(_.delta.content).foreach(print)
      }
      .map(_ => println("\n[streamed ok]"))
}
