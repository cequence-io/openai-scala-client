package io.cequence.openaiscala.examples.typesafe

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  JsonSchema,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.typesafe.domain.{SystemOneResponse, TypeSafeModelId}
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import play.api.libs.json.Json

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * A walkthrough of `TypeSafeServiceFactory.asOpenAI()`: what an OpenAI-shaped call turns into
 * on the System One wire, and what comes back. The first half runs against a LOCAL server so
 * the exact request body can be printed; the second half repeats the call against the real API
 * (needs `TYPESAFE_API_KEY`) so you can see the live answer.
 *
 * Message mapping (`TypeSafeChatMapping`): system messages become the state's `instructions`,
 * a lone user message its `message` (as JSON when it is one), several turns a `conversation`
 * array; with no system message a lone user message IS the state. Settings mapping: `model` is
 * passed through, `jsonSchema` becomes the questions, `setTypeSafeNoulThreshold` decides where
 * a boolean flips, and the sampling settings (temperature, max_tokens, ...) are ignored -
 * System One does not sample. `response_format_type` MUST be `json_schema`.
 */
object TypeSafeOpenAIAdapterWalkthrough {

  // closed vocabulary only: a string enum (choice), a boolean (noul), a bounded integer
  // (score) and an array of enums (one noul per option)
  private val triageSchema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "department" -> JsonSchema.String(
            description = Some("Which team should handle this"),
            `enum` = Seq("billing", "technical", "sales")
          ),
          "is_urgent" -> JsonSchema.Boolean(Some("The message conveys time pressure")),
          "frustration" -> JsonSchema.Integer(
            Some("How frustrated the customer appears"),
            minimum = Some(1),
            maximum = Some(5)
          ),
          "topics" -> JsonSchema.Array(
            JsonSchema.String(`enum` = Seq("payments", "integration", "pricing")),
            description = Some("What the message is about")
          )
        ),
        required = Seq("department", "is_urgent", "frustration", "topics")
      )
    )
  )

  private val messages = Seq(
    SystemMessage("You triage inbound support tickets for a payments platform."),
    UserMessage("My Stripe connection has been failing for 3 days."),
    AssistantMessage("Sorry about that - has it ever worked?"),
    UserMessage("It worked last week. I'm losing sales, please fix it ASAP.")
  )

  private val settings = CreateChatCompletionSettings(
    model = TypeSafeModelId.jev_latest,
    response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
    jsonSchema = Some(triageSchema),
    // ignored by System One, kept here to show they do no harm
    temperature = Some(0.7),
    max_tokens = Some(500)
  ).setTypeSafeNoulThreshold(0.6)

  private val cannedAnswer =
    """{"model":"jev-1.13.0","answers":{
      |  "department":{"type":"choice","choice":"technical","confidence":0.71,
      |                "probabilities":{"billing":0.13,"technical":0.86,"sales":0.01}},
      |  "is_urgent":{"type":"noul","noul":0.97},
      |  "frustration":{"type":"score","score":2.6,"confidence":0.64,
      |                 "legend":{"0":"1","1":"2","2":"3","3":"4","4":"5"},
      |                 "probabilities":{"0":0.0,"1":0.1,"2":0.3,"3":0.5,"4":0.1}},
      |  "topics.[payments]":{"type":"noul","noul":0.42},
      |  "topics.[integration]":{"type":"noul","noul":0.95},
      |  "topics.[pricing]":{"type":"noul","noul":0.03}
      |},"usage":{"input_tokens":486,"output_tokens":142}}""".stripMargin

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    println("=== what the caller writes (plain OpenAI chat completion) ===")
    messages.foreach(m => println(s"  ${m.role}: $m"))
    println(s"  model              : ${settings.model}")
    println(s"  response_format    : ${settings.response_format_type.get}")
    println(s"  noul threshold     : ${settings.typeSafeNoulThreshold}")
    println(
      s"  ignored by Jev     : temperature=${settings.temperature}, max_tokens=${settings.max_tokens}"
    )

    againstLocalServer()
    liveCall()
  }

  /** Prints the exact System One request the adapter builds. */
  private def againstLocalServer(
  )(
    implicit ec: ExecutionContext
  ): Unit = {
    val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    val engine = WSClientEngineRegistry(TransportSettings())

    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          println(s"\n=== POST ${exchange.getRequestURI.getPath} (what the adapter sends) ===")
          println(Json.prettyPrint(Json.parse(body)))

          val bytes = cannedAnswer.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.getResponseHeaders.add("x-typesafe-request-id", "req_local_demo")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()

    val typeSafe = TypeSafeServiceFactory.withEngine(
      engine,
      apiKey = "demo-key",
      baseUrl = s"http://localhost:${server.getAddress.getPort}"
    )
    val service = TypeSafeServiceFactory.asOpenAI(typeSafe)

    try {
      val response = Await.result(service.createChatCompletion(messages, settings), 30.seconds)

      println("\n=== the ChatCompletionResponse the caller gets back ===")
      println(s"  id (request id)  : ${response.id}")
      println(s"  model            : ${response.model}")
      println(s"  finish_reason    : ${response.choices.head.finish_reason.getOrElse("-")}")
      println(s"  usage            : ${response.usage.get}")
      println(s"  content          : ${Json.prettyPrint(Json.parse(response.contentHead))}")

      // note is_urgent stayed true at 0.97 while topics.[payments] (0.42) dropped out under
      // the 0.6 threshold, and frustration took its most likely level (4 -> value 4)
      response.originalResponse.collect { case r: SystemOneResponse =>
        println("\n=== the calibrated detail, via originalResponse ===")
        r.answers.toSeq.sortBy(_._1).foreach { case (name, answer) =>
          println(s"  $name: $answer")
        }
      }
    } finally {
      service.close()
      engine.close()
      server.stop(0)
    }
  }

  /** The same call against the real API. */
  private def liveCall(
  )(
    implicit ec: ExecutionContext
  ): Unit = {
    val service = TypeSafeServiceFactory.asOpenAI()

    val result = service
      .createChatCompletion(messages, settings)
      .map { response =>
        println("\n=== live call ===")
        println(s"  model   : ${response.model}")
        println(s"  usage   : ${response.usage.get}")
        println(s"  content : ${response.contentHead}")
      }
      .recover { case e =>
        println(
          s"\n=== live call skipped: ${e.getClass.getSimpleName}: ${e.getMessage.take(200)}"
        )
      }

    try Await.result(result, 60.seconds)
    finally service.close()

    val _ = Future.successful(())
  }
}
