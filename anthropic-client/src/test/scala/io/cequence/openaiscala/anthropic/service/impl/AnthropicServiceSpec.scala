package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{
  AnthropicScalaUnauthorizedException,
  AnthropicService,
  AnthropicServiceFactory
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory.{
  AnthropicServiceClassImpl,
  apiVersion,
  defaultCoreUrl,
  getAPIKeyFromEnv
}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.wsclient.service.ws.Timeouts
import org.scalamock.scalatest.{AsyncMockFactory, MockFactory}
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.GivenWhenThen
import org.scalatest.PrivateMethodTester.PrivateMethod
import org.scalatest.PrivateMethodTester._
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

class AnthropicServiceSpec
    extends AsyncWordSpec
    with GivenWhenThen
    with AsyncMockFactory
//    with GeneratedMockFactory
    {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

//  def service: AnthropicService =
//    AnthropicServiceFactory()

  "Anthropic Service" when {

    "should fail" in {
      val service = TestFactory.testService()

      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
        service.createMessage(
          Seq(UserMessage("Hello")),
          AnthropicCreateMessageSettings(
            NonOpenAIModelId.claude_3_haiku_20240307,
            max_tokens = 4096
          )
        )
      }
    }

//    "should fail" in {
//      val mocked = stub[Anthropic]
//
//      // mock execPOSTWithStatus to return http code 401 - authentication_error: There’s an issue with your API key.
//      (mocked.execPOSTWithStatus _)
//        .when(*, *, *, *, *)
//        .returns({
//          println(s"returning 401")
//          Future.successful(
//            Right((401, "authentication_error: There’s an issue with your API key."))
//          )
//        })
//      // .when().returns(Future.successful(401))
////      mocked.execPOST _ expects * returning Future.failed(new Exception("Failed")) once
//
//      val e = mocked.createMessage(
//        Seq(UserMessage("Hello")),
//        AnthropicCreateMessageSettings(
//          NonOpenAIModelId.claude_3_haiku_20240307,
//          max_tokens = 4096
//        )
//      )
//
//      e.map(x => assert(x == 1))
//
////      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
////        mocked.createMessage(
////          Seq(UserMessage("Hello")),
////          AnthropicCreateMessageSettings(
////            NonOpenAIModelId.claude_3_haiku_20240307,
////            max_tokens = 4096
////          )
////        )
////      }
//
////      val service = mocked
////
////      for {
////        result <- service.createMessage(
////          Seq(UserMessage("Hello")),
////          AnthropicCreateMessageSettings(
////            NonOpenAIModelId.claude_3_haiku_20240307,
////            max_tokens = 4096
////          )
////        )
////      } yield {
////        result should be(null)
////      }
////
////      (service.execPOSTWithStatus _).verify(*, *, *, *, *).once()
//
////      service
////        .createMessage(
////          Seq(UserMessage("Hello")),
////          AnthropicCreateMessageSettings(
////            NonOpenAIModelId.claude_3_haiku_20240307,
////            max_tokens = 4096
////          )
////        )
////        .map { _ =>
////          fail("Expected an AnthropicScalaUnauthorizedException to be thrown")
////        }
////        .recover { case exception: AnthropicScalaUnauthorizedException =>
////          exception.getMessage should include(
////            "authentication_error: There’s an issue with your API key."
////          )
////        }
//
////      recoverToSucceededIf[AnthropicScalaUnauthorizedException] {
////        service.createMessage(
////          Seq(UserMessage("Hello")),
////          AnthropicCreateMessageSettings(
////            NonOpenAIModelId.claude_3_haiku_20240307,
////            max_tokens = 4096
////          )
////        )
////      }
//
////      (service.createMessage _).expects().throwing(new RuntimeException("getInt called"))
//
////      assertThrows[RuntimeException](service.createMessage(
////        Seq(UserMessage("Hello")),
////        AnthropicCreateMessageSettings(
////          NonOpenAIModelId.claude_3_haiku_20240307,
////          max_tokens = 4096
////        )
////      ))
//
////      val exception: AnthropicScalaUnauthorizedException =
////        intercept[AnthropicScalaUnauthorizedException] {
////          service.createMessage(
////            Seq(UserMessage("Hello")),
////            AnthropicCreateMessageSettings(
////              NonOpenAIModelId.claude_3_haiku_20240307,
////              max_tokens = 4096
////            )
////          )
////        }
//
////      for {
////        e <- //intercept[AnthropicScalaUnauthorizedException] {
////          service.createMessage(
////            Seq(UserMessage("Hello")),
////            AnthropicCreateMessageSettings(
////              NonOpenAIModelId.claude_3_haiku_20240307,
////              max_tokens = 4096
////            )
////          )
////        //}
////      } yield {
////        e should be(null)
////        e.getMessage should include(
////          "authentication_error: There’s an issue with your API key."
////        )
////      }
//
//      // }
//
////      exception should be(null)
////      exception.getMessage should include(
////        "authentication_error: There’s an issue with your API key."
////      )
//    }
  }

}
