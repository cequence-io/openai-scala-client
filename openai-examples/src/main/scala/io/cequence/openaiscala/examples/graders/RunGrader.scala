package io.cequence.openaiscala.examples.graders

import io.cequence.openaiscala.domain.{ChatRole, ModelId}
import io.cequence.openaiscala.domain.graders._
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

object RunGrader extends Example {

  // Only the following models are allowed:
  // - gpt-4o-2024-08-06, gpt-4o-mini-2024-07-18,
  // - gpt-4.1-2025-04-14, gpt-4.1-mini-2025-04-14, gpt-4.1-nano-2025-04-14,
  // - o1-2024-12-17, o3-mini-2025-01-31, o4-mini-2025-04-16, o3-2025-04-16

  // Try gpt-4o-mini first as it may have fewer restrictions
  val gradingModel = ModelId.gpt_4o_mini_2024_07_18

  def run: Future[Unit] = {
    // Define a ScoreModelGrader to evaluate the quality of a model's response
    val grader = ScoreModelGrader(
      input = Seq(
        GraderModelInput(
          content = GraderInputContent.TextString(
            "Rate the helpfulness of the following response on a scale from 0 to 1:"
          ),
          role = ChatRole.System
        ),
        GraderModelInput(
          content = GraderInputContent.InputText("{{item.question}}"),
          role = ChatRole.User
        ),
        GraderModelInput(
          content = GraderInputContent.OutputText("{{sample.output_json}}"),
          role = ChatRole.Assistant
        )
      ),
      model = gradingModel,
      name = "helpfulness_scorer",
      range = Seq(0.0, 1.0),
      samplingParams = Some(
        SamplingParams(
//          temperature = Some(0.3),
          maxCompletionsTokens = Some(100)
        )
      )
    )

    // Sample model output to be evaluated
    val modelSample = """{"answer": "The capital of France is Paris."}"""

    // Dataset item containing the question
    val item = Map[String, Any](
      "question" -> "What is the capital of France?"
    )

    // Run the grader
    service
      .runGrader(
        grader = grader,
        modelSample = modelSample,
        item = item
      )
      .map { result =>
        println(s"Grader evaluation result: $result")
      }
  }
}
