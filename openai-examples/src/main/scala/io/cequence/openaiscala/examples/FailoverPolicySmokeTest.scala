package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.{
  OpenAIScalaChainStoppedException,
  OpenAIScalaModelSkippedException
}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{ModelId, ProviderSettings, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service._
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of the model failover policy ([[FailoverPolicy]]) against OpenAI
 * (`OPENAI_SCALA_CLIENT_API_KEY`):
 *   - (a) `attemptTimeout`: a slow model (gpt-5.4, effort xhigh) is abandoned after ~4 s, the
 *     chain fails over to gpt-5.4-mini; the listener prints every outcome with its usage, and
 *     the abandoned call's late answer (billed!) is awaited for up to 3 minutes - reported,
 *     never a failure if it does not come;
 *   - (b) the JSON helper with a failover chain whose first model does not exist (404), the
 *     second answers;
 *   - (c) `shouldStop` already true: the chain stops with zero calls;
 *   - (d) a short deadline on the slow model: the try is capped to the time left, times out,
 *     and the chain stops with `OpenAIScalaChainStoppedException(byDeadline = true)` (no
 *     failover model gets a try once the deadline has passed);
 *   - (e) a guard in `wrapAttempt` skips the first model with
 *     `OpenAIScalaModelSkippedException`: never retried (even with `retryOnAnyError`), the
 *     failover logged at WARN without a stack trace, the second model answers.
 *
 * A standalone `main` (not the `Example` trait, whose `System.exit` swallows sbt's output).
 * Every section prints PASS/FAIL; the exit code is 1 if any failed.
 */
object FailoverPolicySmokeTest {

  case class Capital(
    capital: String,
    country: String
  )
  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val puzzle = Seq(
    UserMessage(
      "A courier starts at 9:00 and visits 5 shops. Each visit takes 12 minutes, travel " +
        "between shops takes 7, 9, 4 and 11 minutes, and she takes a 15-minute break after " +
        "the third shop. When does she finish? Answer with just the time."
    )
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val service = OpenAIChatCompletionServiceFactory(
      ProviderSettings("https://api.openai.com/v1/", "OPENAI_SCALA_CLIENT_API_KEY")
    )
    val failures = new AtomicInteger(0)

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
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
          Success(())
      }
    }

    def check(
      condition: Boolean,
      message: => String
    ): Unit = if (!condition) sys.error(message)

    def usageOf(response: ChatCompletionResponse): String =
      response.usage
        .map(u =>
          s"in ${u.prompt_tokens} / out ${u.completion_tokens.getOrElse(0)} / reasoning ${u.completion_tokens_details.flatMap(_.reasoning_tokens).getOrElse(0)}"
        )
        .getOrElse("no usage")

    // prints every outcome as it comes (also the late ones, from other threads)
    def printing(
      lateAnswer: Option[Promise[ChatCompletionResponse]] = None
    ): AttemptListener = new AttemptListener {
      override def apply(
        info: AttemptInfo,
        outcome: AttemptOutcome
      ): Unit = {
        val text = outcome match {
          case AttemptOutcome.Answered(r)       => s"answered (${usageOf(r)})"
          case AttemptOutcome.Unparseable(r, _) => s"unparseable (${usageOf(r)})"
          case AttemptOutcome.Failed(e)         => s"failed: ${e.getClass.getSimpleName}"
          case AttemptOutcome.TimedOut(l)       => s"timed out after $l"
          case AttemptOutcome.LateAnswer(r) =>
            lateAnswer.foreach(_.trySuccess(r))
            s"LATE answer (${usageOf(r)})"
          case AttemptOutcome.LateFailure(e) => s"late failure: ${e.getClass.getSimpleName}"
        }
        println(
          s"        [listener] ${info.requestedModel} #${info.modelIndex}.${info.tryNumber} ${info.kind}: $text"
        )
      }
    }

    val slow =
      CreateChatCompletionSettings(ModelId.gpt_5_4).withReasoningEffort(ReasoningEffort.xhigh)
    val fast = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      .withReasoningEffort(ReasoningEffort.none)

    val run = for {
      _ <- section("(a) attemptTimeout: slow model abandoned, failover answers") {
        val late = Promise[ChatCompletionResponse]()

        service
          .createChatCompletionWithFailoverSettings(
            puzzle,
            slow,
            Seq(fast),
            maxRetries = Some(1),
            failureMessage = "Puzzle failed.",
            policy = FailoverPolicy(
              attemptTimeout = Some(4.seconds),
              onAttempt = printing(Some(late))
            )
          )
          .map { response =>
            check(
              response.model.startsWith(ModelId.gpt_5_4_mini),
              s"expected an answer of ${ModelId.gpt_5_4_mini}, got ${response.model}"
            )
            val lateText = Try(Await.result(late.future, 3.minutes)) match {
              case Success(r) => s"late answer of ${r.model} arrived (${usageOf(r)})"
              case Failure(_) => "no late answer within 3 minutes (not a failure)"
            }
            s"answered by ${response.model} (${usageOf(response)}): ${response.contentHead.trim}; $lateText"
          }
      }

      _ <- section("(b) JSON helper: unknown first model, second answers") {
        service
          .createChatCompletionWithJSONFullResponse[Capital](
            Seq(UserMessage("What is the capital of Slovakia?")),
            CreateChatCompletionSettings("no-such-model-xyz"),
            failoverModels = Seq(ModelId.gpt_5_4_mini),
            maxRetries = Some(1),
            policy = FailoverPolicy(onAttempt = printing())
          )
          .map { case (capital, response) =>
            check(capital.capital.toLowerCase.contains("bratislava"), s"got $capital")
            s"$capital (${usageOf(response)})"
          }
      }

      _ <- section("(c) shouldStop already true: stopped, zero calls") {
        val calls = new AtomicInteger(0)

        service
          .createChatCompletionWithFailover(
            puzzle,
            fast,
            Nil,
            failureMessage = "Puzzle failed.",
            policy = FailoverPolicy(
              shouldStop = () => true,
              wrapAttempt = new AttemptWrapper {
                override def apply(
                  info: AttemptInfo,
                  call: () => Future[ChatCompletionResponse]
                ): Future[ChatCompletionResponse] = {
                  calls.incrementAndGet()
                  call()
                }
              }
            )
          )
          .transform {
            case Failure(e: OpenAIScalaChainStoppedException) =>
              check(!e.byDeadline, "expected the stop flag, not the deadline")
              check(calls.get == 0, s"expected zero calls, got ${calls.get}")
              check(e.attempts == 0, s"expected zero attempts, got ${e.attempts}")
              Success(s"stopped: ${e.getMessage}")
            case Failure(e) => Failure(e)
            case Success(_) =>
              Failure(new RuntimeException("expected the chain to be stopped"))
          }
      }

      _ <- section("(d) deadline: the try is capped, times out, the chain stops by deadline") {
        service
          .createChatCompletionWithFailoverSettings(
            puzzle,
            slow,
            Seq(fast),
            maxRetries = Some(1),
            failureMessage = "Puzzle failed.",
            policy = FailoverPolicy(
              deadline = Some(Deadline.now + 1500.millis),
              onAttempt = printing()
            )
          )
          .transform {
            case Failure(e: OpenAIScalaChainStoppedException) =>
              check(e.byDeadline, "expected the deadline to be the reason")
              check(e.attempts == 1, s"expected one try, got ${e.attempts}")
              Success(s"stopped by the deadline after ${e.attempts} try; cause: ${e.getCause}")
            case Failure(e) => Failure(e)
            case Success(_) =>
              Failure(new RuntimeException("expected the chain to be stopped"))
          }
      }

      _ <- section("(e) a guard skips the first model: failover logged as a warning") {
        val calls = new AtomicInteger(0)

        service
          .createChatCompletionWithFailoverSettings(
            puzzle,
            slow,
            Seq(fast),
            retryOnAnyError = true,
            failureMessage = "Puzzle failed.",
            policy = FailoverPolicy(
              onAttempt = printing(),
              wrapAttempt = new AttemptWrapper {
                override def apply(
                  info: AttemptInfo,
                  call: () => Future[ChatCompletionResponse]
                ): Future[ChatCompletionResponse] = {
                  calls.incrementAndGet()
                  if (info.requestedModel == ModelId.gpt_5_4)
                    Future.failed(
                      new OpenAIScalaModelSkippedException(
                        "the guard rules this model out",
                        info.requestedModel
                      )
                    )
                  else
                    call()
                }
              }
            )
          )
          .map { response =>
            check(
              response.model.startsWith(ModelId.gpt_5_4_mini),
              s"expected an answer of ${ModelId.gpt_5_4_mini}, got ${response.model}"
            )
            // one skipped try (never retried, even with retryOnAnyError) + one answered
            check(calls.get == 2, s"expected 2 tries, got ${calls.get}")
            s"answered by ${response.model} after the skip; ${calls.get} tries"
          }
      }
    } yield ()

    Try(Await.result(run, 10.minutes))
    service.close()
    println(
      if (failures.get == 0)
        "ALL PASSED"
      else
        s"${failures.get} FAILED"
    )
    Await.result(system.terminate(), 30.seconds)
    System.exit(
      if (failures.get == 0)
        0
      else
        1
    )
  }
}
