package io.cequence.openaiscala.service

import io.cequence.openaiscala.aws.{AwsCredentialsProvider, RecordingEngine}
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

/**
 * The unified Bedrock entry point: auth (bearer / SigV4) and endpoint (mantle / runtime) are
 * independent choices, and neither auth form closes a caller-supplied engine.
 */
class BedrockFactorySpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val region = "us-east-1"
  private val bearer = BedrockAuth.BearerToken("bedrock-api-key")
  private val sigV4 = BedrockAuth.SigV4(AwsCredentialsProvider.static("AKIDEXAMPLE", "secret"))

  /** Issues one chat completion and reports the site and headers that reached the engine. */
  private def callThrough(
    auth: BedrockAuth,
    endpoint: BedrockEndpoint = BedrockEndpoint.Mantle,
    isOpenAIModel: Boolean = false
  ): (RecordingEngine, String, Seq[(String, String)]) = {
    val fake = new RecordingEngine
    val service = OpenAIServiceFactory.forBedrockWithEngine(
      engine = fake,
      auth = auth,
      region = region,
      endpoint = endpoint,
      isOpenAIModel = isOpenAIModel
    )

    service.createChatCompletion(
      Seq(UserMessage("hi")),
      CreateChatCompletionSettings(ModelId.bedrock_openai_gpt_oss_120b)
    )
    service.close()

    val (_, site, headers) = fake.calls.head
    // the engine reads auth off the site binding, the signer off the per-call extra headers
    (fake, site.coreUrl, site.requestContext.authHeaders ++ headers)
  }

  "OpenAIServiceFactory.forBedrock" should {

    "send a plain bearer header for BedrockAuth.BearerToken" in {
      val (_, _, headers) = callThrough(bearer)

      headers should contain("Authorization" -> "Bearer bedrock-api-key")
    }

    "sign the request for BedrockAuth.SigV4, with no static bearer alongside it" in {
      val (_, _, headers) = callThrough(sigV4)

      val auth = headers.collect { case ("Authorization", v) => v }
      auth should have size 1
      auth.head should startWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/")
      auth.head should include(s"/$region/bedrock/aws4_request")
    }

    "pick the base URL from the endpoint, independently of the auth used" in {
      val mantle = "https://bedrock-mantle.us-east-1.api.aws/v1/"
      val mantleOpenAI = "https://bedrock-mantle.us-east-1.api.aws/openai/v1/"
      val runtime = "https://bedrock-runtime.us-east-1.amazonaws.com/openai/v1/"

      for (auth <- Seq(bearer, sigV4)) {
        callThrough(auth)._2 shouldBe mantle
        callThrough(auth, isOpenAIModel = true)._2 shouldBe mantleOpenAI
        callThrough(auth, BedrockEndpoint.Runtime)._2 shouldBe runtime
        // the runtime host is always openai/v1, so the flag cannot change it
        callThrough(auth, BedrockEndpoint.Runtime, isOpenAIModel = true)._2 shouldBe runtime
      }
    }

    "never close a caller-supplied engine, under either form of auth" in {
      for (auth <- Seq(bearer, sigV4))
        callThrough(auth)._1.closed shouldBe 0
    }
  }

  "BedrockEndpoint.coreUrl" should {

    "be reachable without going through a factory" in {
      BedrockEndpoint.Mantle.coreUrl("eu-central-1", isOpenAIModel = true) shouldBe
        "https://bedrock-mantle.eu-central-1.api.aws/openai/v1/"
      BedrockEndpoint.Runtime.coreUrl("eu-central-1") shouldBe
        "https://bedrock-runtime.eu-central-1.amazonaws.com/openai/v1/"
    }
  }

  "BedrockAuth.fromEnv" should {

    "fall back to SigV4 when no bearer token is in the environment" in {
      // the token env vars are not set in the test JVM
      BedrockAuth.bearerTokenFromEnv() shouldBe None
      BedrockAuth.fromEnv() shouldBe a[BedrockAuth.SigV4]
    }
  }
}
