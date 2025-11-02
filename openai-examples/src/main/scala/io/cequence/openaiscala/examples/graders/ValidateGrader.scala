package io.cequence.openaiscala.examples.graders

import io.cequence.openaiscala.domain.{ChatRole, ModelId}
import io.cequence.openaiscala.domain.graders._
import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.openaiscala.examples.Example

import scala.concurrent.Future

object ValidateGrader extends Example {

  private val graders = Seq(
    StringGrader(
      input = "{{sample.output_json}}",
      name = "exact_match_validator",
      operation = StringCheckOperation.eq,
      reference = "{{item.expected_answer}}"
    ),
    ScoreModelGrader(
      input = Seq(
        GraderModelInput(
          role = ChatRole.User,
          content = GraderInputContent.TextString(
            "Score how close the reference answer is to the model answer. Score 1.0 if they are the same and 0.0 if they are different." +
              " Return just a floating point score\n\n" +
              " Reference answer: {{item.label}}\n\n" +
              " Model answer: {{sample.output_text}}"
          )
        )
      ),
      model = ModelId.o4_mini_2025_04_16,
      name = "Example score model grader",
      samplingParams = Some(
        SamplingParams(
          temperature = Some(1.0),
          topP = Some(1.0),
          seed = Some(42),
          maxCompletionsTokens = Some(32768),
          reasoningEffort = Some(ReasoningEffort.medium)
        )
      )
    ),
    LabelModelGrader(
      input = Seq(
        GraderModelInput(
          role = ChatRole.System,
          content = GraderInputContent.InputText(
            "Classify the sentiment of the following statement as one of positive, neutral, or negative"
          )
        ),
        GraderModelInput(
          role = ChatRole.User,
          content = GraderInputContent.InputText(
            "Statement: {{item.response}}"
          )
        )
      ),
      labels = Seq("positive", "neutral", "negative"),
      model = ModelId.gpt_4o_2024_08_06,
      name = "First label grader",
      passingLabels = Seq("positive")
    ),
    PythonGrader(
      imageTag = "2025-05-08",
      name = "Example python grader",
      source = """
def grade(sample: dict, item: dict) -> float:
    \"\"\"
    Returns 1.0 if `output_text` equals `label`, otherwise 0.0.
    \"\"\"
    output = sample.get("output_text")
    label = item.get("label")
    return 1.0 if output == label else 0.0
"""
    ),
    MultiGrader(
      name = "example multi grader",
      graders = Seq(
        TextSimilarityGrader(
          input = "The graded text",
          name = "example text similarity grader",
          reference = "The reference text",
          evaluationMetric = TextSimilarityEvaluationMetric.fuzzy_match
        ),
        StringGrader(
          input = "{{sample.output_text}}",
          name = "Example string check grader",
          operation = StringCheckOperation.eq,
          reference = "{{item.label}}"
        )
      ),
      calculateOutput = "0.5 * text_similarity_score +  0.5 * string_check_score)"
    )
  )

  def run: Future[Unit] = {
    // Iterate through all graders and validate each one
    val validationFutures = graders.zipWithIndex.map { case (grader, index) =>
      println(
        s"\n[${index + 1}/${graders.size}] Validating: ${grader.`type`} - ${grader match {
            case sg: StringGrader      => sg.name
            case smg: ScoreModelGrader => smg.name
            case lmg: LabelModelGrader => lmg.name
            case pg: PythonGrader      => pg.name
            case mg: MultiGrader       => mg.name
            case _                     => "Unknown"
          }}"
      )

      service
        .validateGrader(grader)
        .map { validatedGrader =>
          println(s"✓ Grader validated successfully:")
          println(s"  Type: ${validatedGrader.`type`}")

          validatedGrader match {
            case sg: StringGrader =>
              println(s"  Name: ${sg.name}")
              println(s"  Operation: ${sg.operation}")
              println(s"  Input: ${sg.input}")
              println(s"  Reference: ${sg.reference}")

            case smg: ScoreModelGrader =>
              println(s"  Name: ${smg.name}")
              println(s"  Model: ${smg.model}")
              println(s"  Input messages: ${smg.input.size}")
              println(s"  Range: ${smg.range}")

            case lmg: LabelModelGrader =>
              println(s"  Name: ${lmg.name}")
              println(s"  Model: ${lmg.model}")
              println(s"  Labels: ${lmg.labels.mkString(", ")}")
              println(s"  Passing labels: ${lmg.passingLabels.mkString(", ")}")

            case pg: PythonGrader =>
              println(s"  Name: ${pg.name}")
              println(s"  Image tag: ${pg.imageTag}")
              println(s"  Source code length: ${pg.source.length} chars")

            case mg: MultiGrader =>
              println(s"  Name: ${mg.name}")
              println(s"  Number of sub-graders: ${mg.graders.size}")
              println(s"  Calculate output: ${mg.calculateOutput}")

            case other =>
              println(s"  Grader: $other")
          }
        }
        .recover { case e: Exception =>
          println(s"✗ Validation failed: ${e.getMessage}")
        }
    }

    // Wait for all validations to complete
    Future.sequence(validationFutures).map { _ =>
      println("\n" + "=" * 60)
      println(s"Validation complete: ${graders.size} grader(s) processed")
      println("=" * 60)
    }
  }
}
