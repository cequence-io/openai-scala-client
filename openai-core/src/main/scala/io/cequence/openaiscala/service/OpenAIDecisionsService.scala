package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  Decision,
  DecisionInput,
  DecisionQuestion
}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * The Decisions API (public beta since 2026-10-06): typed answers about text and inline
 * images, about 10x faster than asking a model through the Responses API - the probability
 * that a condition holds (`predicate`), one of fixed options (`choice`) or a score against
 * ordered levels (`score`), each with its distribution. `gpt-6-luna` is the only model; input
 * costs $0.10 per 1M tokens, output nothing.
 *
 * Live facts (2026-10-07): at most 200 questions, 255 choices and 10 levels per question;
 * names must be unique; images only as base64 data URLs; an input over the token limit (300k
 * tokens passed, 1.2M did not) is a 400 "Decision input exceeds the token limit."; a question
 * the model declines is answered with a
 * [[io.cequence.openaiscala.domain.decisions.DecisionAnswer.Refusal]], the others normally;
 * ~200-300 ms for a few questions, ~500 ms for 40.
 *
 * A service of its own, which the full [[OpenAIService]] extends - so code written against it
 * switches hosts by construction alone: `OpenAIServiceFactory()` answers on OpenAI, and the
 * typesafe-client module's `TypeSafeServiceFactory.asOpenAIDecisions(provider)` on a System
 * One host (TypeSafe's Jev, Liquid's d1, Perplexity's decider, OpenRouter, llama.cpp), the
 * questions translated. The other way round, the decision routines of the typesafe-client
 * module (typed decisions, re-ranking, the `json_schema` adapter, a decision-model guardrail)
 * run on OpenAI through `DecisionProviderSettings.openAI`.
 *
 * @see
 *   <a href="https://developers.openai.com/api/docs/guides/decisions">OpenAI Doc</a>
 */
trait OpenAIDecisionsService extends CloseableService {

  /**
   * Asks the questions about the input in one request.
   *
   * @param settings
   *   the model (none: the service's default - `gpt-6-luna` on OpenAI) and OpenAI's safety
   *   identifier
   * @return
   *   the answers, in the questions' order, each carrying its question's name
   */
  def createDecision(
    input: DecisionInput,
    questions: Seq[DecisionQuestion],
    settings: CreateDecisionSettings = CreateDecisionSettings()
  ): Future[Decision]
}
