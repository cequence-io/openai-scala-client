package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort,
  ServiceTier,
  Verbosity
}
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.typesafe.service._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class OpenAITypeSafeChatCompletionServiceSpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  /** Records the request and answers from a canned map. */
  private class Stub(answers: Map[String, Answer]) extends TypeSafeService {
    var lastState: Option[JsValue] = None
    var lastQuestions: Map[String, Question] = Map.empty
    var lastModel: Option[String] = None

    override val defaultModel = "jev-latest"

    override def systemOne(
      state: JsValue,
      questions: Map[String, Question],
      model: String
    ): Future[SystemOneResponse] = {
      lastState = Some(state)
      lastQuestions = questions
      lastModel = Some(model)
      Future.successful(
        SystemOneResponse("jev-1.13.0", answers, Usage(Some(300), Some(40)), Some("req-1"))
      )
    }

    override def listModels: Future[Seq[ModelMetadata]] = Future.successful(Nil)
    override def close(): Unit = ()
  }

  private val schema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "department" -> JsonSchema.String(`enum` = Seq("billing", "technical")),
          "is_urgent" -> JsonSchema.Boolean(Some("Conveys urgency"))
        ),
        required = Seq("department", "is_urgent")
      )
    )
  )

  private val answers: Map[String, Answer] = Map(
    "department" -> ChoiceAnswer("billing", 0.5, Map("billing" -> 0.7, "technical" -> 0.3)),
    "is_urgent" -> NoulAnswer(0.6)
  )

  private val jsonSchemaSettings = CreateChatCompletionSettings(
    model = "jev-preview",
    response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
    jsonSchema = Some(schema)
  )

  private def await[T](f: Future[T]): T = Await.result(f, 5.seconds)

  private def failure(f: Future[_]): Throwable = await(f.failed)

  "createChatCompletion" should {

    "answer a json_schema request with a document of the schema" in {
      val stub = new Stub(answers)
      val service = TypeSafeServiceFactory.asOpenAI(stub)

      val response = await(
        service
          .createChatCompletion(Seq(UserMessage("I was charged twice!")), jsonSchemaSettings)
      )

      Json.parse(response.contentHead) shouldBe
        Json.obj("department" -> "billing", "is_urgent" -> true)
      response.model shouldBe "jev-1.13.0"
      response.id shouldBe "req-1"
      response.choices.head.finish_reason shouldBe Some("stop")
      response.usage.map(u => (u.prompt_tokens, u.completion_tokens, u.total_tokens)) shouldBe
        Some((300, Some(40), 340))
      response.originalResponse.collect { case r: SystemOneResponse => r.requestId } shouldBe
        Some(Some("req-1"))

      // a single user message is sent as plain text, the schema as questions, the model as is
      stub.lastState shouldBe Some(JsString("I was charged twice!"))
      stub.lastQuestions shouldBe Map(
        "department" -> ChoiceQuestion.ofLabels("Department", "billing", "technical"),
        "is_urgent" -> NoulQuestion("Conveys urgency")
      )
      stub.lastModel shouldBe Some("jev-preview")
    }

    "put system messages under `instructions` and a lone user message under `message`" in {
      val stub = new Stub(answers)
      await(
        TypeSafeServiceFactory
          .asOpenAI(stub)
          .createChatCompletion(
            Seq(
              SystemMessage("You triage support tickets."),
              DeveloperMessage("Route Stripe issues to sales."),
              UserMessage("My Stripe webhook is failing.")
            ),
            jsonSchemaSettings
          )
      )

      stub.lastState shouldBe Some(
        Json.obj(
          "instructions" -> "You triage support tickets.\n\nRoute Stripe issues to sales.",
          "message" -> "My Stripe webhook is failing."
        )
      )
    }

    "embed a JSON user message as JSON, alone or under instructions" in {
      val payload = Json.obj("subject" -> "Stripe payout", "order_id" -> "A-104")
      val stub = new Stub(answers)
      val service = TypeSafeServiceFactory.asOpenAI(stub)

      await(
        service
          .createChatCompletion(Seq(UserMessage(Json.stringify(payload))), jsonSchemaSettings)
      )
      stub.lastState shouldBe Some(payload)

      await(
        service.createChatCompletion(
          Seq(
            SystemMessage("Triage."),
            UserMessage(" " + Json.prettyPrint(Json.arr(1, 2)) + "\n")
          ),
          jsonSchemaSettings
        )
      )
      stub.lastState shouldBe Some(
        Json.obj("instructions" -> "Triage.", "message" -> Json.arr(1, 2))
      )

      // not an object or array -> stays text, even when it parses as JSON
      await(service.createChatCompletion(Seq(UserMessage("42")), jsonSchemaSettings))
      stub.lastState shouldBe Some(JsString("42"))
      await(service.createChatCompletion(Seq(UserMessage("{not json")), jsonSchemaSettings))
      stub.lastState shouldBe Some(JsString("{not json"))
    }

    "send several turns as a conversation" in {
      val stub = new Stub(answers)
      await(
        TypeSafeServiceFactory
          .asOpenAI(stub)
          .createChatCompletion(
            Seq(
              SystemMessage("You triage support tickets."),
              UserMessage("Hi"),
              AssistantMessage("Hello, how can I help?"),
              UserSeqMessage(Seq(TextContent("I was charged"), TextContent("twice")))
            ),
            jsonSchemaSettings
          )
      )

      stub.lastState shouldBe Some(
        Json.obj(
          "instructions" -> "You triage support tickets.",
          "conversation" -> Json.arr(
            Json.obj("role" -> "user", "content" -> "Hi"),
            Json.obj("role" -> "assistant", "content" -> "Hello, how can I help?"),
            Json.obj("role" -> "user", "content" -> "I was charged\ntwice")
          )
        )
      )

      // no instructions -> no field
      await(
        TypeSafeServiceFactory
          .asOpenAI(stub)
          .createChatCompletion(Seq(UserMessage("a"), UserMessage("b")), jsonSchemaSettings)
      )
      stub.lastState shouldBe Some(
        Json.obj(
          "conversation" -> Json.arr(
            Json.obj("role" -> "user", "content" -> "a"),
            Json.obj("role" -> "user", "content" -> "b")
          )
        )
      )
    }

    "honour the noul threshold" in {
      val response = await(
        TypeSafeServiceFactory
          .asOpenAI(new Stub(answers))
          .createChatCompletion(
            Seq(UserMessage("x")),
            jsonSchemaSettings.setTypeSafeNoulThreshold(0.9)
          )
      )
      (Json.parse(response.contentHead) \ "is_urgent").as[Boolean] shouldBe false
    }

    "fail (in the future, not by throwing) without json_schema" in {
      val service = TypeSafeServiceFactory.asOpenAI(new Stub(answers))

      val plain = failure(
        service.createChatCompletion(
          Seq(UserMessage("x")),
          CreateChatCompletionSettings("jev-latest")
        )
      )
      plain shouldBe an[OpenAIScalaClientException]
      plain.getMessage should include("json_schema")

      val jsonObject = failure(
        service.createChatCompletion(
          Seq(UserMessage("x")),
          CreateChatCompletionSettings(
            "jev-latest",
            response_format_type = Some(ChatCompletionResponseFormatType.json_object)
          )
        )
      )
      jsonObject.getMessage should include("got json_object")

      val noSchema = failure(
        service.createChatCompletion(
          Seq(UserMessage("x")),
          jsonSchemaSettings.copy(jsonSchema = None)
        )
      )
      noSchema.getMessage should include("no jsonSchema")
    }

    "refuse n > 1, an unanswerable schema, non-text content and other message kinds" in {
      val service = TypeSafeServiceFactory.asOpenAI(new Stub(answers))

      failure(
        service.createChatCompletion(
          Seq(UserMessage("x")),
          jsonSchemaSettings.copy(n = Some(2))
        )
      ).getMessage should include("n = 2")

      val freeText = jsonSchemaSettings.copy(jsonSchema =
        Some(
          JsonSchemaDef(
            "s",
            strict = true,
            structure = Left(JsonSchema.Object(Seq("summary" -> JsonSchema.String())))
          )
        )
      )
      failure(service.createChatCompletion(Seq(UserMessage("x")), freeText)).getMessage should
        include("summary: a free-form string")

      failure(
        service.createChatCompletion(
          Seq(UserSeqMessage(Seq(ImageURLContent("data:image/png;base64,AAA")))),
          jsonSchemaSettings
        )
      ).getMessage should include("Only text content")

      failure(
        service.createChatCompletion(
          Seq(ToolMessage(Some("out"), "call-1", "tool")),
          jsonSchemaSettings
        )
      ).getMessage should include("ToolMessage is not supported")

      failure(service.createChatCompletion(Nil, jsonSchemaSettings)).getMessage should
        include("At least one user")
      failure(
        service.createChatCompletion(
          Seq(SystemMessage("only instructions")),
          jsonSchemaSettings
        )
      ).getMessage should include("At least one user")
    }

    "name every dropped setting in one warning, and warn about nothing else" in {
      import OpenAITypeSafeChatCompletionService.{
        unsupportedSettings,
        unsupportedSettingsMessage
      }

      // the four settings that DO mean something here, plus the TypeSafe threshold
      unsupportedSettings(
        jsonSchemaSettings.copy(n = Some(1)).setTypeSafeNoulThreshold(0.8)
      ) shouldBe empty
      unsupportedSettingsMessage(jsonSchemaSettings) shouldBe None

      val noisy = jsonSchemaSettings
        .copy(
          temperature = Some(0.7),
          top_p = Some(0.9),
          stop = Seq("END"),
          max_tokens = Some(500),
          presence_penalty = Some(0.1),
          frequency_penalty = Some(0.2),
          logit_bias = Map("1" -> 1),
          logprobs = Some(true),
          top_logprobs = Some(3),
          user = Some("u1"),
          seed = Some(42),
          store = Some(true),
          reasoning_effort = Some(ReasoningEffort.high),
          verbosity = Some(Verbosity.low),
          service_tier = Some(ServiceTier.auto),
          parallel_tool_calls = Some(true),
          metadata = Map("k" -> "v"),
          extra_params = Map("some_vendor_flag" -> true)
        )
        .setTypeSafeNoulThreshold(0.7)

      unsupportedSettings(noisy) shouldBe Seq(
        "temperature",
        "top_p",
        "stop",
        "max_tokens",
        "presence_penalty",
        "frequency_penalty",
        "logit_bias",
        "logprobs",
        "top_logprobs",
        "user",
        "seed",
        "store",
        "reasoning_effort",
        "verbosity",
        "service_tier",
        "parallel_tool_calls",
        "metadata",
        "extra_params.some_vendor_flag"
      )

      val message = unsupportedSettingsMessage(noisy).getOrElse(fail("expected a warning"))
      message should startWith("Dropping temperature, top_p, stop")
      message should include("they are not supported by TypeSafe System One (Jev)")
      message should not include "typesafe_noul_threshold"

      unsupportedSettingsMessage(jsonSchemaSettings.copy(seed = Some(1)))
        .getOrElse(fail("expected a warning")) should startWith(
        "Dropping seed because it is not supported"
      )
    }

    "still answer when unsupported settings are set" in {
      val stub = new Stub(answers)
      val response = await(
        TypeSafeServiceFactory
          .asOpenAI(stub)
          .createChatCompletion(
            Seq(UserMessage("x")),
            jsonSchemaSettings.copy(temperature = Some(0.7), max_tokens = Some(100))
          )
      )
      Json.parse(response.contentHead) shouldBe
        Json.obj("department" -> "billing", "is_urgent" -> true)
    }

    "repack the native exceptions onto the OpenAI hierarchy, keeping them as the cause" in {
      def failingWith(e: Throwable) = TypeSafeServiceFactory.asOpenAI(new Stub(answers) {
        override def systemOne(
          state: JsValue,
          questions: Map[String, Question],
          model: String
        ): Future[SystemOneResponse] = Future.failed(e)
      })

      val cases: Seq[(TypeSafeScalaClientException, Class[_])] = Seq(
        new TypeSafeScalaUnauthorizedException("u") -> classOf[
          OpenAIScalaUnauthorizedException
        ],
        new TypeSafeScalaTokenCountExceededException("t") -> classOf[
          OpenAIScalaTokenCountExceededException
        ],
        new TypeSafeScalaRateLimitException("r") -> classOf[OpenAIScalaRateLimitException],
        new TypeSafeScalaEngineOverloadedException("o") -> classOf[
          OpenAIScalaEngineOverloadedException
        ],
        new TypeSafeScalaServerErrorException("s") -> classOf[OpenAIScalaServerErrorException],
        new TypeSafeScalaClientTimeoutException("ti") -> classOf[
          OpenAIScalaClientTimeoutException
        ],
        new TypeSafeScalaClientUnknownHostException("h") -> classOf[
          OpenAIScalaClientUnknownHostException
        ],
        new TypeSafeScalaApiUsageException("a") -> classOf[OpenAIScalaClientException],
        new TypeSafeScalaInvalidRequestException("i") -> classOf[OpenAIScalaClientException],
        new TypeSafeScalaNotFoundException("n") -> classOf[OpenAIScalaClientException],
        new TypeSafeScalaClientException("c") -> classOf[OpenAIScalaClientException]
      )

      cases.foreach { case (native, expected) =>
        val e = failure(
          failingWith(native).createChatCompletion(Seq(UserMessage("x")), jsonSchemaSettings)
        )
        withClue(s"${native.getClass.getSimpleName}: ") {
          e.getClass shouldBe expected
          e.getCause shouldBe theSameInstanceAs(native)
          e.getMessage shouldBe native.getMessage
        }
      }

      // the shared Retryable matcher now sees them right
      Retryable(
        failure(
          failingWith(new TypeSafeScalaRateLimitException("r"))
            .createChatCompletion(Seq(UserMessage("x")), jsonSchemaSettings)
        ).asInstanceOf[OpenAIScalaClientException]
      ) shouldBe true
      Retryable(
        failure(
          failingWith(new TypeSafeScalaUnauthorizedException("u"))
            .createChatCompletion(Seq(UserMessage("x")), jsonSchemaSettings)
        ).asInstanceOf[OpenAIScalaClientException]
      ) shouldBe false

      // a foreign exception passes through untouched
      val foreign = new IllegalStateException("boom")
      failure(
        failingWith(foreign).createChatCompletion(Seq(UserMessage("x")), jsonSchemaSettings)
      ) shouldBe theSameInstanceAs(foreign)
    }

    "not support tool completions" in {
      failure(
        TypeSafeServiceFactory
          .asOpenAI(new Stub(answers))
          .createChatToolCompletion(Seq(UserMessage("x")), Nil)
      ) shouldBe an[OpenAIScalaClientException]
    }
  }
}
