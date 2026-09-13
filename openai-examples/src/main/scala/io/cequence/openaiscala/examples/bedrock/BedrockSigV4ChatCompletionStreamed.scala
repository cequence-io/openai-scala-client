package io.cequence.openaiscala.examples.bedrock

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.aws.AwsCredentialsProvider
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * Streamed Bedrock call authenticated with AWS SigV4. Streaming goes through a different
 * engine method than the sync path, and the signature is computed eagerly because the
 * underlying engine issues the HTTP request when the method is called, not when the `Source`
 * is materialized.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`.
 */
object BedrockSigV4ChatCompletionStreamed extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService =
    OpenAIServiceFactory.withStreaming.forBedrockSigV4(
      credentials = AwsCredentialsProvider.static(
        accessKeyId = sys.env("AWS_BEDROCK_ACCESS_KEY"),
        secretAccessKey = sys.env("AWS_BEDROCK_SECRET_KEY")
      ),
      region = sys.env.getOrElse("AWS_BEDROCK_REGION", "us-east-1"),
      isOpenAIModel = true
    )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = Seq(UserMessage("Count from 1 to 5, separated by spaces.")),
        settings = CreateChatCompletionSettings(
          NonOpenAIModelId.bedrock_openai_gpt_5_6_luna
        )
      )
      .runWith(Sink.foreach { chunk =>
        chunk.choices.headOption.flatMap(_.delta.content).foreach(print)
      })
      .map(_ => println("\n[streamed ok]"))
}
