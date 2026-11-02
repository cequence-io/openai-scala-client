package io.cequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.service.{AttemptInfo, AttemptKind, FailoverLogLevel}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class RetryEngineSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with RetryHelpers {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val system: ActorSystem = ActorSystem("retry-engine-spec")
  private implicit val scheduler: Scheduler = system.scheduler

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(20, Seconds), interval = Span(25, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private implicit val fast: RetrySettings =
    RetrySettings(maxRetries = 2, delayOffset = 10.millis, delayBase = 0)

  private def retryable = new OpenAIScalaServerErrorException("server down")
  private def fatal(msg: String) = new OpenAIScalaClientException(msg)

  /** Scripted calls: the k-th call of an input runs the k-th behavior (the last repeats). */
  private class Script(scripts: Map[String, Seq[() => Future[String]]]) {
    private val counters = scripts.keys.map(_ -> new AtomicInteger(0)).toMap
    private val queue = new ConcurrentLinkedQueue[String]()

    def calls: Seq[String] = queue.asScala.toList
    def callsOf(input: String): Seq[String] = calls.filter(_ == input)

    val call: String => Future[String] = input => {
      queue.add(input)
      val script = scripts(input)
      script(math.min(counters(input).getAndIncrement(), script.size - 1))()
    }
  }

  private def ok(v: String): () => Future[String] = () => Future.successful(v)
  private def failing(e: Throwable): () => Future[String] = () => Future.failed(e)
  private def throwing(e: Throwable): () => Future[String] = () => throw e

  private class Lines {
    private val queue = new ConcurrentLinkedQueue[String]()
    val sink: Option[String => Unit] = Some { (line: String) =>
      queue.add(line)
      ()
    }
    def all: Seq[String] = queue.asScala.toList
  }

  // --- retry parity ---------------------------------------------------------------------

  "retry" should {

    "try once with maxAttempts <= 1" in {
      var n = 0
      val f = RetryHelpers.retry[String](
        () => {
          n += 1
          Future.failed(retryable)
        },
        maxAttempts = 1,
        log = None
      )

      f.failed.futureValue shouldBe a[OpenAIScalaServerErrorException]
      n shouldBe 1
    }

    "retry a retryable error maxAttempts - 1 times" in {
      val n = new AtomicInteger(0)
      val f = RetryHelpers.retry[String](
        () => {
          n.incrementAndGet()
          Future.failed(retryable)
        },
        maxAttempts = 3,
        log = None
      )

      f.failed.futureValue shouldBe a[OpenAIScalaServerErrorException]
      n.get shouldBe 3
    }

    "not retry a non-retryable error" in {
      val n = new AtomicInteger(0)
      val f = RetryHelpers.retry[String](
        () => {
          n.incrementAndGet()
          Future.failed(fatal("bad"))
        },
        maxAttempts = 3,
        log = None
      )

      f.failed.futureValue.getMessage shouldBe "bad"
      n.get shouldBe 1
    }

    "not retry a synchronous throw" in {
      val n = new AtomicInteger(0)
      val f = RetryHelpers.retry[String](
        () => {
          n.incrementAndGet()
          throw retryable
        },
        maxAttempts = 3,
        log = None
      )

      f.failed.futureValue shouldBe a[OpenAIScalaServerErrorException]
      n.get shouldBe 1
    }

    "write the exact retry line, with the exception message and a failure message" in {
      val lines = new Lines
      val n = new AtomicInteger(0)

      RetryHelpers
        .retry[String](
          () =>
            if (n.incrementAndGet() < 3)
              Future.failed(new OpenAIScalaServerErrorException("Boom."))
            else
              Future.successful("done"),
          maxAttempts = 3,
          failureMessage = Some("Call failed."),
          log = lines.sink,
          includeExceptionMessage = true
        )
        .futureValue shouldBe "done"

      lines.all shouldBe Seq(
        "Call failed. Attempt 1. Error: Boom. Retrying...",
        "Call failed. Attempt 2. Error: Boom. Retrying..."
      )
    }

    "write the exact retry line without them" in {
      val lines = new Lines
      val n = new AtomicInteger(0)

      RetryHelpers
        .retry[String](
          () =>
            if (n.incrementAndGet() < 2)
              Future.failed(retryable)
            else
              Future.successful("done"),
          maxAttempts = 2,
          log = lines.sink
        )
        .futureValue shouldBe "done"

      lines.all shouldBe Seq("Attempt 1. Retrying...")
    }

    "write nothing about a failover or the end" in {
      val lines = new Lines

      RetryHelpers
        .retry[String](
          () => Future.failed(retryable),
          maxAttempts = 2,
          failureMessage = Some("x"),
          log = lines.sink
        )
        .failed
        .futureValue

      lines.all should have size 1
    }
  }

  // --- the engine's failover log ---------------------------------------------------------

  private final case class Logged(
    level: FailoverLogLevel,
    message: String,
    error: Option[Throwable]
  )

  private def runEngine(
    script: Script,
    inputs: Seq[String],
    config: RetryEngine.Config[String, Nothing, String]
  ): Future[String] =
    RetryEngine.run[String, String, Nothing, String](
      inputs.map(i => (i, i)),
      script.call,
      config
    )

  private def config(
    logged: ConcurrentLinkedQueue[Logged],
    maxRetries: Int = 0,
    failureMessage: Option[String] = Some("Test call failed."),
    level: Throwable => FailoverLogLevel = FailoverLogLevel.default,
    isRetryable: Throwable => Boolean = RetryEngine.retryableByDefault
  ) = RetryEngine.Config[String, Nothing, String](
    accept = (r: String) => Right(r),
    retrySettings = fast.copy(maxRetries = maxRetries),
    isRetryable = isRetryable,
    failoverLogLevel = level,
    failureMessage = failureMessage,
    logFailover = Some {
      (
        l: FailoverLogLevel,
        m: String,
        e: Option[Throwable]
      ) =>
        logged.add(Logged(l, m, e))
        ()
    }
  )

  "the engine" should {

    "fail over in order and fail with the last error, logging at Error with the stack trace" in {
      val logged = new ConcurrentLinkedQueue[Logged]()
      val last = fatal("last")
      val first = fatal("first")
      val script = new Script(Map("a" -> Seq(failing(first)), "b" -> Seq(failing(last))))

      runEngine(
        script,
        Seq("a", "b"),
        config(logged)
      ).failed.futureValue shouldBe theSameInstanceAs(
        last
      )

      script.calls shouldBe Seq("a", "b")

      logged.asScala.toList shouldBe List(
        Logged(
          FailoverLogLevel.Error,
          "a - Test call failed after retries! Initiating failover to b.",
          Some(first)
        ),
        Logged(
          FailoverLogLevel.Error,
          "b - Test call failed after retries! Initiating failover to N/A.",
          Some(last)
        ),
        Logged(
          FailoverLogLevel.Error,
          "No more failover inputs to try! Throwing the last error: last",
          None
        )
      )
    }

    "use the label-only line without a failure message" in {
      val logged = new ConcurrentLinkedQueue[Logged]()
      val script = new Script(Map("a" -> Seq(failing(fatal("x"))), "b" -> Seq(ok("fine"))))

      runEngine(
        script,
        Seq("a", "b"),
        config(logged, failureMessage = None)
      ).futureValue shouldBe "fine"

      logged.asScala.map(_.message).toList shouldBe List(
        "a failed after retries! Initiating failover to b."
      )
    }

    "log a skip at Warn, with the reason and no stack trace, and never retry it" in {
      val logged = new ConcurrentLinkedQueue[Logged]()
      val skip = new OpenAIScalaModelSkippedException("over budget", "a")
      val script = new Script(Map("a" -> Seq(failing(skip)), "b" -> Seq(ok("fine"))))

      runEngine(
        script,
        Seq("a", "b"),
        config(logged, maxRetries = 3, isRetryable = _ => true)
      ).futureValue shouldBe "fine"

      script.callsOf("a") should have size 1
      logged.asScala.toList shouldBe List(
        Logged(
          FailoverLogLevel.Warn,
          "a - Test call failed after retries! Initiating failover to b. Reason: over budget",
          None
        )
      )
    }

    "use a custom level function for the failover and the final line" in {
      val logged = new ConcurrentLinkedQueue[Logged]()
      val script = new Script(Map("a" -> Seq(failing(fatal("x")))))

      runEngine(
        script,
        Seq("a"),
        config(logged, level = _ => FailoverLogLevel.Debug)
      ).failed.futureValue

      logged.asScala.toList shouldBe List(
        Logged(
          FailoverLogLevel.Debug,
          "a - Test call failed after retries! Initiating failover to N/A. Reason: x",
          None
        ),
        Logged(
          FailoverLogLevel.Debug,
          "No more failover inputs to try! Throwing the last error: x",
          None
        )
      )
    }

    "fall back to Error when the level function throws or returns null" in {
      val logged = new ConcurrentLinkedQueue[Logged]()
      val script = new Script(Map("a" -> Seq(failing(fatal("x")))))

      runEngine(
        script,
        Seq("a"),
        config(logged, level = _ => throw new RuntimeException("no"))
      ).failed.futureValue

      logged.asScala.map(_.level).toList shouldBe List(
        FailoverLogLevel.Error,
        FailoverLogLevel.Error
      )
    }

    "report the tries to the outcome callback" in {
      val outcomes =
        new ConcurrentLinkedQueue[(AttemptInfo, RetryEngine.Outcome[String, Nothing])]()
      val script = new Script(Map("a" -> Seq(failing(retryable), ok("fine"))))
      val logged = new ConcurrentLinkedQueue[Logged]()

      val cfg = config(logged, maxRetries = 1).copy[String, Nothing, String](
        onOutcome = (
          i: AttemptInfo,
          o: RetryEngine.Outcome[String, Nothing]
        ) => {
          outcomes.add((i, o))
          ()
        }
      )

      runEngine(script, Seq("a"), cfg).futureValue shouldBe "fine"

      outcomes.asScala.map(_._1.kind).toList shouldBe List(
        AttemptKind.First,
        AttemptKind.TransientRetry
      )
      outcomes.asScala.last._2 shouldBe RetryEngine.Outcome.Accepted("fine")
    }
  }

  "the engine's time limits" should {

    "round a limit from the time left UP to the millisecond" in {
      RetryEngine.tryLimit(None, Some(1496755370.nanos)) shouldBe Some(1497.millis)
      RetryEngine.tryLimit(Some(4.seconds), Some(1496755370.nanos)) shouldBe Some(1497.millis)
      RetryEngine.tryLimit(Some(4.seconds), Some(10.seconds)) shouldBe Some(4.seconds)
      RetryEngine.tryLimit(Some(4.seconds), None) shouldBe Some(4.seconds)
      RetryEngine.tryLimit(None, Some(-5.millis)) shouldBe Some(Duration.Zero)
      RetryEngine.tryLimit(None, None) shouldBe None
    }

    "run a try without a limit the scheduler cannot take, never losing its answer" in {
      val lines = new ConcurrentLinkedQueue[String]()
      val script = new Script(Map("a" -> Seq(ok("fine"))))

      // Akka refuses a delay beyond ~248 days (its default tick) - the call already runs
      val cfg = config(new ConcurrentLinkedQueue[Logged]()).copy[String, Nothing, String](
        attemptTimeout = Some(1000.days),
        log = Some { (line: String) =>
          lines.add(line)
          ()
        }
      )

      runEngine(script, Seq("a"), cfg).futureValue shouldBe "fine"
      lines.asScala.exists(_.contains("could not be scheduled")) shouldBe true
    }

    "make the attempt timeout neither retryable nor a timeout subtype" in {
      val timeout = new OpenAIScalaAttemptTimeoutException("slow", "m", 1.second)

      // a retry around a chain that ended with it would run the chain again while the
      // abandoned calls still run
      Retryable(timeout) shouldBe false
      timeout should not be an[OpenAIScalaClientTimeoutException]
    }
  }

  // --- retryOnFailureOrFailover over slf4j -------------------------------------------------

  private def withCapturedLogs[T](body: => T): (T, Seq[ILoggingEvent]) = {
    val logger = LoggerFactory.getLogger(this.getClass).asInstanceOf[LogbackLogger]
    val appender = new ListAppender[ILoggingEvent]()
    val oldLevel = logger.getLevel
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.DEBUG)

    try {
      val result = body
      (result, appender.list.asScala.toList)
    } finally {
      logger.detachAppender(appender)
      logger.setLevel(oldLevel)
    }
  }

  "retryOnFailureOrFailover" should {

    "try the inputs in order, retry per input, and throw the last error" in {
      val first = retryable
      val last = new OpenAIScalaServerErrorException("last")
      val script = new Script(Map("a" -> Seq(failing(first)), "b" -> Seq(failing(last))))
      val lines = new Lines

      val (error, events) = withCapturedLogs {
        script.call
          .retryOnFailureOrFailover(
            Seq(("a", "A"), ("b", "B")),
            failureMessage = Some("Call failed."),
            log = lines.sink,
            includeExceptionMessage = true
          )
          .failed
          .futureValue
      }

      error shouldBe theSameInstanceAs(last)
      script.calls shouldBe Seq("a", "a", "a", "b", "b", "b")

      lines.all.take(2) shouldBe Seq(
        "A - Call failed. Attempt 1. Error: server down. Retrying...",
        "A - Call failed. Attempt 2. Error: server down. Retrying..."
      )

      events.map(e => (e.getLevel, e.getFormattedMessage)) shouldBe Seq(
        (Level.ERROR, "A - Call failed after retries! Initiating failover to B."),
        (Level.ERROR, "B - Call failed after retries! Initiating failover to N/A."),
        (Level.ERROR, "No more failover inputs to try! Throwing the last error: last")
      )
      // the stack trace comes with the failover line, not with the final one
      events.map(_.getThrowableProxy != null) shouldBe Seq(true, true, false)
    }

    "answer from a later input" in {
      val script = new Script(Map("a" -> Seq(failing(fatal("x"))), "b" -> Seq(ok("fine"))))

      val (result, _) = withCapturedLogs {
        script.call
          .retryOnFailureOrFailover(Seq(("a", "A"), ("b", "B")), log = None)
          .futureValue
      }

      result shouldBe "fine"
    }

    "log a skipped model at Warn without a stack trace and not retry it" in {
      val skip = new OpenAIScalaModelSkippedException("not allowed", "a")
      val script = new Script(Map("a" -> Seq(failing(skip)), "b" -> Seq(ok("fine"))))

      val (result, events) = withCapturedLogs {
        script.call
          .retryOnFailureOrFailover(
            Seq(("a", "A"), ("b", "B")),
            log = None,
            isRetryable = _ => true
          )
          .futureValue
      }

      result shouldBe "fine"
      script.callsOf("a") should have size 1
      events.map(e => (e.getLevel, e.getFormattedMessage)) shouldBe Seq(
        (Level.WARN, "A failed after retries! Initiating failover to B. Reason: not allowed")
      )
      events.head.getThrowableProxy shouldBe null
    }

    "use a custom level function, also for the final line" in {
      val script = new Script(Map("a" -> Seq(failing(fatal("x")))))

      val (_, events) = withCapturedLogs {
        script.call
          .retryOnFailureOrFailover(
            Seq(("a", "A")),
            log = None,
            failoverLogLevel = {
              case e if e.getMessage == "x" => FailoverLogLevel.Info
              case e                        => FailoverLogLevel.default(e)
            }
          )
          .failed
          .futureValue
      }

      events.map(e => (e.getLevel, e.getFormattedMessage)) shouldBe Seq(
        (Level.INFO, "A failed after retries! Initiating failover to N/A. Reason: x"),
        (Level.INFO, "No more failover inputs to try! Throwing the last error: x")
      )
    }

    "throw on no inputs" in {
      val script = new Script(Map.empty)

      an[AssertionError] should be thrownBy
        script.call.retryOnFailureOrFailover(Seq.empty, log = None)
    }
  }
}
