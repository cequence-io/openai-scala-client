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
}
