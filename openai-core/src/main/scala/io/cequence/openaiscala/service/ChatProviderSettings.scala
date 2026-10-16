package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ProviderSettings

object ChatProviderSettings {

  val cerebras = ProviderSettings("https://api.cerebras.ai/v1/", "CEREBRAS_API_KEY")
  val groq = ProviderSettings("https://api.groq.com/openai/v1/", "GROQ_API_KEY")
  val fireworks =
    ProviderSettings("https://api.fireworks.ai/inference/v1/", "FIREWORKS_API_KEY")
  val mistral = ProviderSettings("https://api.mistral.ai/v1/", "MISTRAL_API_KEY")
  val octoML = ProviderSettings("https://text.octoai.run/v1/", "OCTOAI_TOKEN")
  val togetherAI =
    ProviderSettings("https://api.together.xyz/v1/", "TOGETHERAI_API_KEY")
  val grok = ProviderSettings("https://api.x.ai/v1/", "GROK_API_KEY")
  val deepseek = ProviderSettings("https://api.deepseek.com/", "DEEPSEEK_API_KEY")
  val deepseekBeta = ProviderSettings("https://api.deepseek.com/beta/", "DEEPSEEK_API_KEY")
  val sonar = ProviderSettings("https://api.perplexity.ai/", "SONAR_API_KEY")
  val geminiCoreURL = "https://generativelanguage.googleapis.com/v1beta/"
  val gemini = ProviderSettings(s"${geminiCoreURL}openai/", "GOOGLE_API_KEY")
  val novita = ProviderSettings("https://api.novita.ai/v3/openai/", "NOVITA_API_KEY")
  // MiniMax direct API, OpenAI-compatible endpoints (requires MINIMAX_API_KEY).
  // `minimax` is the global endpoint, `minimaxChina` the China (minimaxi.com) endpoint.
  val minimax = ProviderSettings("https://api.minimax.io/v1/", "MINIMAX_API_KEY")
  // Liquid AI's OpenAI-compatible surface (live 2026-09-30: it answers in OpenAI's format but
  // lists no chat models for a free-tier key - Liquid's hosted model is the decision model d1,
  // see the typesafe-client's TypeSafeServiceFactory.liquid)
  val liquid = ProviderSettings("https://api.liquid.ai/openai/v1/", "LIQUID_API_KEY")
  val minimaxChina = ProviderSettings("https://api.minimaxi.com/v1/", "MINIMAX_API_KEY")
  // Meta Model API (dev.meta.ai) - Muse Wonder and Muse Spark over OpenAI's chat completions;
  // the key variable is the one Meta's docs use
  val meta = ProviderSettings("https://api.meta.ai/v1/", "MODEL_API_KEY")
  // OpenRouter's OpenAI-compatible chat API - `provider/model` ids, e.g. `meta/muse-wonder`
  val openRouter = ProviderSettings("https://openrouter.ai/api/v1/", "OPENROUTER_API_KEY")
  // Vercel's AI Gateway chat API - `provider/model` ids, e.g. `meta/muse-spark-open`; a key of
  // the gateway (`vck_...`), not a Vercel account token
  val vercel = ProviderSettings("https://ai-gateway.vercel.sh/v1/", "AI_GATEWAY_API_KEY")
}
