package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ReasoningConfig
}
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId}
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Head-to-head latency benchmark: `gpt-5.5` served by Amazon Bedrock (`bedrock-mantle`, the
 * `openai/v1` path) vs. vanilla OpenAI, across several scenarios of increasing difficulty and
 * reasoning effort - so you can see how the two providers compare as the model "thinks" more.
 *
 * Requires:
 *   - Bedrock: `AWS_BEARER_TOKEN_BEDROCK` + `AWS_BEDROCK_REGION` (e.g. "us-east-2")
 *   - OpenAI: `OPENAI_SCALA_CLIENT_API_KEY`
 *
 * For each scenario both providers get an identical prompt and settings (same reasoning
 * effort, store = false). Trials alternate between providers to spread out transient
 * network/load effects. Per-trial we report latency, output tokens, reasoning tokens, and
 * tokens/sec, plus a per-scenario average summary.
 *
 * NOTE: results depend heavily on your network location relative to the AWS region vs. OpenAI,
 * time-of-day load, and reasoning effort - treat the numbers as indicative, not authoritative.
 *
 * Standalone `main` (rather than the `Example` trait) so the output isn't swallowed by sbt's
 * TrapExit and so it can hold two services at once.
 *
 * ==Observed findings==
 * Sample run from a machine close to `us-east-2` (3 trials/scenario, June 2026,
 * non-streaming):
 *
 * {{{
 * Scenario (effort)             Provider   avg latency   avg reasoning   avg throughput
 * simple (low)                  bedrock         3.35s          0 tok        58.9 tok/s
 *                               openai          5.75s          4 tok        36.4 tok/s
 * multi-step logic (medium)     bedrock         5.41s        205 tok        82.2 tok/s
 *                               openai          7.85s        178 tok        59.0 tok/s
 * hard puzzle / 12-coin (high)  bedrock        39.83s       2933 tok       105.8 tok/s
 *                               openai         68.20s       3445 tok        68.9 tok/s
 * }}}
 *
 *   - Throughput is the clean, apples-to-apples signal: Bedrock was ~1.5-1.6x faster in
 *     tokens/sec at every effort level, consistently across all trials.
 *   - Raw latency is confounded by output length - each provider emits a different number of
 *     reasoning+output tokens per run, so a "faster" wall-clock time sometimes just means
 *     fewer tokens were generated. That is why throughput is reported alongside latency.
 *   - Both engines generate progressively more reasoning tokens as effort rises (~0 -> ~190 ->
 *     ~3000), so high-effort calls are slow in absolute terms on both providers.
 */
object BedrockVsOpenAIGpt55Benchmark {

  private val trials = 3

  private case class Scenario(
    name: String,
    effort: ReasoningEffort,
    prompt: String
  )

  // Increasing difficulty / reasoning effort.
  private val scenarios = Seq(
    Scenario(
      name = "simple (low)",
      effort = ReasoningEffort.low,
      prompt = "Explain how a hash map works and how collisions are handled. About 150 words."
    ),
    Scenario(
      name = "multi-step logic (medium)",
      effort = ReasoningEffort.medium,
      prompt = "Alice, Bob, and Carol have distinct positive integer ages summing to 60. " +
        "Alice's age is twice Bob's minus 5, and Carol is 4 years older than Alice. " +
        "Find each age, and show your reasoning step by step."
    ),
    Scenario(
      name = "hard puzzle (high)",
      effort = ReasoningEffort.high,
      prompt =
        "You have 12 identical-looking coins; exactly one is counterfeit and is either " +
          "heavier or lighter than the rest. Using a balance scale at most 3 times, give a " +
          "strategy that always identifies the counterfeit coin AND whether it is heavy or " +
          "light. Enumerate every weighing and every branch of outcomes explicitly."
    )
  )

  private case class Sample(
    latencyMs: Long,
    outputTokens: Int,
    reasoningTokens: Int
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: scala.concurrent.ExecutionContext = system.dispatcher

    val bedrock = OpenAIServiceFactory.forBedrockMantle(isOpenAIModel = true)
    val openai = OpenAIServiceFactory()

    try {
      // Single warm-up to prime connection pools / TLS so it isn't charged to scenario #1.
      println("Warming up ...")
      timeOne(bedrock, NonOpenAIModelId.bedrock_openai_gpt_5_5, scenarios.head)
      timeOne(openai, ModelId.gpt_5_5, scenarios.head)

      scenarios.foreach { scenario =>
        println("\n========================================================")
        println(s"Scenario: ${scenario.name}   (reasoning effort = ${scenario.effort})")
        println("========================================================")
        println(
          f"${"trial"}%-6s${"provider"}%-10s${"latency"}%10s${"out"}%7s${"reason"}%8s${"tok/s"}%9s"
        )

        val bedrockSamples = mutable.ListBuffer.empty[Sample]
        val openaiSamples = mutable.ListBuffer.empty[Sample]

        (1 to trials).foreach { i =>
          val b = timeOne(bedrock, NonOpenAIModelId.bedrock_openai_gpt_5_5, scenario)
          bedrockSamples += b
          printRow(i, "bedrock", b)

          val o = timeOne(openai, ModelId.gpt_5_5, scenario)
          openaiSamples += o
          printRow(i, "openai", o)
        }

        println("  --- averages ---")
        printSummary("bedrock", bedrockSamples.toList)
        printSummary("openai", openaiSamples.toList)
      }
    } finally {
      bedrock.close()
      openai.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def timeOne(
    service: OpenAIService,
    model: String,
    scenario: Scenario
  ): Sample = {
    val settings = CreateModelResponseSettings(
      model = model,
      reasoning = Some(ReasoningConfig(effort = Some(scenario.effort))),
      store = Some(false)
    )

    val start = System.nanoTime()
    val response = Await.result(
      service.createModelResponse(Inputs.Text(scenario.prompt), settings),
      5.minutes
    )
    val latencyMs = (System.nanoTime() - start) / 1000000

    Sample(
      latencyMs = latencyMs,
      outputTokens = response.usage.map(_.outputTokens).getOrElse(0),
      reasoningTokens =
        response.usage.flatMap(_.outputTokensDetails).map(_.reasoningTokens).getOrElse(0)
    )
  }

  private def tokPerSec(s: Sample): Double =
    if (s.latencyMs == 0) 0.0 else s.outputTokens * 1000.0 / s.latencyMs

  private def printRow(
    trial: Int,
    provider: String,
    s: Sample
  ): Unit =
    println(
      f"$trial%-6d$provider%-10s${s.latencyMs / 1000.0}%9.2fs${s.outputTokens}%7d${s.reasoningTokens}%8d${tokPerSec(s)}%9.1f"
    )

  private def printSummary(
    provider: String,
    samples: List[Sample]
  ): Unit = {
    val avgMs = samples.map(_.latencyMs).sum.toDouble / samples.size
    val avgReason = samples.map(_.reasoningTokens).sum.toDouble / samples.size
    val avgTokS = samples.map(tokPerSec).sum / samples.size
    println(
      f"  $provider%-10s avg latency ${avgMs / 1000.0}%6.2fs   avg reasoning ${avgReason}%6.0f tok   avg ${avgTokS}%5.1f tok/s"
    )
  }
}
