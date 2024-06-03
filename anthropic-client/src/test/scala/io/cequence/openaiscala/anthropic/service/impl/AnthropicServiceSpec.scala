package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service._
import io.cequence.openaiscala.domain.NonOpenAIModelId
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

class AnthropicServiceSpec extends AsyncWordSpec with GivenWhenThen {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

  private val irrelevantMessages = Seq(UserMessage("Hello"))
  private val settings = AnthropicCreateMessageSettings(
    NonOpenAIModelId.claude_3_haiku_20240307,
    max_tokens = 4096
  )

  "Anthropic Service handleErrorCodes" when {

    "should throw AnthropicScalaUnauthorizedException when 401" in {
      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
        TestFactory.mockedService401().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaUnauthorizedException when 403" in {
      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
        TestFactory.mockedService403().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaNotFoundException when 404" in {
      recoverToSucceededIf[AnthropicScalaNotFoundException] {
        TestFactory.mockedService404().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaNotFoundException when 429" in {
      recoverToSucceededIf[AnthropicScalaRateLimitException] {
        TestFactory.mockedService429().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaServerErrorException when 500" in {
      recoverToSucceededIf[AnthropicScalaServerErrorException] {
        TestFactory.mockedService500().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaEngineOverloadedException when 529" in {
      recoverToSucceededIf[AnthropicScalaEngineOverloadedException] {
        TestFactory.mockedService529().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaClientException when 400" in {
      recoverToSucceededIf[AnthropicScalaClientException] {
        TestFactory.mockedService400().createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaClientException when unknown error code" in {
      recoverToSucceededIf[AnthropicScalaClientException] {
        TestFactory.mockedServiceOther().createMessage(irrelevantMessages, settings)
      }
    }

  }

}
