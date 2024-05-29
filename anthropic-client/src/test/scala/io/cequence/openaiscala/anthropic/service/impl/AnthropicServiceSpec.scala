package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service._
import io.cequence.openaiscala.domain.NonOpenAIModelId
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

class AnthropicServiceSpec
    extends AsyncWordSpec
    with GivenWhenThen
    with AsyncMockFactory
    {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

  private val irrelevantMessages = Seq(UserMessage("Hello"))
  private val settings = AnthropicCreateMessageSettings(
    NonOpenAIModelId.claude_3_haiku_20240307,
    max_tokens = 4096
  )

  "Anthropic Service" when {

    "should throw AnthropicScalaUnauthorizedException when 401" in {
      val service = TestFactory.mockedService401()

      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaUnauthorizedException when 403" in {
      val service = TestFactory.mockedService403()

      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaNotFoundException when 404" in {
      val service = TestFactory.mockedService404()

      recoverToSucceededIf[AnthropicScalaNotFoundException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaNotFoundException when 429" in {
      val service = TestFactory.mockedService429()

      recoverToSucceededIf[AnthropicScalaRateLimitException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaServerErrorException when 500" in {
      val service = TestFactory.mockedService500()

      recoverToSucceededIf[AnthropicScalaServerErrorException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaEngineOverloadedException when 529" in {
      val service = TestFactory.mockedService529()

      recoverToSucceededIf[AnthropicScalaEngineOverloadedException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaClientException when 400" in {
      val service = TestFactory.mockedService400()

      recoverToSucceededIf[AnthropicScalaClientException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

    "should throw AnthropicScalaClientException when unknown error code" in {
      val service = TestFactory.mockedServiceOther()

      recoverToSucceededIf[AnthropicScalaClientException] {
        service.createMessage(irrelevantMessages, settings)
      }
    }

  }

}
