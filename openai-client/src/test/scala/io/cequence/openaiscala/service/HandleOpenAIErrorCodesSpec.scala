package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala._
import io.cequence.openaiscala.service.impl.TestFactory
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AsyncWordSpec

class HandleOpenAIErrorCodesSpec
    extends AsyncWordSpec
    with GivenWhenThen
    with AsyncMockFactory {

  private val irrelevantMessages = Seq(UserMessage("Hello"))

  "handleErrorCodes" when {

    "should throw OpenAIScalaUnauthorizedException when 401" in {
      recoverToSucceededIf[OpenAIScalaUnauthorizedException] {
        TestFactory.mockedService401().createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaRateLimitException when 429" in {
      recoverToSucceededIf[OpenAIScalaRateLimitException] {
        TestFactory.mockedService429().createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaServerErrorException when 500" in {
      recoverToSucceededIf[OpenAIScalaServerErrorException] {
        TestFactory.mockedService500().createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaEngineOverloadedException when 503" in {
      recoverToSucceededIf[OpenAIScalaEngineOverloadedException] {
        TestFactory.mockedService503().createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaTokenCountExceededException when 400 with token status message" in {
      recoverToSucceededIf[OpenAIScalaTokenCountExceededException] {
        TestFactory
          .mockedService400token()
          .createChatToolCompletion(irrelevantMessages, Seq.empty)
      }

      recoverToSucceededIf[OpenAIScalaTokenCountExceededException] {
        TestFactory
          .mockedService400token2()
          .createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaClientException when 400 with other status message" in {
      recoverToSucceededIf[OpenAIScalaClientException] {
        TestFactory.mockedService400().createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

    "should throw OpenAIScalaClientException when other status code" in {
      recoverToSucceededIf[OpenAIScalaClientException] {
        TestFactory
          .mockedServiceOther()
          .createChatToolCompletion(irrelevantMessages, Seq.empty)
      }
    }

  }

}
