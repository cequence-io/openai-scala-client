package io.cequence.openaiscala.examples

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test of the model ids registered for the 1.3.0 release (September 2026): one
 * short chat completion per (provider, model), printed with usage and wall-clock time. A
 * failing model never stops the run - the error is printed and the next one is tried.
 *
 * Uses a plain `main` with `Await` (not the `Example` trait) so the output is not swallowed by
 * sbt's TrapExit. Requires the respective provider keys: `GOOGLE_API_KEY`, `GROK_API_KEY`,
 * `CEREBRAS_API_KEY`, `GROQ_API_KEY`, `DEEPSEEK_API_KEY`, `MISTRAL_API_KEY`,
 * `ANTHROPIC_API_KEY`. Providers whose key is missing are skipped.
 *
 * Run: `sbt "examples/runMain io.cequence.openaiscala.examples.NewModelsSmokeTest"`
 */
object NewModelsSmokeTest {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val messages = Seq(
    SystemMessage("You are a terse assistant. Answer in at most 12 words."),
    UserMessage(
      "Which planet is the largest in our solar system, and roughly how many Earths fit inside it?"
    )
  )

  private case class Case(
    label: String,
    model: String,
    reasoningEffort: Option[ReasoningEffort] = None,
    expectedToFail: Option[String] = None
  )

  private def provider(
    name: String,
    keyEnv: String
  )(
    make: => OpenAIChatCompletionService
  ): Option[(String, OpenAIChatCompletionService)] =
    if (sys.env.get(keyEnv).exists(_.nonEmpty)) Some(name -> make)
    else {
      println(s"[$name] skipped - $keyEnv not set")
      None
    }

  def main(args: Array[String]): Unit = {
    val plan: Seq[(String, String, Seq[Case])] = Seq(
      (
        "gemini (native adapter)",
        "GOOGLE_API_KEY",
        Seq(
          Case(
            "gemini-3.8-flash / effort low",
            NonOpenAIModelId.gemini_3_8_flash,
            Some(ReasoningEffort.low)
          ),
          Case(
            "gemini-3.8-flash / effort minimal (must downgrade to LOW)",
            NonOpenAIModelId.gemini_3_8_flash,
            Some(ReasoningEffort.minimal)
          ),
          Case("gemma-4-31b-it", NonOpenAIModelId.gemma_4_31b_it)
        )
      ),
      (
        "grok",
        "GROK_API_KEY",
        Seq(
          Case("grok-4.6", NonOpenAIModelId.grok_4_6),
          Case("grok-4.5", NonOpenAIModelId.grok_4_5)
        )
      ),
      (
        "cerebras",
        "CEREBRAS_API_KEY",
        Seq(
          Case("qwen-3.8-27b", NonOpenAIModelId.cerebras_qwen_3_8_27b),
          Case(
            "qwen-3.8-27b / effort none",
            NonOpenAIModelId.cerebras_qwen_3_8_27b,
            Some(ReasoningEffort.none)
          )
        )
      ),
      (
        "groq",
        "GROQ_API_KEY",
        Seq(
          Case("qwen/qwen3.8-27b", NonOpenAIModelId.groq_qwen3_8_27b),
          Case("groq/compound", NonOpenAIModelId.groq_compound)
        )
      ),
      (
        "deepseek",
        "DEEPSEEK_API_KEY",
        Seq(
          Case("deepseek-v4-flash", NonOpenAIModelId.deepseek_v4_flash),
          Case("deepseek-v4-pro", NonOpenAIModelId.deepseek_v4_pro),
          Case("deepseek-flash", NonOpenAIModelId.deepseek_flash),
          Case(
            "deepseek-chat (legacy alias, retirement announced)",
            NonOpenAIModelId.deepseek_chat
          )
        )
      ),
      (
        "mistral",
        "MISTRAL_API_KEY",
        Seq(
          Case("mistral-large-2512", NonOpenAIModelId.mistral_large_2512),
          Case("mistral-medium-2604 (Medium 3.5)", NonOpenAIModelId.mistral_medium_2604),
          Case("mistral-small-2603 (Small 4)", NonOpenAIModelId.mistral_small_2603),
          Case("ministral-8b-2512", NonOpenAIModelId.ministral_8b_2512),
          Case("magistral-medium-latest", NonOpenAIModelId.magistral_medium_latest)
        )
      ),
      (
        "anthropic",
        "ANTHROPIC_API_KEY",
        Seq(
          Case(
            "claude-mythos-5-1 (invite-only)",
            NonOpenAIModelId.claude_mythos_5_1,
            expectedToFail = Some("invite-only")
          )
        )
      )
    )

    val services: Map[String, OpenAIChatCompletionService] = Map(
      "gemini (native adapter)" -> (() => GeminiServiceFactory.asOpenAI()),
      "grok" -> (() => OpenAIChatCompletionServiceFactory(ChatProviderSettings.grok)),
      "cerebras" -> (() => OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)),
      "groq" -> (() => OpenAIChatCompletionServiceFactory(ChatProviderSettings.groq)),
      "deepseek" -> (() => OpenAIChatCompletionServiceFactory(ChatProviderSettings.deepseek)),
      "mistral" -> (() => OpenAIChatCompletionServiceFactory(ChatProviderSettings.mistral)),
      "anthropic" -> (() => AnthropicServiceFactory.asOpenAI())
    ).flatMap { case (name, make) =>
      val keyEnv = plan.find(_._1 == name).map(_._2).getOrElse("")
      provider(name, keyEnv)(make())
    }

    var ok = 0
    var failed = 0
    var expectedFailures = 0

    plan.foreach { case (providerName, _, cases) =>
      services.get(providerName).foreach { service =>
        cases.foreach { c =>
          val settings = CreateChatCompletionSettings(
            model = c.model,
            max_tokens = Some(256),
            reasoning_effort = c.reasoningEffort
          )
          val start = System.currentTimeMillis()
          val result =
            Try(Await.result(service.createChatCompletion(messages, settings), 120.seconds))
          val ms = System.currentTimeMillis() - start

          result match {
            case Success(response) =>
              ok += 1
              val text = response.contentHead.replaceAll("\\s+", " ").take(160)
              println(
                s"[OK   ] $providerName | ${c.label} | ${ms}ms | usage=${response.usage} | $text"
              )
            case Failure(e) if c.expectedToFail.isDefined =>
              expectedFailures += 1
              println(
                s"[EXPECTED FAIL] $providerName | ${c.label} (${c.expectedToFail.get}) | ${ms}ms | ${e.getClass.getSimpleName}: ${e.getMessage
                    .take(200)}"
              )
            case Failure(e) =>
              failed += 1
              println(
                s"[FAIL ] $providerName | ${c.label} | ${ms}ms | ${e.getClass.getSimpleName}: ${e.getMessage
                    .take(300)}"
              )
          }
        }
      }
    }

    services.values.foreach(_.close())
    println(s"\nSummary: ok=$ok failed=$failed expectedFailures=$expectedFailures")
    if (failed > 0) System.exit(1)
  }
}
