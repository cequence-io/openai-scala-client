package io.cequence.openaiscala.examples.fireworksai

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.examples.ChatCompletionProvider
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Standalone test for MiniMax-M2.7 on Fireworks AI. Uses Await.result instead of `Example` to
 * avoid sbt's TrapExit swallowing stdout.
 *
 * Requires `FIREWORKS_API_KEY` env var.
 */
object FireworksAIMiniMaxM2Test {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("FireworksAIMiniMaxM2Test")
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

    val service = ChatCompletionProvider.fireworks
    val fireworksModelPrefix = "accounts/fireworks/models/"
    val modelId = fireworksModelPrefix + NonOpenAIModelId.minimax_m2p7

    try {
      println("=" * 80)
      println(s"Test 1 - plain chat completion against $modelId")
      println("=" * 80)

      val plainMessages = Seq(
        SystemMessage("You are a helpful assistant. Answer in one short sentence."),
        UserMessage("What is the capital of Norway?")
      )

      val plainResp = Await.result(
        service.createChatCompletion(
          messages = plainMessages,
          settings = CreateChatCompletionSettings(
            model = modelId,
            temperature = Some(0.1),
            max_tokens = Some(200)
          )
        ),
        90.seconds
      )
      println("Response: " + plainResp.contentHead)

      println()
      println("=" * 80)
      println(s"Test 2 - JSON schema structured output against $modelId")
      println("=" * 80)

      val schema: JsonSchema = JsonSchema.Object(
        properties = Seq(
          "country" -> JsonSchema.String(),
          "capital" -> JsonSchema.String(),
          "population_millions" -> JsonSchema.Number()
        ),
        required = Seq("country", "capital", "population_millions")
      )

      val jsonMessages = Seq(
        SystemMessage(
          "You output strict JSON only, conforming to the requested schema."
        ),
        UserMessage("Give me facts about Norway.")
      )

      val jsonResp = Await.result(
        service.createChatCompletion(
          messages = jsonMessages,
          settings = CreateChatCompletionSettings(
            model = modelId,
            temperature = Some(0),
            max_tokens = Some(400),
            extra_params = Map(
              "response_format" -> Json.obj(
                "type" -> ChatCompletionResponseFormatType.json_object.toString,
                "schema" -> Json.toJson(schema)
              )
            )
          )
        ),
        90.seconds
      )
      println("Response: " + jsonResp.contentHead)
    } catch {
      case e: Throwable =>
        println("ERROR: " + e.getClass.getName + ": " + e.getMessage)
        e.printStackTrace()
    } finally {
      service.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
