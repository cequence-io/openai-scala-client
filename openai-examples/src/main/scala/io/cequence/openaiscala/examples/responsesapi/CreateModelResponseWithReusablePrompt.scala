package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  Prompt
}
import io.cequence.openaiscala.examples.Example

object CreateModelResponseWithReusablePrompt extends Example {

  override def run: Future[Unit] = {
    val prompt = Prompt(
      id = "pmpt_6903e5c4aaa881909210e670508669a6091c1b711bd77e6d",
      variables = Map(
        "customer_name" -> "John Doe",
        "product_name" -> "Premium Widget",
        "order_id" -> "12345"
      ),
      version = Some("1")
    )

    val settings = CreateModelResponseSettings(
      model = ModelId.gpt_5_mini,
      prompt = Some(prompt)
    )

    service
      .createModelResponse(
        Inputs.Text(""), // Input can be empty when using a prompt
        settings
      )
      .map { response =>
        println(s"Response ID: ${response.id}")
        println(s"Model: ${response.model}")
        println(s"Status: ${response.status}")
        println(s"\nPrompt used: ${response.prompt.map(_.id).getOrElse("N/A")}")
        println(s"\nOutput:")
        println(response.outputText.getOrElse("N/A"))

        response.usage.foreach { usage =>
          println(s"\nUsage:")
          println(s"  Input tokens: ${usage.inputTokens}")
          println(s"  Output tokens: ${usage.outputTokens}")
          println(s"  Total tokens: ${usage.totalTokens}")
        }
      }
  }
}
