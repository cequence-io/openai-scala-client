package io.cequence.openaiscala.examples.typesafe

import akka.actor.ActorSystem
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{JsonSchema, SystemMessage, UserMessage}
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaUnauthorizedException}
import io.cequence.openaiscala.typesafe.service._
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import play.api.libs.json.{JsString, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for the TypeSafe client (all sections live-verified 2026-09-16):
 *
 *   - `listModels` and the aliases it lists (`jev-latest`, `jev-preview`)
 *   - a text, a JSON-object and a JSON-array (chat log) state
 *   - every question kind and every way of writing one: described / undescribed choice
 *     options, text and JSON-object score levels, a noul with instructions and one with
 *     criteria only, unicode
 *   - an explicit model (`jev-preview`), the request id header, usage
 *   - many questions in one call, many calls in parallel, a service on a shared engine, the
 *     retry adapter
 *   - errors, natively: a bad key (`TypeSafeScalaUnauthorizedException` carrying the request
 *     id), an unknown model (`TypeSafeScalaApiUsageException`), the ~32k-token input limit
 *     (`TypeSafeScalaTokenCountExceededException`), a wrong path
 *     (`TypeSafeScalaNotFoundException`); through the OpenAI adapter the same errors as
 *     `OpenAIScala*` with the native cause; and the client-side checks that mirror the API's
 *     400s (fail before any I/O)
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `TYPESAFE_API_KEY`.
 */
object TypeSafeSmokeTest {

  private val ticket =
    "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. " +
      "I'm losing sales. Please help ASAP."

  def main(args: Array[String]): Unit = {
    // the actor system only serves the retry adapter's scheduler
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val service = TypeSafeServiceFactory()

    var failures = 0

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures += 1
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(400)}")
          Success(())
      }
    }

    def check(
      condition: Boolean,
      message: => String
    ): Unit =
      if (!condition) throw new AssertionError(message)

    def sumsToOne(probabilities: Iterable[Double]): Boolean =
      math.abs(probabilities.sum - 1d) < 0.02

    val all = for {
      _ <- section("listModels") {
        service.listModels.map { models =>
          val names = models.map(_.name)
          check(names.contains(TypeSafeModelId.jev_latest), s"no jev-latest in $names")
          check(models.forall(_.release_date.nonEmpty), "a model without a release date")
          names.mkString(", ")
        }
      }

      _ <- section("text state, the three kinds, request id, usage") {
        service
          .systemOne(
            ticket,
            Map(
              "department" -> ChoiceQuestion(
                "Which team should handle this",
                "billing" -> "Payment or subscription issues",
                "technical" -> "Bugs or integration problems",
                "sales" -> "Pricing or account questions"
              ),
              "frustration" -> ScoreQuestion(
                "How frustrated the customer appears",
                "Calm, just stating facts",
                "Frustrated but civil",
                "Very angry, strong language"
              ),
              "is_urgent" -> NoulQuestion("The message conveys urgency or time-sensitivity")
            )
          )
          .map { r =>
            val d = r.choice("department")
            val f = r.score("frustration")
            val u = r.noul("is_urgent")
            check(r.model.startsWith("jev-"), s"model ${r.model}")
            check(r.requestId.exists(_.startsWith("req_")), s"request id ${r.requestId}")
            check(r.usage.input_tokens.exists(_ > 0), s"usage ${r.usage}")
            check(Set("billing", "technical").contains(d.choice), s"department ${d.choice}")
            check(d.probabilities.keySet == Set("billing", "technical", "sales"), "options")
            check(
              sumsToOne(d.probabilities.values),
              s"choice probabilities ${d.probabilities}"
            )
            check(d.confidence >= 0 && d.confidence <= 1, s"confidence ${d.confidence}")
            check(f.levels == Seq(0, 1, 2), s"levels ${f.levels}")
            check(f.score >= 0 && f.score <= 2, s"score ${f.score}")
            check(f.describe(1).contains("Frustrated but civil"), s"legend ${f.legend}")
            check(sumsToOne(f.probabilities.values), s"score probabilities ${f.probabilities}")
            check(u.isYes(), s"urgent ${u.noul}")
            check(r.unknown.isEmpty, s"unknown answers ${r.unknown}")
            s"${r.model} ${r.requestId.getOrElse("-")} department=${d.choice} " +
              f"frustration=${f.score}%.2f urgent=${u.noul}%.2f usage=${r.usage}"
          }
      }

      _ <- section(
        "JSON-object state, criteria-only noul, undescribed choice, object levels"
      ) {
        service
          .systemOne(
            state = Json.obj(
              "ticket" -> Json.obj(
                "subject" -> "Doppelte Abbuchung 💳",
                "body" -> "Ich wurde zweimal belastet – bitte sofort erstatten!",
                "customer" -> Json.obj("tier" -> "gold", "open_tickets" -> 3)
              ),
              "history" -> Json.arr("2026-09-01: refund requested", "2026-09-03: no reply")
            ),
            questions = Map(
              "is_billing" -> NoulQuestion(
                criteria =
                  Some(NoulCriteria("about payments, charges or refunds", "anything else"))
              ),
              "tone" -> ChoiceQuestion
                .ofLabels("Tone of the message", "calm", "frustrated", "furious"),
              "priority" -> ScoreQuestion(
                criteria = Seq(
                  Json.obj("label" -> "low", "sla" -> "7d"),
                  Json.obj("label" -> "normal", "sla" -> "48h"),
                  Json.obj("label" -> "high", "sla" -> "4h"),
                  Json.obj("label" -> "critical", "sla" -> "30m")
                ),
                instructions = Some(JsString("How should this ticket be prioritised?"))
              ),
              "lang" -> ChoiceQuestion(
                "Language of the ticket body",
                "en" -> "English",
                "de" -> "German",
                "fr" -> "French"
              )
            ),
            model = TypeSafeModelId.jev_preview
          )
          .map { r =>
            val p = r.score("priority")
            check(r.noul("is_billing").isYes(), "not billing?")
            check(r.choice("lang").choice == "de", s"lang ${r.choice("lang")}")
            check(
              Set("frustrated", "furious").contains(r.choice("tone").choice),
              s"tone ${r.choice("tone")}"
            )
            check(p.levels == Seq(0, 1, 2, 3), s"levels ${p.levels}")
            check((p.legend(3) \ "sla").as[String] == "30m", s"object legend ${p.legend}")
            check(p.describe(3).isEmpty, "describe on an object level must be None")
            check(p.score >= 1.5, s"priority ${p.score}")
            f"${r.model} lang=de tone=${r.choice("tone").choice} priority=${p.score}%.2f " +
              s"(most likely ${p.mostLikelyLevel}) is_billing=${r.noul("is_billing").noul}"
          }
      }

      _ <- section("JSON-array (chat log) state") {
        service
          .systemOne(
            state = Json.arr(
              Json.obj("role" -> "user", "content" -> "can you cancel my subscription"),
              Json.obj("role" -> "assistant", "content" -> "Sure, may I ask why?"),
              Json.obj("role" -> "user", "content" -> "too expensive, found a cheaper one")
            ),
            questions = Map(
              "churn_reason" -> ChoiceQuestion(
                "Why is the user leaving?",
                "price" -> "cost too high",
                "missing_feature" -> "lacks something",
                "competitor" -> "switching to a rival"
              )
            )
          )
          .map { r =>
            val c = r.choice("churn_reason")
            check(c.choice == "price", s"churn ${c.ranked}")
            check(c.ranked.head._1 == "price", s"ranked ${c.ranked}")
            s"churn_reason=${c.choice} ${c.ranked.map { case (k, v) => f"$k=$v%.2f" }.mkString(" ")}"
          }
      }

      _ <- section("twelve questions in one call") {
        val questions = (1 to 12).map { i =>
          s"q$i" -> (i % 3 match {
            case 0 => NoulQuestion(s"Question $i: does the customer mention Stripe?")
            case 1 =>
              ChoiceQuestion.ofLabels(
                s"Question $i: sentiment",
                "negative",
                "neutral",
                "positive"
              )
            case _ =>
              ScoreQuestion(
                s"Question $i: how long has this been going on?",
                "hours",
                "days",
                "weeks"
              )
          }): (String, Question)
        }.toMap
        service.systemOne(ticket, questions).map { r =>
          check(r.answers.keySet == questions.keySet, s"answers ${r.answers.keySet}")
          check(r.nouls.size == 4 && r.choices.size == 4 && r.scores.size == 4, "kinds")
          check(r.nouls.values.forall(_.isYes()), s"stripe? ${r.nouls}")
          check(r.choices.values.forall(_.choice == "negative"), s"sentiment ${r.choices}")
          check(r.scores.values.forall(_.mostLikelyLevel == 1), s"days? ${r.scores}")
          s"${r.answers.size} answers, usage ${r.usage}"
        }
      }

      _ <- section("eight calls in parallel") {
        val commands = Seq(
          "What's my balance?",
          "Send two hundred to Maria",
          "Cancel my card",
          "Is the app down?",
          "What's the weather like in Oslo?",
          "Move 50 to savings",
          "Show my last transactions",
          "Tell me a joke"
        )
        val questions = Map(
          "is_banking" -> NoulQuestion(
            "The request is about the user's bank accounts or money"
          )
        )
        Future.traverse(commands)(c => service.systemOne(c, questions).map(c -> _)).map {
          results =>
            val banking = results.collect { case (c, r) if r.noul("is_banking").isYes() => c }
            check(!banking.contains("What's the weather like in Oslo?"), "weather is banking?")
            check(!banking.contains("Tell me a joke"), "joke is banking?")
            check(banking.contains("What's my balance?"), "balance is not banking?")
            check(
              results.map(_._2.requestId).distinct.size == results.size,
              "request ids not unique"
            )
            s"banking=${banking.size}/${results.size}"
        }
      }

      _ <- section("shared engine + retry adapter") {
        implicit val retrySettings: RetrySettings =
          RetrySettings(maxRetries = 2, delayOffset = 200.millis)
        val engine = WSClientEngineRegistry(TransportSettings())
        val shared = TypeSafeServiceAdapters.retry(TypeSafeServiceFactory.withEngine(engine))
        shared.systemOne(ticket, Map("urgent" -> NoulQuestion("Is it urgent?"))).map { r =>
          shared.close()
          engine.close()
          check(r.noul("urgent").isYes(), s"urgent ${r.noul("urgent")}")
          s"urgent=${r.noul("urgent").noul}"
        }
      }

      _ <- section("bad key -> TypeSafeScalaUnauthorizedException (with the request id)") {
        val bogus = TypeSafeServiceFactory(apiKey = "sk-bogus")
        bogus.systemOne(ticket, Map("urgent" -> NoulQuestion("Is it urgent?"))).transform {
          case Failure(e: TypeSafeScalaUnauthorizedException) =>
            bogus.close()
            check(
              e.httpCode.contains(401) && e.errorType.contains("authentication_error"),
              e.toString
            )
            check(e.requestId.exists(_.startsWith("req_")), s"request id ${e.requestId}")
            check(!TypeSafeRetryable(e), "must not be retryable")
            Success(e.getMessage)
          case Failure(other) => bogus.close(); Failure(other)
          case Success(_) =>
            bogus.close(); Failure(new AssertionError("a bogus key was accepted"))
        }
      }

      _ <- section("unknown model -> TypeSafeScalaApiUsageException") {
        service
          .systemOne(
            ticket,
            Map("urgent" -> NoulQuestion("Is it urgent?")),
            model = "jev-nope"
          )
          .transform {
            case Failure(e: TypeSafeScalaApiUsageException) =>
              check(
                e.getMessage.startsWith("Code 400 : Unknown model: jev-nope [request req_"),
                e.getMessage
              )
              check(e.errorType.contains("api_usage_error"), e.toString)
              Success(e.getMessage)
            case Failure(other) => Failure(other)
            case Success(_)     => Failure(new AssertionError("an unknown model was accepted"))
          }
      }

      _ <- section("~34k-token state -> TypeSafeScalaTokenCountExceededException") {
        val huge = JsString((ticket + " ") * 1500)
        service.systemOne(huge, Map("urgent" -> NoulQuestion("Is it urgent?"))).transform {
          case Failure(e: TypeSafeScalaTokenCountExceededException) =>
            check(e.errorType.contains("max_tokens_exceeded"), e.toString)
            Success(e.getMessage)
          case Failure(other) => Failure(other)
          case Success(r)     => Failure(new AssertionError(s"accepted ${r.usage}"))
        }
      }

      _ <- section("wrong base path -> TypeSafeScalaNotFoundException") {
        val wrong =
          TypeSafeServiceFactory(baseUrl = TypeSafeServiceConsts.defaultBaseUrl + "nope")
        wrong.listModels.transform {
          case Failure(e: TypeSafeScalaNotFoundException) =>
            wrong.close(); Success(e.getMessage)
          case Failure(other) => wrong.close(); Failure(other)
          case Success(_) =>
            wrong.close(); Failure(new AssertionError("a wrong path answered"))
        }
      }

      _ <- section(
        "through the OpenAI adapter the same errors are OpenAIScala* with the native cause"
      ) {
        val bogus = TypeSafeServiceFactory.asOpenAI(apiKey = "sk-bogus")
        val schema = JsonSchemaDef(
          "s",
          strict = true,
          structure = Left(JsonSchema.Object(Seq("urgent" -> JsonSchema.Boolean())))
        )
        val settings = CreateChatCompletionSettings(
          TypeSafeModelId.jev_latest,
          response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
          jsonSchema = Some(schema)
        )
        bogus.createChatCompletion(Seq(UserMessage(ticket)), settings).transform {
          case Failure(e: OpenAIScalaUnauthorizedException) =>
            bogus.close()
            check(
              e.getCause.isInstanceOf[TypeSafeScalaUnauthorizedException],
              s"cause ${e.getCause}"
            )
            Success(
              s"${e.getClass.getSimpleName} caused by ${e.getCause.getClass.getSimpleName}"
            )
          case Failure(other) => bogus.close(); Failure(other)
          case Success(_) =>
            bogus.close(); Failure(new AssertionError("a bogus key was accepted"))
        }
      }

      _ <- section("client-side checks mirror the API's 400s") {
        Future {
          def refused(f: => Any): Boolean =
            Try(f).failed.toOption.exists(_.isInstanceOf[IllegalArgumentException])
          check(refused(NoulQuestion()), "noul without instructions or criteria")
          check(refused(ChoiceQuestion.ofLabels("x")), "choice without options")
          check(refused(ScoreQuestion("x")), "score without levels")
          check(
            refused(service.systemOne(JsString("x"), Map.empty[String, Question])),
            "no questions"
          )
          check(
            refused(service.systemOne(Json.toJson(42), Map("q" -> NoulQuestion("x")))),
            "numeric state"
          )
          "4 shapes refused before any I/O"
        }
      }

      _ <- section("OpenAI adapter: json_schema chat completion") {
        val adapter = TypeSafeServiceFactory.asOpenAI(service)
        val schema = JsonSchemaDef(
          name = "triage",
          strict = true,
          structure = Map(
            "type" -> "object",
            "properties" -> Map(
              "department" -> Map(
                "type" -> "string",
                "enum" -> Seq("billing", "technical", "sales")
              ),
              "is_urgent" -> Map("type" -> "boolean"),
              "frustration" -> Map("type" -> "integer", "minimum" -> 1, "maximum" -> 5),
              "topics" -> Map(
                "type" -> "array",
                "items" -> Map(
                  "type" -> "string",
                  "enum" -> Seq("payments", "integration", "pricing")
                )
              )
            )
          )
        )
        adapter
          .createChatCompletion(
            Seq(SystemMessage("You triage support tickets."), UserMessage(ticket)),
            CreateChatCompletionSettings(
              model = TypeSafeModelId.jev_preview,
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(schema)
            )
          )
          .map { r =>
            val json = Json.parse(r.contentHead)
            check(
              Set("billing", "technical").contains((json \ "department").as[String]),
              r.contentHead
            )
            check((json \ "is_urgent").as[Boolean], r.contentHead)
            check((1 to 5).contains((json \ "frustration").as[Int]), r.contentHead)
            check((json \ "topics").as[Seq[String]].contains("integration"), r.contentHead)
            check(
              r.originalResponse.exists(_.isInstanceOf[SystemOneResponse]),
              "originalResponse"
            )
            check(r.usage.exists(_.prompt_tokens > 0), s"usage ${r.usage}")
            s"${r.model} ${r.contentHead}"
          }
      }

      _ <- section("OpenAI adapter: refuses a free-form string schema and a plain request") {
        val adapter = TypeSafeServiceFactory.asOpenAI(service)
        val freeText = JsonSchemaDef(
          "s",
          strict = true,
          structure = Left(JsonSchema.Object(Seq("summary" -> JsonSchema.String())))
        )
        for {
          e1 <- adapter
            .createChatCompletion(
              Seq(UserMessage(ticket)),
              CreateChatCompletionSettings(TypeSafeModelId.jev_latest)
            )
            .failed
          e2 <- adapter
            .createChatCompletion(
              Seq(UserMessage(ticket)),
              CreateChatCompletionSettings(
                TypeSafeModelId.jev_latest,
                response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                jsonSchema = Some(freeText)
              )
            )
            .failed
        } yield {
          check(
            e1.isInstanceOf[OpenAIScalaClientException] && e1.getMessage
              .contains("json_schema"),
            e1.getMessage
          )
          check(e2.getMessage.contains("summary: a free-form string"), e2.getMessage)
          "both refused before any I/O"
        }
      }

      _ <- section("default model / env keys") {
        Future {
          check(
            service.defaultModel == sys.env
              .getOrElse(TypeSafeServiceConsts.defaultModelEnvKey, TypeSafeModelId.jev_latest),
            service.defaultModel
          )
          s"defaultModel=${service.defaultModel}"
        }
      }
    } yield ()

    try Await.result(all, 5.minutes)
    finally {
      service.close()
      Await.result(system.terminate(), 10.seconds)
    }

    println(if (failures == 0) "ALL PASSED" else s"$failures FAILED")
    if (failures > 0) System.exit(1)
  }
}
