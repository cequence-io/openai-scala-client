package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  BaseMessage,
  NonOpenAIModelId,
  SystemMessage,
  TextContent,
  UserSeqMessage,
  VLMContent
}
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import io.cequence.openaiscala.service.OpenAIServiceFactory

import java.io.File
import java.nio.file.Files
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Verifies that file/document content (`VLMContent` -> FileContent / ImageURLContent) is
 * passed through the Responses chat-completion adapter to `openai.gpt-5.5` on Amazon Bedrock
 * (bedrock-mantle) and is actually seen by the model.
 *
 * Drives a PNG (image input) and a PDF (document input), each carrying known marker strings,
 * and asserts the model reads them back.
 *
 * Requires `AWS_BEARER_TOKEN_BEDROCK` + `AWS_BEDROCK_REGION`, plus probe files at
 * `VLM_PNG_PATH` and `VLM_PDF_PATH`.
 */
object BedrockMantleVLMContentSmokeTest {

  private val systemPrompt = SystemMessage(
    "Each attached file is preceded by a label of the form '[file: NAME]'. " +
      "Read the file content and answer using exactly what you see."
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: scala.concurrent.ExecutionContext = system.dispatcher

    val service = OpenAIServiceFactory.forBedrockMantle(isOpenAIModel = true)
    val chatService = OpenAIResponsesChatCompletionService(service)

    def ask(
      prompt: String,
      fileEnv: String
    ): String = {
      val bytes = Files.readAllBytes(new File(sys.env(fileEnv)).toPath)
      val fileName = new File(sys.env(fileEnv)).getName
      val content = TextContent(prompt) +: VLMContent.of(bytes, fileName)
      val messages: Seq[BaseMessage] = Seq(systemPrompt, UserSeqMessage(content))
      Await
        .result(
          chatService.createChatCompletion(
            messages = messages,
            settings = CreateChatCompletionSettings(
              model = NonOpenAIModelId.bedrock_openai_gpt_5_5,
              // gpt-5.5 on bedrock-mantle returns an empty completion (0 output tokens) for
              // file/PDF input under its DEFAULT reasoning effort; an explicit effort fixes it.
              reasoning_effort = Some(ReasoningEffort.low)
            )
          ),
          4.minutes
        )
        .contentHead
    }

    try {
      println("=== PNG (image input) ===")
      val pngAnswer = ask("What text is shown in this image?", "VLM_PNG_PATH")
      println(s"Answer: $pngAnswer")
      require(
        pngAnswer.toUpperCase.replaceAll("\\s", "").contains("BEDROCK42"),
        s"image content NOT read back (expected 'BEDROCK 42'): $pngAnswer"
      )
      println("OK: image content reached gpt-5.5 and was read.\n")

      println("=== PDF (document input) ===")
      val pdfAnswer =
        ask(
          "What is the secret codename and the total budget in this document?",
          "VLM_PDF_PATH"
        )
      println(s"Answer: $pdfAnswer")
      require(
        pdfAnswer.toUpperCase.contains("BEDROCK-OWL") && pdfAnswer.contains("4242"),
        s"PDF content NOT read back (expected 'BEDROCK-OWL' + '4242'): $pdfAnswer"
      )
      println("OK: PDF document content reached gpt-5.5 and was read.")
    } finally {
      chatService.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
