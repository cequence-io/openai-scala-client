package io.cequence.openaiscala.examples

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.ChatCompletionTool.SkillTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService

import scala.concurrent.Future

/**
 * An uploaded OpenAI skill (`POST /v1/skills`, a folder with a `SKILL.md`) through the
 * provider-neutral `SkillTool` on the typed stream: the adapter routes the request through the
 * Responses API and loads the skill into the hosted shell tool's `container_auto` environment
 * (GPT-6); the container work streams as server-side `shell` tool calls and results before the
 * answer.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `OPENAI_SKILL_ID` (the id of an uploaded skill).
 */
object CreateChatToolCompletionStreamedWithSkillTool
    extends ExampleBase[OpenAIStreamedService] {

  override val service: OpenAIStreamedService = OpenAIServiceFactory.withStreaming()

  private val skillId = sys.env.getOrElse(
    "OPENAI_SKILL_ID",
    throw new IllegalStateException("OPENAI_SKILL_ID environment variable expected")
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletionStreamed(
        messages = Seq(UserMessage("Greet the Scala developer named Peter using your skill.")),
        tools = Seq(SkillTool(skillId, version = Some("latest"))),
        settings = CreateChatCompletionSettings(model = ModelId.gpt_6_astra)
      )
      .runWith(Sink.foreach[ChatChunk](chunk => println(ChatChunkPrinter.describe(chunk))))
}
