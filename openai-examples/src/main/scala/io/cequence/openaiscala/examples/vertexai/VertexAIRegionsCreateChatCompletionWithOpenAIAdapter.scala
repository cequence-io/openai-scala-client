package io.cequence.openaiscala.examples.vertexai

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory
import org.slf4j.LoggerFactory

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` environments variable to be set
object VertexAIRegionsCreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val model = NonOpenAIModelId.gemini_2_0_flash_exp

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who makes jokes about Google."),
    UserMessage("What is the weather like in Norway?")
  )

  private val vertexAILocations = Seq(
    "us-central1",
    "asia-east1",
    "asia-east2",
    "asia-northeast1", // model only supports up to 32767 tokens
    "asia-northeast3",
    "asia-south1",
    "asia-southeast1",
    "australia-southeast1", // model only supports up to 32767 tokens
    "europe-central2",
    "europe-north1",
    "europe-southwest1",
    "europe-west1",
    "europe-west2",
    "europe-west3",
    "europe-west4",
    "europe-west6",
    "europe-west8",
    "europe-west9",
    "me-central1",
    "me-central2",
    "me-west1",
    "northamerica-northeast1", // model only supports up to 32767 tokens
    "southamerica-east1",
    "us-east1",
    "us-east2",
    "us-east3",
    "us-east4", // seems slows but revisit
    "us-east5", // seems slows but revisit
    "us-south1",
    "us-west1",
    "us-west4"
  )

  private val adapters = OpenAIServiceAdapters.forChatCompletionService

  override val service: OpenAIChatCompletionService =
    adapters.roundRobin(
      vertexAILocations.map { location =>
        adapters.chatCompletionIntercept(data =>
          Future(
            logger.info(
              "Execution for the location {} succeeded! (took {} ms)",
              location,
              data.execTimeMs
            )
          )
        )(
          VertexAIServiceFactory.asOpenAI(location = location)
        )
      }: _*
    )

  override protected def run: Future[_] =
    Future.sequence(vertexAILocations.map(_ => runForRegion)).map(_ => ())

  private def runForRegion: Future[_] = {
    service.createChatCompletion(
      messages = messages,
      settings = CreateChatCompletionSettings(
        model,
        temperature = Some(0)
      )
    )
  }.recover { case e: Exception =>
    logger.error(s"Location FAILED due to ${e.getMessage}.")
    Future(())
  }
}
