package io.cequence.openaiscala.service

import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{
  OpenAIScalaAttemptTimeoutException,
  OpenAIScalaChainStoppedException,
  OpenAIScalaClientException,
  OpenAIScalaJsonParseException,
  OpenAIScalaModelSkippedException,
  OpenAIScalaServerErrorException,
  OpenAIScalaTokenCountExceededException,
  Retryable
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatRole,
  MessageSpec,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

object FailoverPolicySpec {
  final case class City(city: String)
  implicit val cityFormat: Format[City] = Json.format[City]
}

class FailoverPolicySpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with Eventually
    with BeforeAndAfterAll {

  import FailoverPolicySpec._

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(20, Seconds), interval = Span(25, Millis))

  override def afterAll(): Unit = {
    ()
  }

  private val usage =
    UsageInfo(prompt_tokens = 10, total_tokens = 15, completion_tokens = Some(5))

  private def response(
    content: String,
    model: String = "echo"
  ) = ChatCompletionResponse(
    id = "id",
    created = new java.util.Date(0L),
    model = model,
    system_fingerprint = None,
    choices = Seq(ChatCompletionChoiceInfo(AssistantMessage(content), 0, Some("stop"), None)),
    usage = Some(usage),
    originalResponse = None
  )

  // --- scripted fake -------------------------------------------------------------------

  sealed trait Behavior
  final case class Answer(content: String) extends Behavior
  final case class Fail(error: Throwable) extends Behavior
  final case class FailAfter(
    millis: Long,
    error: Throwable
  ) extends Behavior
  final case class Hang(promise: Promise[ChatCompletionResponse]) extends Behavior
  final case class Throw(error: Throwable) extends Behavior

  final case class Call(
    model: String,
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  )

  /** Per-model scripts; the last behavior of a script repeats. */
  private class ScriptedService(scripts: Map[String, Seq[Behavior]])
      extends OpenAIChatCompletionService {
    private val queue = new ConcurrentLinkedQueue[Call]()
    private val counters = scripts.keys.map(_ -> new AtomicInteger(0)).toMap

    def calls: Seq[Call] = queue.asScala.toList
    def callsOf(model: String): Seq[Call] = calls.filter(_.model == model)

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      queue.add(Call(settings.model, messages, settings))

      val script = scripts(settings.model)
      val i = counters(settings.model).getAndIncrement()
      script(math.min(i, script.size - 1)) match {
        case Answer(c) => Future.successful(response(c))
        case Fail(e)   => Future.failed(e)
        case Throw(e)  => throw e
        case Hang(p)   => p.future
        case FailAfter(ms, e) =>
          Future {
            Thread.sleep(ms)
            throw e
          }
      }
    }

    override def close(): Unit = ()
  }

  private class Recorder extends AttemptListener {
    private val queue = new ConcurrentLinkedQueue[(AttemptInfo, AttemptOutcome)]()
    def all: Seq[(AttemptInfo, AttemptOutcome)] = queue.asScala.toList
    def outcomes: Seq[AttemptOutcome] = all.map(_._2)
    def infos: Seq[AttemptInfo] = all.map(_._1)

    override def apply(
      info: AttemptInfo,
      outcome: AttemptOutcome
    ): Unit = {
      queue.add((info, outcome))
      ()
    }
  }

  private def settingsFor(model: String) = CreateChatCompletionSettings(model = model)
  private val messages = Seq(UserMessage("hi"))

  private def retryable = new OpenAIScalaServerErrorException("server down")
  private def fatal(msg: String = "bad request") = new OpenAIScalaClientException(msg)

  private val defaultIsRetryable: Throwable => Boolean = {
    case Retryable(_) => true
    case _            => false
  }

  private def fastRetries(n: Int) =
    RetrySettings(maxRetries = n, delayOffset = 10.millis, delayBase = 0)

  private def runText(
    service: OpenAIChatCompletionService,
    models: Seq[String],
    policy: FailoverPolicy = FailoverPolicy.default,
    retries: RetrySettings = fastRetries(0),
    isRetryable: Throwable => Boolean = defaultIsRetryable
  ): Future[String] =
    FailoverChain.run[String](
      service,
      models.map(m => FailoverChain.Step(messages, settingsFor(m))),
      accept = r => Right(r.contentHead),
      policy = policy,
      retrySettings = retries,
      isRetryable = isRetryable,
      failureMessage = "Test call failed.",
      logger = org.slf4j.LoggerFactory.getLogger("FailoverPolicySpec")
    )

  private def parsing(r: ChatCompletionResponse) =
    if (r.contentHead.startsWith("{"))
      Right(r.contentHead)
    else
      Left(new OpenAIScalaJsonParseException("not json", r))

  private def runParsing(
    service: OpenAIChatCompletionService,
    models: Seq[String],
    policy: FailoverPolicy
  ): Future[String] =
    FailoverChain.run[String](
      service,
      models.map(m => FailoverChain.Step(messages, settingsFor(m))),
      accept = parsing,
      policy = policy,
      retrySettings = fastRetries(0),
      isRetryable = defaultIsRetryable,
      failureMessage = "Test call failed.",
      logger = org.slf4j.LoggerFactory.getLogger("FailoverPolicySpec")
    )

  // --- tests ----------------------------------------------------------------------------

  "the default policy" should {

    "retry a retryable error on the same model and then answer" in {
      val service = new ScriptedService(
        Map(
          "m1" -> Seq(Fail(retryable), Fail(retryable), Answer("ok")),
          "m2" -> Seq(Answer("no"))
        )
      )
      val recorder = new Recorder

      val result = runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(onAttempt = recorder),
        retries = fastRetries(3)
      ).futureValue

      result shouldBe "ok"
      service.callsOf("m1") should have size 3
      service.callsOf("m2") shouldBe empty
      recorder.infos.map(_.kind) shouldBe Seq(
        AttemptKind.First,
        AttemptKind.TransientRetry,
        AttemptKind.TransientRetry
      )
      recorder.infos.map(_.tryNumber) shouldBe Seq(1, 2, 3)
      recorder.outcomes.last shouldBe a[AttemptOutcome.Answered]
    }

    "fail over at once on a non-retryable error" in {
      val service =
        new ScriptedService(Map("m1" -> Seq(Fail(fatal())), "m2" -> Seq(Answer("ok"))))

      runText(service, Seq("m1", "m2"), retries = fastRetries(3)).futureValue shouldBe "ok"
      service.callsOf("m1") should have size 1
      service.callsOf("m2") should have size 1
    }

    "fail with the last error when every model fails" in {
      val last = fatal("last")
      val service = new ScriptedService(
        Map("m1" -> Seq(Fail(fatal("first"))), "m2" -> Seq(Fail(last)))
      )

      runText(service, Seq("m1", "m2")).failed.futureValue shouldBe theSameInstanceAs(last)
    }

    "retry on any error with retryOnAnyError" in {
      val service = new ScriptedService(Map("m1" -> Seq(Fail(fatal()), Answer("ok"))))

      runText(
        service,
        Seq("m1"),
        retries = fastRetries(2),
        isRetryable = _ => true
      ).futureValue shouldBe "ok"
      service.callsOf("m1") should have size 2
    }

    "fail over on a synchronous throw without a same-model retry" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Throw(retryable)), "m2" -> Seq(Answer("ok")))
      )
      val recorder = new Recorder

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(onAttempt = recorder),
        retries = fastRetries(3)
      ).futureValue shouldBe "ok"

      service.callsOf("m1") should have size 1
      recorder.outcomes.head shouldBe a[AttemptOutcome.Failed]
    }

    "fail over without a same-model retry on a skip exception thrown by wrapAttempt" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("never")), "m2" -> Seq(Answer("ok")))
      )
      val recorder = new Recorder
      val skip = new OpenAIScalaModelSkippedException("not today", "m1")

      val policy = FailoverPolicy(
        onAttempt = recorder,
        wrapAttempt = new AttemptWrapper {
          override def apply(
            info: AttemptInfo,
            call: () => Future[ChatCompletionResponse]
          ): Future[ChatCompletionResponse] =
            if (info.requestedModel == "m1")
              throw skip
            else
              call()
        }
      )

      runText(
        service,
        Seq("m1", "m2"),
        policy,
        retries = fastRetries(3),
        isRetryable = _ => true
      ).futureValue shouldBe "ok"

      service.callsOf("m1") shouldBe empty
      recorder.all.head._1.requestedModel shouldBe "m1"
      recorder.outcomes.head shouldBe AttemptOutcome.Failed(skip)
    }

    "fail over without a same-model retry on a skip exception as a failed Future" in {
      val skip = new OpenAIScalaModelSkippedException("not today", "m1")
      val service = new ScriptedService(
        Map("m1" -> Seq(Fail(skip)), "m2" -> Seq(Answer("ok")))
      )
      val recorder = new Recorder

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(onAttempt = recorder),
        retries = fastRetries(3),
        isRetryable = _ => true
      ).futureValue shouldBe "ok"

      service.callsOf("m1") should have size 1
      recorder.outcomes.head shouldBe AttemptOutcome.Failed(skip)
    }

    "still apply filterModels in the public helper" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("one")), "m2" -> Seq(Answer("two")))
      )

      val result = service
        .createChatCompletionWithFailover(
          messages,
          settingsFor("m1"),
          Seq("m2"),
          maxRetries = Some(0),
          failureMessage = "Test failed.",
          filterModels = Some(models => Future.successful(models.filter(_ == "m2")))
        )
        .futureValue

      result.contentHead shouldBe "two"
      service.callsOf("m1") shouldBe empty
    }
  }

  "finalError" should {
    "pick the token-count error of any model with tokenCountExceededFirst" in {
      val tokenError = new OpenAIScalaTokenCountExceededException("too long")
      val service = new ScriptedService(
        Map("m1" -> Seq(Fail(tokenError)), "m2" -> Seq(Fail(fatal("other"))))
      )

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(finalError = FailoverPolicy.tokenCountExceededFirst)
      ).failed.futureValue shouldBe theSameInstanceAs(tokenError)
    }

    "fall back to the last error when the rule throws" in {
      val last = fatal("last")
      val service = new ScriptedService(
        Map("m1" -> Seq(Fail(fatal("first"))), "m2" -> Seq(Fail(last)))
      )

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(finalError = _ => throw new IllegalStateException("boom"))
      ).failed.futureValue shouldBe theSameInstanceAs(last)
    }

    "fall back to the last error when the rule returns null" in {
      val last = fatal("last")
      val service = new ScriptedService(
        Map("m1" -> Seq(Fail(fatal("first"))), "m2" -> Seq(Fail(last)))
      )

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(finalError = _ => null)
      ).failed.futureValue shouldBe theSameInstanceAs(last)
    }
  }

  "attemptTimeout" should {

    "fail over after a hung try, never retry the timeout and report the late answer" in {
      val hang = Promise[ChatCompletionResponse]()
      val service = new ScriptedService(
        Map("m1" -> Seq(Hang(hang)), "m2" -> Seq(Answer("ok")))
      )
      val recorder = new Recorder
      val inFlight = new AtomicInteger(0)

      val wrapper = new AttemptWrapper {
        override def apply(
          info: AttemptInfo,
          call: () => Future[ChatCompletionResponse]
        ): Future[ChatCompletionResponse] = {
          inFlight.incrementAndGet()
          val f = call()
          f.onComplete(_ => inFlight.decrementAndGet())
          f
        }
      }

      val result = runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(
          attemptTimeout = Some(150.millis),
          onAttempt = recorder,
          wrapAttempt = wrapper
        ),
        retries = fastRetries(3)
      ).futureValue

      result shouldBe "ok"
      service.callsOf("m1") should have size 1
      service.callsOf("m2") should have size 1
      recorder.outcomes.collect { case t: AttemptOutcome.TimedOut => t } should have size 1
      // the timed-out call is still running
      inFlight.get shouldBe 1

      hang.success(response("late", model = "m1-dated"))

      eventually {
        recorder.outcomes.collect { case l: AttemptOutcome.LateAnswer => l } should have size 1
      }
      eventually(inFlight.get shouldBe 0)

      val late = recorder.all.collectFirst { case (i, l: AttemptOutcome.LateAnswer) =>
        (i, l)
      }.get
      late._1.requestedModel shouldBe "m1"
      late._2.response.usage shouldBe Some(usage)
    }

    "report a late failure" in {
      val hang = Promise[ChatCompletionResponse]()
      val service = new ScriptedService(
        Map("m1" -> Seq(Hang(hang)), "m2" -> Seq(Answer("ok")))
      )
      val recorder = new Recorder

      runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(attemptTimeout = Some(100.millis), onAttempt = recorder)
      ).futureValue shouldBe "ok"

      hang.failure(fatal("late boom"))
      eventually {
        recorder.outcomes.collect { case l: AttemptOutcome.LateFailure =>
          l
        } should have size 1
      }
    }

    "fail the chain with the timeout when it is the only model" in {
      val hang = Promise[ChatCompletionResponse]()
      val service = new ScriptedService(Map("m1" -> Seq(Hang(hang))))

      val error = runText(
        service,
        Seq("m1"),
        FailoverPolicy(attemptTimeout = Some(100.millis))
      ).failed.futureValue

      error shouldBe a[OpenAIScalaAttemptTimeoutException]
      error.asInstanceOf[OpenAIScalaAttemptTimeoutException].model shouldBe "m1"
      hang.success(response("x"))
    }
  }

  "deadline" should {

    "start no try once overdue" in {
      val service = new ScriptedService(Map("m1" -> Seq(Answer("ok"))))

      val error = runText(
        service,
        Seq("m1"),
        FailoverPolicy(deadline = Some(Deadline.now - 1.second))
      ).failed.futureValue

      error shouldBe a[OpenAIScalaChainStoppedException]
      error.asInstanceOf[OpenAIScalaChainStoppedException].byDeadline shouldBe true
      error.asInstanceOf[OpenAIScalaChainStoppedException].attempts shouldBe 0
      service.calls shouldBe empty
    }

    "stop with the previous error as the cause once it passes during a try" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(FailAfter(600, fatal("slow"))), "m2" -> Seq(Answer("ok")))
      )

      val error = runText(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(deadline = Some(Deadline.now + 150.millis))
      ).failed.futureValue

      error shouldBe a[OpenAIScalaChainStoppedException]
      val stopped = error.asInstanceOf[OpenAIScalaChainStoppedException]
      stopped.byDeadline shouldBe true
      stopped.attempts shouldBe 1
      stopped.getCause shouldBe a[OpenAIScalaAttemptTimeoutException]
      service.callsOf("m2") shouldBe empty
    }

    "cap a try's limit to the time left" in {
      val hang = Promise[ChatCompletionResponse]()
      val service = new ScriptedService(Map("m1" -> Seq(Hang(hang))))
      val recorder = new Recorder

      runText(
        service,
        Seq("m1"),
        FailoverPolicy(
          attemptTimeout = Some(1.hour),
          deadline = Some(Deadline.now + 300.millis),
          onAttempt = recorder
        )
      ).failed.futureValue

      val limits = recorder.outcomes.collect { case AttemptOutcome.TimedOut(l) => l }
      limits should have size 1
      limits.head should be <= 300.millis
      hang.success(response("x"))
    }
  }

  "shouldStop" should {

    "stop before any call when true up front" in {
      val service = new ScriptedService(Map("m1" -> Seq(Answer("ok"))))

      val error = runText(
        service,
        Seq("m1"),
        FailoverPolicy(shouldStop = () => true)
      ).failed.futureValue

      error shouldBe a[OpenAIScalaChainStoppedException]
      error.asInstanceOf[OpenAIScalaChainStoppedException].byDeadline shouldBe false
      service.calls shouldBe empty
    }

    "stop when flipped during a backoff sleep" in {
      val stop = new AtomicBoolean(false)
      val service = new ScriptedService(Map("m1" -> Seq(Fail(retryable), Answer("ok"))))
      val flipper = new AttemptListener {
        override def apply(
          info: AttemptInfo,
          outcome: AttemptOutcome
        ): Unit = stop.set(true)
      }

      val error = runText(
        service,
        Seq("m1"),
        FailoverPolicy(shouldStop = () => stop.get, onAttempt = flipper),
        retries = RetrySettings(maxRetries = 3, delayOffset = 200.millis, delayBase = 0)
      ).failed.futureValue

      error shouldBe a[OpenAIScalaChainStoppedException]
      val stopped = error.asInstanceOf[OpenAIScalaChainStoppedException]
      stopped.byDeadline shouldBe false
      stopped.attempts shouldBe 1
      stopped.getCause shouldBe a[OpenAIScalaServerErrorException]
      service.calls should have size 1
    }
  }

  "parse handling" should {

    "re-ask the same model with parseRetries" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("garbage"), Answer("""{"a":1}""")))
      )
      val recorder = new Recorder

      runParsing(
        service,
        Seq("m1"),
        FailoverPolicy(parseRetries = 1, onAttempt = recorder)
      ).futureValue shouldBe """{"a":1}"""

      recorder.outcomes.map(_.getClass.getSimpleName) shouldBe Seq("Unparseable", "Answered")
      recorder.infos.map(_.kind) shouldBe Seq(AttemptKind.First, AttemptKind.ParseReask)
      recorder.outcomes.head.asInstanceOf[AttemptOutcome.Unparseable].response.usage shouldBe
        Some(usage)
    }

    "fail with the parse error and never call the next model by default" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("garbage")), "m2" -> Seq(Answer("""{"a":1}""")))
      )

      runParsing(service, Seq("m1", "m2"), FailoverPolicy.default).failed.futureValue shouldBe
        a[OpenAIScalaJsonParseException]
      service.callsOf("m2") shouldBe empty
    }

    "move on to the next model with failoverOnParseError" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("garbage")), "m2" -> Seq(Answer("""{"a":1}""")))
      )

      runParsing(
        service,
        Seq("m1", "m2"),
        FailoverPolicy(failoverOnParseError = true)
      ).futureValue shouldBe """{"a":1}"""
      service.callsOf("m2") should have size 1
    }
  }

  "the JSON helper" should {

    "choose the JSON mode per model" in {
      val service = new ScriptedService(
        Map(
          "m1" -> Seq(Fail(fatal())),
          "m2" -> Seq(Answer("""{"city": "Bratislava"}"""))
        )
      )
      val userMessages = Seq(UserMessage("where?"))

      val (city, _) = service
        .createChatCompletionWithJSONFullResponse[City](
          userMessages,
          settingsFor("m1"),
          failoverModels = Seq("m2"),
          maxRetries = Some(0),
          jsonSchemaModels = Seq("m1")
        )
        .futureValue

      city shouldBe City("Bratislava")

      val first = service.callsOf("m1").head
      first.settings.response_format_type shouldBe Some(
        ChatCompletionResponseFormatType.json_schema
      )
      first.settings.jsonSchema shouldBe defined
      first.messages shouldBe userMessages

      val second = service.callsOf("m2").head
      second.settings.response_format_type shouldBe Some(
        ChatCompletionResponseFormatType.json_object
      )
      second.settings.jsonSchema shouldBe None
      val lastContent = second.messages.last.asInstanceOf[UserMessage].content
      lastContent should startWith("where?")
      lastContent.length should be > "where?".length
    }

    "prepare a failover model only when the chain reaches it" in {
      // a last message only json_schema mode passes on as it is: JSON-object mode (m2, not
      // listed) cannot append the schema to it, so m2's request cannot be built
      val odd = Seq(MessageSpec(ChatRole.User, "where?"))

      val answered = new ScriptedService(Map("m1" -> Seq(Answer("""{"city": "Wien"}"""))))

      answered
        .createChatCompletionWithJSON[City](
          odd,
          settingsFor("m1"),
          failoverModels = Seq("m2"),
          maxRetries = Some(0),
          jsonSchemaModels = Seq("m1")
        )
        .futureValue shouldBe City("Wien")

      // reached, m2's build failure is a failed try - reported, failed over, never sent
      val recorder = new Recorder
      val failing = new ScriptedService(Map("m1" -> Seq(Fail(fatal()))))

      val error = failing
        .createChatCompletionWithJSON[City](
          odd,
          settingsFor("m1"),
          failoverModels = Seq("m2"),
          maxRetries = Some(0),
          retryOnAnyError = true,
          jsonSchemaModels = Seq("m1"),
          policy = FailoverPolicy(onAttempt = recorder)
        )
        .failed
        .futureValue

      error shouldBe an[IllegalArgumentException]
      failing.callsOf("m2") shouldBe empty
      recorder.infos
        .map(i => (i.requestedModel, i.tryNumber)) shouldBe Seq(("m1", 1), ("m2", 1))
      recorder.outcomes.last shouldBe AttemptOutcome.Failed(error)
    }

    "refuse enforceMode text before filterModels runs" in {
      val filtered = new AtomicBoolean(false)
      val service = new ScriptedService(Map("m1" -> Seq(Answer("""{"city": "Wien"}"""))))

      val error = service
        .createChatCompletionWithJSON[City](
          Seq(UserMessage("where?")),
          settingsFor("m1"),
          enforceMode = Some(ChatCompletionResponseFormatType.text),
          filterModels = Some { (models: Seq[String]) =>
            filtered.set(true)
            Future.successful(models)
          }
        )
        .failed
        .futureValue

      error shouldBe an[IllegalArgumentException]
      filtered.get shouldBe false
      service.callsOf("m1") shouldBe empty
    }

    "parse through the policy and re-ask" in {
      val service = new ScriptedService(
        Map("m1" -> Seq(Answer("""{"town": "x"}"""), Answer("""{"city": "Wien"}""")))
      )

      val (city, _) = service
        .createChatCompletionWithJSONFullResponse[City](
          messages,
          settingsFor("m1"),
          maxRetries = Some(0),
          policy = FailoverPolicy(parseRetries = 1)
        )
        .futureValue

      city shouldBe City("Wien")
      service.calls should have size 2
    }
  }

  "the attempt listener" should {
    "be ignored when it throws" in {
      val service = new ScriptedService(Map("m1" -> Seq(Answer("ok"))))
      val listener = new AttemptListener {
        override def apply(
          info: AttemptInfo,
          outcome: AttemptOutcome
        ): Unit = throw new IllegalStateException("listener boom")
      }

      runText(
        service,
        Seq("m1"),
        FailoverPolicy(onAttempt = listener)
      ).futureValue shouldBe "ok"
    }

    "see the usage of answered responses" in {
      val service = new ScriptedService(Map("m1" -> Seq(Answer("ok"))))
      val recorder = new Recorder

      runText(service, Seq("m1"), FailoverPolicy(onAttempt = recorder)).futureValue
      recorder.outcomes.collect { case AttemptOutcome.Answered(r) => r.usage } shouldBe Seq(
        Some(usage)
      )
    }
  }

  "FailoverPolicy" should {
    "refuse invalid values" in {
      an[IllegalArgumentException] should be thrownBy FailoverPolicy(parseRetries = -1)
      an[IllegalArgumentException] should be thrownBy FailoverPolicy(
        attemptTimeout = Some(Duration.Zero)
      )
    }
  }
}
