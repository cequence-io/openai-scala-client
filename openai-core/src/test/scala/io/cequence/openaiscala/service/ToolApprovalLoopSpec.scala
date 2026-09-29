package io.cequence.openaiscala.service

import akka.NotUsed
import akka.actor.ActorSystem
import akka.pattern.{after => delayed}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.{
  AssembledChatCompletion,
  ChatChunk,
  ToolApprovalDecision,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * [[ToolApprovalLoop]] (behind `createChatToolCompletionStreamedWithApprovals`) over a fake
 * provider whose rounds are picked by the run the decisions resume.
 */
class ToolApprovalLoopSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("tool-approval-loop-spec")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(20, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val settings = CreateChatCompletionSettings("gpt-test")

  private def request(
    id: String,
    run: String
  ) =
    ToolApprovalRequest(id, s"tool_$id", "{}", Some("deepwiki"), run, Json.obj("id" -> id))

  private def usage(
    prompt: Int,
    completion: Int
  ) = Usage(UsageInfo(prompt, prompt + completion, Some(completion)))

  private val paused = Finish(FinishReason.approval_required, Some("approval_required"))
  private val stopped = Finish(FinishReason.stop, Some("completed"))

  private val askA = request("a", "run1")
  private val askB = request("b", "run1")
  private val askC = request("c", "run2")

  // run1 pauses on a + b, its resume pauses on c, whose resume answers
  private val round0 = List(
    Start("run1", "m"),
    Thinking("plan"),
    ToolCallStart(0, "c0", "list", serverSide = true),
    ToolCall(0, "c0", "list", "{}", serverSide = true),
    ToolResult("c0", "list", Json.obj(), Some("tools"), isError = false),
    askA,
    askB,
    paused,
    usage(10, 1)
  )
  private val round1 = List(
    Start("run2", "m"),
    ToolCallStart(0, "a", "tool_a", serverSide = true),
    ToolCallDelta(0, "{}"),
    ToolCall(0, "a", "tool_a", "{}", serverSide = true),
    ToolCallStart(1, "b", "tool_b", serverSide = true),
    ToolCall(1, "b", "tool_b", "{}", serverSide = true),
    askC,
    paused,
    usage(20, 2)
  )
  private val round2 = List(
    Start("run3", "m"),
    ToolCallStart(0, "c", "tool_c", serverSide = true),
    ToolCall(0, "c", "tool_c", "{}", serverSide = true),
    usage(30, 3), // before its Finish - the joined stream ends with Finish, then the usage
    Text("done"),
    stopped
  )

  // the fake provider: a round is picked by the run its decisions resume (None = a fresh run)
  private final class FakeRuns(rounds: (Option[String], Source[ChatChunk, NotUsed])*) {
    private val byRun = rounds.toMap
    @volatile var sent: Vector[CreateChatCompletionSettings] = Vector.empty

    def stream(roundSettings: CreateChatCompletionSettings): Source[ChatChunk, NotUsed] = {
      synchronized { sent :+= roundSettings }
      byRun(roundSettings.toolApprovalDecisions.headOption.map(_.request.runId))
    }
  }

  private def runs(rounds: (Option[String], List[ChatChunk])*) =
    new FakeRuns(rounds.map { case (run, chunks) => run -> Source(chunks) }: _*)

  private def threeRounds =
    runs(None -> round0, Some("run1") -> round1, Some("run2") -> round2)

  private final class Decider(
    answer: ToolApprovalRequest => Future[ToolApprovalDecision] = r =>
      Future.successful(r.approve)
  ) {
    @volatile var asked: Vector[ToolApprovalRequest] = Vector.empty

    val decide: ToolApprovalRequest => Future[ToolApprovalDecision] = { r =>
      synchronized { asked :+= r }
      answer(r)
    }
  }

  private def joined(
    fake: FakeRuns,
    decider: Decider,
    maxRounds: Int = ToolApprovalLoop.DefaultMaxRounds,
    callSettings: CreateChatCompletionSettings = settings
  ): Source[ChatChunk, NotUsed] =
    ToolApprovalLoop(callSettings, decider.decide, maxRounds)(fake.stream)

  private def run(source: Source[ChatChunk, NotUsed]): Seq[ChatChunk] =
    source.runWith(Sink.seq).futureValue

  private def assemble(chunks: Seq[ChatChunk]): AssembledChatCompletion =
    chunks.foldLeft(AssembledChatCompletion.empty)(_ add _)

  "the tool approval loop" should {

    "pass a run that never pauses through, ending with its Finish and usage" in {
      val fake = runs(None -> List(Start("r", "m"), usage(5, 1), Text("hi"), stopped))
      val decider = new Decider()

      run(joined(fake, decider)) shouldBe
        Seq(Start("r", "m"), Text("hi"), stopped, usage(5, 1))
      decider.asked shouldBe empty
      fake.sent shouldBe Seq(settings)
    }

    "answer every pause through the callback and join the rounds into one stream" in {
      val fake = threeRounds
      val decider = new Decider(r =>
        Future.successful(if (r.requestId == "b") r.deny("no") else r.approve)
      )

      val chunks = run(joined(fake, decider))

      chunks shouldBe Seq(
        Start("run1", "m"),
        Thinking("plan"),
        ToolCallStart(0, "c0", "list", serverSide = true),
        ToolCall(0, "c0", "list", "{}", serverSide = true),
        ToolResult("c0", "list", Json.obj(), Some("tools"), isError = false),
        // the ordinals continue across rounds
        ToolCallStart(1, "a", "tool_a", serverSide = true),
        ToolCallDelta(1, "{}"),
        ToolCall(1, "a", "tool_a", "{}", serverSide = true),
        ToolCallStart(2, "b", "tool_b", serverSide = true),
        ToolCall(2, "b", "tool_b", "{}", serverSide = true),
        ToolCallStart(3, "c", "tool_c", serverSide = true),
        ToolCall(3, "c", "tool_c", "{}", serverSide = true),
        Text("done"),
        stopped,
        Usage(UsageInfo(60, 66, Some(6)))
      )

      decider.asked shouldBe Seq(askA, askB, askC)
      fake.sent shouldBe Seq(
        settings,
        settings.setToolApprovalDecisions(Seq(askA.approve, askB.deny("no"))),
        settings.setToolApprovalDecisions(Seq(askC.approve))
      )

      val assembled = assemble(chunks)
      assembled.id shouldBe Some("run1")
      assembled.text shouldBe "done"
      assembled.toolCalls.map(_.callId) shouldBe Seq("c0", "a", "b", "c")
      assembled.toolApprovalRequests shouldBe empty
      assembled.awaitingApproval shouldBe false
      assembled.finishReason shouldBe Some(FinishReason.stop)
    }

    "ask about one request at a time, in order" in {
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)
      val decider = new Decider(r => {
        val now = inFlight.incrementAndGet()
        maxInFlight.synchronized(maxInFlight.set(math.max(maxInFlight.get, now)))
        delayed(50.millis, system.scheduler) {
          inFlight.decrementAndGet()
          Future.successful(r.approve)
        }
      })

      run(joined(threeRounds, decider)).last shouldBe Usage(UsageInfo(60, 66, Some(6)))
      decider.asked shouldBe Seq(askA, askB, askC)
      maxInFlight.get shouldBe 1
    }

    "ask only once the paused round completed - never for a failed or cancelled one" in {
      val gate = Promise[Unit]()
      val gated = new FakeRuns(
        None -> (Source(round0) ++
          Source.futureSource(gate.future.map(_ => Source.empty[ChatChunk]))),
        Some("run1") -> Source(List(Text("done"), stopped))
      )
      val decider = new Decider()

      val result = joined(gated, decider).runWith(Sink.seq)
      Thread.sleep(200)
      decider.asked shouldBe empty // everything streamed, but the round is still open
      gate.success(())
      result.futureValue.collect { case t: Text => t } shouldBe Seq(Text("done"))
      decider.asked shouldBe Seq(askA, askB)

      val boom = new RuntimeException("stream broke")
      val failing = new FakeRuns(None -> (Source(round0) ++ Source.failed[ChatChunk](boom)))
      val failDecider = new Decider()
      joined(failing, failDecider).runWith(Sink.ignore).failed.futureValue shouldBe boom

      val cancelDecider = new Decider()
      val cancelled = threeRounds
      run(joined(cancelled, cancelDecider).take(2)) shouldBe
        Seq(Start("run1", "m"), Thinking("plan"))
      Thread.sleep(200)
      failDecider.asked shouldBe empty
      cancelDecider.asked shouldBe empty
      cancelled.sent should have size 1
    }

    "send nothing before it is materialized, and run a fresh loop per materialization" in {
      val fake = threeRounds
      val decider = new Decider()
      val source = joined(fake, decider)

      Thread.sleep(100)
      fake.sent shouldBe empty

      run(source) shouldBe run(source)
      fake.sent should have size 6
      decider.asked shouldBe Seq(askA, askB, askC, askA, askB, askC)
    }

    "end paused - the pending requests passed through - after maxRounds resumes" in {
      val decider = new Decider()
      val chunks = run(joined(threeRounds, decider, maxRounds = 1))

      decider.asked shouldBe Seq(askA, askB)
      chunks.takeRight(3) shouldBe Seq(askC, paused, Usage(UsageInfo(30, 33, Some(3))))
      val assembled = assemble(chunks)
      assembled.awaitingApproval shouldBe true
      assembled.toolApprovalRequests shouldBe Seq(askC) // only the pending one
      assembled.finishReason shouldBe Some(FinishReason.approval_required)

      // no resume at all: the plain stream
      val plain = new Decider()
      run(joined(threeRounds, plain, maxRounds = 0)).takeRight(4) shouldBe
        Seq(askA, askB, paused, usage(10, 1))
      plain.asked shouldBe empty

      an[IllegalArgumentException] should be thrownBy
        joined(threeRounds, plain, maxRounds = -1)
    }

    "end paused when the pause comes with client-side function calls to run" in {
      val clientCall = ToolCall(0, "f", "get_time", "{}", serverSide = false)
      val decider = new Decider()
      val chunks =
        run(joined(runs(None -> List(Start("run1", "m"), clientCall, askA, paused)), decider))

      chunks shouldBe Seq(Start("run1", "m"), clientCall, askA, paused)
      decider.asked shouldBe empty
    }

    "bind each decision to the request as the provider sent it" in {
      val fake = threeRounds
      // e.g. a decision rebuilt from a UI round trip that lost the provider's raw JSON
      val decider = new Decider(r =>
        Future.successful(ToolApprovalDecision(r.copy(raw = Json.obj()), approve = true))
      )
      run(joined(fake, decider))

      fake.sent(1).toolApprovalDecisions.map(_.request) shouldBe Seq(askA, askB)
      fake.sent(2).toolApprovalDecisions.map(_.request) shouldBe Seq(askC)
    }

    "fail with the callback's failure, a thrown exception, or a decision for another call" in {
      val boom = new RuntimeException("no human around")

      def failure(answer: ToolApprovalRequest => Future[ToolApprovalDecision]): Throwable =
        joined(threeRounds, new Decider(answer)).runWith(Sink.ignore).failed.futureValue

      failure(_ => Future.failed(boom)) shouldBe boom
      failure(_ => throw boom) shouldBe boom
      failure(_ => Future.successful(askC.approve)).getMessage should
        include("answered request 'a' with a decision for 'c'")
    }

    "pass a restart of the first round through, but fail on one of a resumed round" in {
      val retried = runs(
        None -> List(
          Start("run0", "m"),
          Text("partial"),
          request("x", "run0"),
          Retry(2),
          Start("run1", "m"),
          askA,
          paused
        ),
        Some("run1") -> List(Text("done"), stopped)
      )
      val decider = new Decider()
      val chunks = run(joined(retried, decider))

      decider.asked shouldBe Seq(askA) // the void attempt's request is dropped
      chunks should contain(Retry(2))
      assemble(chunks).text shouldBe "done"

      val retriedResume = runs(
        None -> round0,
        Some("run1") -> List(Text("par"), Retry(2), Text("done"), stopped)
      )
      joined(retriedResume, new Decider())
        .runWith(Sink.ignore)
        .failed
        .futureValue
        .getMessage should include("resumed tool-approval round was restarted")
    }

    "resume with the caller's own decisions first, then answer the next pause" in {
      val fake = threeRounds
      val decider = new Decider()
      val resuming = settings.setToolApprovalDecisions(Seq(askA.approve, askB.approve))

      val chunks = run(joined(fake, decider, callSettings = resuming))

      fake.sent shouldBe Seq(resuming, settings.setToolApprovalDecisions(Seq(askC.approve)))
      decider.asked shouldBe Seq(askC)
      chunks.head shouldBe Start("run2", "m")
      chunks.collect { case s: Start => s } should have size 1
    }

    "hold Done back to the very end" in {
      val fake = runs(
        None -> List(Start("run1", "m"), askA, paused, Done),
        Some("run1") -> List(Text("done"), stopped, Done)
      )
      run(joined(fake, new Decider())) shouldBe
        Seq(Start("run1", "m"), Text("done"), stopped, Done)
    }
  }
}
