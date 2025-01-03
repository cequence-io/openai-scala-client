package io.cequence.openaiscala.examples.adapter

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

// requires `FIREWORKS_API_KEY` environment variable to be set
object ChatCompletionInputAdapterForFireworksAI
    extends ExampleBase[OpenAIChatCompletionService] {

  private val logger = Logger(getClass.getName)

  private val adapters = OpenAIServiceAdapters.forChatCompletionService

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  private val fireworksService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://api.fireworks.ai/inference/v1/",
    WsRequestContext(
      authHeaders = Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
    )
  )

  private val modelId =
    NonOpenAIModelId.mixtral_8x22b_instruct // or gemma_7b_it which also doesn't support system messages

  // gemma-7b-it model doesn't support system messages so we need to convert them to user ones
  private val handleSystemMessages = (messages: Seq[BaseMessage]) => {
    val nonSystemMessages = messages.map {
      case SystemMessage(content, _)    => UserMessage(s"System: ${content}")
      case DeveloperMessage(content, _) => UserMessage(s"System: ${content}")
      case x: BaseMessage               => x
    }

    // there cannot be two consecutive user messages, so we need to merge them
    // actually alternating user and assistant messages is supported by Fireworks AI
    nonSystemMessages.foldLeft(Seq.empty[BaseMessage]) {
      case (acc, UserMessage(content, _)) if acc.nonEmpty =>
        acc.last match {
          case UserMessage(lastContent, _) =>
            acc.init :+ UserMessage(lastContent + "\n" + content)
          case _ =>
            acc :+ UserMessage(content)
        }

      case (acc, message) => acc :+ message
    }
  }

  // the fields "seed", "top_logprobs", "logprobs" and "logit_bias" are not supported by Fireworks AI,
  // so we need to remove them from the settings and show a warning
  private val handleSettings = (settings: CreateChatCompletionSettings) => {
    val notPermittedFields = Seq[
      (
        String,
        CreateChatCompletionSettings => Boolean,
        CreateChatCompletionSettings => CreateChatCompletionSettings
      )
    ](
      ("seed", _.seed.isDefined, _.copy(seed = None)),
      ("top_logprobs", _.top_logprobs.isDefined, _.copy(top_logprobs = None)),
      ("logprobs", _.logprobs.isDefined, _.copy(logprobs = None)),
      ("logit_bias", _.logit_bias.nonEmpty, _.copy(logit_bias = Map[String, Int]()))
    )

    // TODO: temperature cannot be lower than 0.01 or so
    notPermittedFields.foldLeft(settings) { case (acc, (fieldName, isDefined, nullifyField)) =>
      if (isDefined(acc)) {
        logger.warn(s"Field '$fieldName' is not supported by Fireworks AI. Dropping it.")
        nullifyField(acc)
      } else acc
    }
  }

  override val service: OpenAIChatCompletionService = {
    // apply the (messages/settings) input adapter to the fireworksService
    adapters.chatCompletionInput(
      handleSystemMessages,
      handleSettings
    )(fireworksService)
  }

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.1),
          max_tokens = Some(512),
          top_p = Some(0.9),
          presence_penalty = Some(0),
          user = Some("peter"),

          // the following fields are not supported by Fireworks AI and normally would throw an error
          logit_bias = Map("I" -> 5, "You" -> -5),
          logprobs = Some(true),
          top_logprobs = Some(4),
          seed = Some(42)
        )
      )
      .map(printMessageContent)
}
