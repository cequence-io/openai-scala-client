package io.cequence.openaiscala.examples.bedrock

import io.cequence.openaiscala.aws.AwsCredentialsProvider
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  BedrockAuth,
  BedrockEndpoint,
  OpenAIService,
  OpenAIServiceFactory
}
import io.cequence.wsclient.service.spi.StreamedEngineRegistry

import scala.concurrent.Future

/**
 * Walks every combination the unified Bedrock entry point exposes: both forms of
 * authentication against both hosts, all on ONE shared engine.
 *
 * Also covers the shared-engine contract - each service is closed as soon as its call
 * finishes, and the next combination still succeeds on the same engine, which it could not do
 * if closing a service had torn the engine down.
 *
 * Requires `AWS_BEDROCK_REGION`, a Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`) and IAM
 * credentials (`AWS_BEDROCK_ACCESS_KEY` / `AWS_BEDROCK_SECRET_KEY`).
 */
object BedrockAuthEndpointMatrix extends ExampleBase[OpenAIService] {

  private val region = sys.env.getOrElse("AWS_BEDROCK_REGION", "us-east-1")

  private val engine = StreamedEngineRegistry.outputStreamed()

  // the service the base trait closes; the matrix below builds its own per combination
  override val service: OpenAIService =
    OpenAIServiceFactory.forBedrockWithEngine(engine, region = region)

  private val auths = Seq(
    "bearer" -> BedrockAuth.BearerToken(sys.env("AWS_BEARER_TOKEN_BEDROCK")),
    "sigv4 " -> BedrockAuth.SigV4(AwsCredentialsProvider.fromEnv())
  )

  // the runtime host needs a cross-region inference profile, mantle needs the bare id
  private val endpoints = Seq(
    ("mantle ", BedrockEndpoint.Mantle, ModelId.bedrock_openai_gpt_5_6_luna),
    ("runtime", BedrockEndpoint.Runtime, "global." + ModelId.bedrock_openai_gpt_5_6_luna)
  )

  private val messages = Seq(UserMessage("What is the capital of Norway? One word."))

  override protected def run: Future[_] = {
    val combinations = for {
      (authLabel, auth) <- auths
      (endpointLabel, endpoint, model) <- endpoints
    } yield (authLabel, auth, endpointLabel, endpoint, model)

    val sweep = combinations.foldLeft(Future.unit: Future[Any]) {
      case (acc, (authLabel, auth, endpointLabel, endpoint, model)) =>
        acc.flatMap { _ =>
          val svc = OpenAIServiceFactory.forBedrockWithEngine(
            engine = engine,
            auth = auth,
            region = region,
            endpoint = endpoint,
            isOpenAIModel = true
          )

          svc
            .createChatCompletion(
              messages,
              // max_tokens must still become max_completion_tokens for the prefixed id
              CreateChatCompletionSettings(model, max_tokens = Some(50))
            )
            .map(r => println(s"$authLabel @ $endpointLabel -> ${r.contentHead.trim}"))
            .recover { case e: Throwable =>
              println(s"$authLabel @ $endpointLabel -> FAILED: ${e.getMessage.take(140)}")
            }
            // closing a borrower must not disturb the shared engine the next one reuses
            .map(_ => svc.close())
        }
    }

    sweep.map(_ => engine.close())
  }
}
