package io.cequence.openaiscala.examples.bedrock

import io.cequence.openaiscala.aws.AwsCredentialsProvider
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{BedrockEndpoint, OpenAIService, OpenAIServiceFactory}

import scala.concurrent.Future

/**
 * Amazon Bedrock authenticated with AWS SigV4 - an IAM access key and secret rather than a
 * Bedrock bearer API key. This is the path for deployments that have AWS credentials but no
 * Bedrock API key.
 *
 * Credentials are read per request, so rotating STS / instance-profile / IRSA credentials work
 * without a restart; here they are passed explicitly.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY` and `AWS_BEDROCK_REGION` (or the
 * standard `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`, which `AwsCredentialsProvider
 * .fromEnv()` also accepts).
 */
object BedrockSigV4ChatCompletion extends ExampleBase[OpenAIService] {

  private val region = sys.env.getOrElse("AWS_BEDROCK_REGION", "us-east-1")

  private val credentials = AwsCredentialsProvider.static(
    accessKeyId = sys.env("AWS_BEDROCK_ACCESS_KEY"),
    secretAccessKey = sys.env("AWS_BEDROCK_SECRET_KEY"),
    sessionToken = sys.env.get("AWS_SESSION_TOKEN")
  )

  // the OpenAI provider models are served from the `openai/v1` base path
  override val service: OpenAIService = OpenAIServiceFactory.forBedrockSigV4(
    credentials = credentials,
    region = region,
    isOpenAIModel = true
  )

  private val gptOssService: OpenAIService = OpenAIServiceFactory.forBedrockSigV4(
    credentials = credentials,
    region = region,
    isOpenAIModel = false
  )

  // the classic runtime host additionally accepts cross-region inference profiles
  private val runtimeService: OpenAIService = OpenAIServiceFactory.forBedrockSigV4(
    credentials = credentials,
    region = region,
    endpoint = BedrockEndpoint.Runtime
  )

  private val messages = Seq(UserMessage("What is the capital of Norway? One word."))

  private def show(
    label: String,
    f: Future[io.cequence.openaiscala.domain.response.ChatCompletionResponse]
  ): Future[Unit] =
    f.map(r => println(s"$label -> ${r.contentHead.trim}")).recover { case e: Throwable =>
      println(s"$label -> FAILED: ${e.getMessage.take(160)}")
    }

  override protected def run: Future[_] =
    for {
      _ <- show(
        "mantle openai/v1  gpt-5.6-luna",
        service.createChatCompletion(
          messages,
          CreateChatCompletionSettings(NonOpenAIModelId.bedrock_openai_gpt_5_6_luna)
        )
      )
      _ <- show(
        "mantle v1         gpt-oss-120b",
        gptOssService.createChatCompletion(
          messages,
          CreateChatCompletionSettings(NonOpenAIModelId.bedrock_openai_gpt_oss_120b)
        )
      )
      _ <- show(
        "runtime openai/v1 global luna",
        runtimeService.createChatCompletion(
          messages,
          CreateChatCompletionSettings("global.openai.gpt-5.6-luna")
        )
      )
      _ = gptOssService.close()
      _ = runtimeService.close()
    } yield ()
}
